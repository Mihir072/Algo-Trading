package com.example.Algo_Trading_Backend.api;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Algo_Trading_Backend.broker.Instrument;
import com.example.Algo_Trading_Backend.broker.InstrumentMasterService;
import com.example.Algo_Trading_Backend.analytics.MarketAnalyticsService;
import com.example.Algo_Trading_Backend.analytics.OptionRecommendationService;
import com.example.Algo_Trading_Backend.broker.AngelOneMarketDataService;
import com.example.Algo_Trading_Backend.market.DataStatus;
import com.example.Algo_Trading_Backend.market.Timeframe;
import com.example.Algo_Trading_Backend.config.StrategyProperties;

@RestController
@RequestMapping("/api/v1")
public class MarketController {
	private final MarketStateStore stateStore;
	private final InstrumentMasterService instruments;
	private final MarketAnalyticsService analytics;
	private final OptionRecommendationService recommendation;
	private final AngelOneMarketDataService brokerMarketData;
	private final StrategyProperties strategyProperties;

	public MarketController(MarketStateStore stateStore, InstrumentMasterService instruments,
			MarketAnalyticsService analytics, OptionRecommendationService recommendation,
			AngelOneMarketDataService brokerMarketData, StrategyProperties strategyProperties) {
		this.stateStore = stateStore;
		this.instruments = instruments;
		this.analytics = analytics;
		this.recommendation = recommendation;
		this.brokerMarketData = brokerMarketData;
		this.strategyProperties = strategyProperties;
	}

	public record DataResponse<T>(String underlying, DataStatus status, Instant updatedAt, T data) {}
	public record PcrResponse(java.math.BigDecimal pcrOi, long ceChangeOi, long peChangeOi,
			java.math.BigDecimal pcrChangeOi, DataStatus pcrChangeStatus) {}

	@GetMapping("/market/underlying/{symbol}")
	public DataResponse<?> underlying(@PathVariable String symbol) {
		var state = stateStore.get(symbol);
		return state == null
				? new DataResponse<>(symbol, DataStatus.MISSING, null, null)
				: new DataResponse<>(symbol, underlyingStatus(state.spotTimestamp(), state.futuresTimestamp()),
						state.updatedAt(), state);
	}

	@GetMapping("/market/futures/{symbol}")
	public DataResponse<?> futures(@PathVariable String symbol) {
		var state = stateStore.get(symbol);
		var candle = state == null ? null : state.futuresCandles().stream()
				.max(java.util.Comparator.comparing(com.example.Algo_Trading_Backend.market.FuturesCandle::timestamp))
				.orElse(null);
		if (candle == null) {
			return new DataResponse<>(symbol, DataStatus.MISSING, null, null);
		}
		long candleAge = Timeframe.parse(strategyProperties.getTimeframe()).duration().toSeconds() + 60;
		return new DataResponse<>(symbol, fresh(candle.timestamp(), candleAge) ? DataStatus.VALID : DataStatus.STALE,
				candle.timestamp(), candle);
	}

	@GetMapping("/market/option-chain/{symbol}")
	public DataResponse<?> optionChain(@PathVariable String symbol) {
		var state = stateStore.get(symbol);
		if (state == null || state.optionChain() == null) {
			return new DataResponse<>(symbol, DataStatus.MISSING, null, null);
		}
		Instant updatedAt = state.optionChain().calculatedAt();
		return new DataResponse<>(symbol, optionChainStatus(state.optionChain()),
				updatedAt, state.optionChain());
	}

	@GetMapping("/market/instruments/{symbol}")
	public List<Instrument> instruments(@PathVariable String symbol) {
		return instruments.find(symbol);
	}

