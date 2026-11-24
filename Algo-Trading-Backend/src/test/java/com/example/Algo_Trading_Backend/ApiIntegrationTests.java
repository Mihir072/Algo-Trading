package com.example.Algo_Trading_Backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.Algo_Trading_Backend.api.MarketStateStore;
import com.example.Algo_Trading_Backend.broker.CandleAggregationService;
import com.example.Algo_Trading_Backend.broker.Instrument;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTests {
	@Autowired
	private MockMvc mvc;

	@Autowired
	private MarketStateStore marketState;

	@Autowired
	private CandleAggregationService candleAggregation;

	@Test
	void generatedOpenApiDescribesStrategyConfigurationEndpoint() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openapi").exists())
				.andExpect(jsonPath("$.paths['/api/v1/strategy/config']").exists());
	}

	@Test
	void underlyingMarketStatusUsesSourceTickTimesRatherThanRecentStateMutation() throws Exception {
		var staleTimestamp = java.time.Instant.now().minusSeconds(300);
		marketState.updateSpot("STATUS_TEST", new java.math.BigDecimal("100"), staleTimestamp);
		marketState.updateFuturesPrice("STATUS_TEST", new java.math.BigDecimal("101"), staleTimestamp);

		mvc.perform(get("/api/v1/market/underlying/STATUS_TEST"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("STALE"));
	}

	@Test
	void candleAggregatorPublishesTheCurrentlyFormingCandle() {
		var timestamp = java.time.Instant.now();
		Instrument future = new Instrument("CANDLE_TEST", "candle-test-token", "CANDLE_TEST_FUT",
				"NFO", "FUTIDX", java.time.LocalDate.now().plusDays(30), null, null, 1);
		candleAggregation.accept(future, new java.math.BigDecimal("100"), 100, timestamp);
		candleAggregation.accept(future, new java.math.BigDecimal("102"), 110, timestamp.plusSeconds(1));

		var candles = marketState.get("CANDLE_TEST").futuresCandles();
		org.junit.jupiter.api.Assertions.assertEquals(1, candles.size());
		org.junit.jupiter.api.Assertions.assertEquals(new java.math.BigDecimal("102"), candles.get(0).close());
		org.junit.jupiter.api.Assertions.assertEquals(10, candles.get(0).volume());
	}

	@Test
	void strategyRejectsStaleDataAndPersistsTheSignal() throws Exception {
		String input = """
				{
				  "underlying": "NIFTY",
				  "futuresPrice": 100,
				  "vwap": 99,
				  "atr": 2,
				  "pcrOi": 1.2,
				  "pcrChangeOi": null,
				  "pcrTrend": {"trend": "RISING", "slope": 0.1, "observations": 5},
				  "adjustedSupport": 98,
				  "adjustedResistance": 110,
				  "dataStatus": "STALE",
				  "marketDataTimestamp": "2026-11-23T04:30:00Z",
				  "oiLevelsValid": true
				}
				""";
		mvc.perform(post("/api/v1/strategy/evaluate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(input))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.direction").value("NO_TRADE"))
				.andExpect(jsonPath("$.reasons[0]").value("Market data is not valid and fresh"));

		mvc.perform(get("/api/v1/strategy/signal/NIFTY"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.signal").value("NO_TRADE"));
	}

	@Test
	void paperTradingStatusAlwaysReportsRealOrdersDisabled() throws Exception {
		mvc.perform(get("/api/v1/strategy/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.realOrdersEnabled").value(false))
				.andExpect(jsonPath("$.paperTradingRunning").value(false));
	}

	@Test
	void invalidStrategyConfigurationDoesNotPartiallyUpdateRuntimeSettings() throws Exception {
		mvc.perform(put("/api/v1/strategy/config")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "strikeRange": 9,
								  "minimumRewardRisk": 3.0,
								  "maximumRewardRisk": 2.0
								}
								"""))
				.andExpect(status().isBadRequest());

		mvc.perform(get("/api/v1/strategy/config"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pcr.strikeRange").value(8))
				.andExpect(jsonPath("$.risk.minimumRewardRisk").value(1.5));
	}

	@Test
	void validSignalOpensAndClosesOnlyASimulatedFuturesTrade() throws Exception {
		String input = """
				{
				  "underlying": "BANKNIFTY",
				  "futuresPrice": 100,
				  "vwap": 99,
				  "atr": 2,
				  "pcrOi": 1.2,
				  "pcrChangeOi": 0.9,
				  "pcrTrend": {"trend": "RISING", "slope": 0.1, "observations": 5},
				  "adjustedSupport": 98,
				  "adjustedResistance": 110,
				  "dataStatus": "VALID",
				  "marketDataTimestamp": "2026-11-23T04:30:00Z",
				  "oiLevelsValid": true
				}
				""";
		mvc.perform(post("/api/v1/strategy/evaluate")
						.contentType(MediaType.APPLICATION_JSON).content(input))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.direction").value("LONG"));
		var persisted = mvc.perform(get("/api/v1/strategy/history").param("symbol", "BANKNIFTY"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].signal").value("LONG"))
				.andReturn().getResponse().getContentAsString();
		long id = tools.jackson.databind.json.JsonMapper.builder().build()
				.readTree(persisted).get(0).path("id").asLong();

		mvc.perform(post("/api/v1/paper-trading/start"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.realOrdersEnabled").value(false));
		mvc.perform(post("/api/v1/paper-trading/signals/" + id)
						.contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.direction").value("LONG"));
		mvc.perform(post("/api/v1/paper-trading/positions/BANKNIFTY/mark")
						.param("price", "104"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("CLOSED"))
				.andExpect(jsonPath("$[0].exitReason").value("TARGET"))
				.andExpect(jsonPath("$[0].netPnl").value(4.0));
		mvc.perform(post("/api/v1/paper-trading/stop"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.running").value(false));
	}
}