	@GetMapping("/analytics/pcr/{symbol}")
	public DataResponse<?> pcr(@PathVariable String symbol) {
		var state = stateStore.get(symbol);
		return state == null || state.optionChain() == null
				? new DataResponse<>(symbol, DataStatus.MISSING, null, null)
				: new DataResponse<>(symbol, optionChainStatus(state.optionChain()),
						state.optionChain().calculatedAt(), new PcrResponse(state.optionChain().pcrOi(),
								state.optionChain().ceChangeOi(), state.optionChain().peChangeOi(),
								state.optionChain().pcrChangeOi(), state.optionChain().pcrChangeStatus()));
	}

	@GetMapping("/analytics/vwap/{symbol}")
	public DataResponse<?> vwap(@PathVariable String symbol) {
		var state = stateStore.get(symbol);
		return state == null || state.vwap() == null
				? new DataResponse<>(symbol, DataStatus.MISSING, null, null)
				: new DataResponse<>(symbol, analyticsStatus(state.vwap()), state.vwap().calculatedAt(), state.vwap());
	}

	@GetMapping("/analytics/atr/{symbol}")
	public DataResponse<?> atr(@PathVariable String symbol) {
		var state = stateStore.get(symbol);
		return state == null || state.atr() == null
				? new DataResponse<>(symbol, DataStatus.MISSING, null, null)
				: new DataResponse<>(symbol, analyticsStatus(state.atr()), state.atr().calculatedAt(), state.atr());
	}

	@GetMapping("/analytics/oi-levels/{symbol}")
	public DataResponse<?> oiLevels(@PathVariable String symbol) {
		var state = stateStore.get(symbol);
		return state == null || state.oiLevels() == null || state.optionChain() == null
				? new DataResponse<>(symbol, DataStatus.MISSING, null, null)
				: new DataResponse<>(symbol, optionChainStatus(state.optionChain()),
						state.optionChain().calculatedAt(), state.oiLevels());
	}

	@GetMapping("/analytics/greeks/{symbol}")
	public DataResponse<?> greeks(@PathVariable String symbol) {
		var result = brokerMarketData.getGreeks(symbol);
		return new DataResponse<>(symbol, result.status(), result.updatedAt(), result);
	}

	@PostMapping("/analytics/recalculate/{symbol}")
	public MarketStateStore.MarketState recalculate(@PathVariable String symbol) {
		return analytics.recalculate(symbol);
	}

	@GetMapping("/strategy/recommendation/{symbol}")
	public OptionRecommendationService.Recommendation recommendation(@PathVariable String symbol) {
		var state = stateStore.get(symbol);
		if (state == null || state.latestSignal() == null) {
			return recommendation.recommend(null, null, List.of());
		}
		return recommendation.recommend(state.latestSignal().direction(), state.spotPrice(), state.optionContracts());
	}

	private DataStatus analyticsStatus(com.example.Algo_Trading_Backend.market.AnalyticsResult result) {
		if (result.status() != DataStatus.VALID) return result.status();
		Instant latestSource = result.sourceTimestamps().stream().max(Instant::compareTo).orElse(null);
		long candleAge = Timeframe.parse(strategyProperties.getTimeframe()).duration().toSeconds() + 60;
		return fresh(latestSource, candleAge) ? DataStatus.VALID : DataStatus.STALE;
	}

	private boolean fresh(Instant timestamp, long seconds) {
		return timestamp != null && !timestamp.isAfter(Instant.now())
				&& timestamp.isAfter(Instant.now().minusSeconds(seconds));
	}

	private DataStatus underlyingStatus(Instant spotTimestamp, Instant futuresTimestamp) {
		if (spotTimestamp == null || futuresTimestamp == null) return DataStatus.MISSING;
		return fresh(spotTimestamp, 60) && fresh(futuresTimestamp, 60)
				? DataStatus.VALID : DataStatus.STALE;
	}

	private DataStatus optionChainStatus(com.example.Algo_Trading_Backend.market.OptionChainSnapshot snapshot) {
		if (snapshot.contracts().isEmpty()) return DataStatus.MISSING;
		return snapshot.contracts().stream().allMatch(contract -> fresh(contract.updatedAt(), 60))
				? DataStatus.VALID : DataStatus.STALE;
	}

}
