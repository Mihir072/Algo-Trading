package com.example.Algo_Trading_Backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.Algo_Trading_Backend.analytics.FuturesAnalyticsService;
import com.example.Algo_Trading_Backend.analytics.OptionChainAnalyticsService;
import com.example.Algo_Trading_Backend.analytics.OiLevelService;
import com.example.Algo_Trading_Backend.analytics.PcrTrendService;
import com.example.Algo_Trading_Backend.config.StrategyProperties;
import com.example.Algo_Trading_Backend.market.DataStatus;
import com.example.Algo_Trading_Backend.market.FuturesCandle;
import com.example.Algo_Trading_Backend.market.OptionContract;
import com.example.Algo_Trading_Backend.market.OptionType;
import com.example.Algo_Trading_Backend.backtest.BacktestObservation;
import com.example.Algo_Trading_Backend.backtest.BacktestService;
import com.example.Algo_Trading_Backend.market.MarketSessionService;
import com.example.Algo_Trading_Backend.strategy.RiskManagementService;
import com.example.Algo_Trading_Backend.strategy.SignalDirection;
import com.example.Algo_Trading_Backend.strategy.SignalGenerator;
import com.example.Algo_Trading_Backend.strategy.StrategyInput;

class AnalyticsServicesTests {
	private static final Instant NOW = Instant.parse("2026-11-23T04:00:00Z");

	@Test
	void pcrUsesExactlySeventeenPairedStrikesAndTracksChanges() {
		StrategyProperties properties = new StrategyProperties();
		OptionChainAnalyticsService service = new OptionChainAnalyticsService(properties);
		List<OptionContract> contracts = new ArrayList<>();
		Map<String, Long> previous = new java.util.HashMap<>();
		for (int index = 0; index < 17; index++) {
			BigDecimal strike = BigDecimal.valueOf(25000 + index * 50L);
			contracts.add(contract("CE" + index, strike, OptionType.CE, 100, "NIFTY"));
			contracts.add(contract("PE" + index, strike, OptionType.PE, 200, "NIFTY"));
			previous.put("CE" + index, 90L);
			previous.put("PE" + index, 180L);
		}
		var result = service.calculate("NIFTY", LocalDate.of(2026, 11, 26),
				new BigDecimal("25400"), contracts, previous);
		assertEquals(17, result.selectedStrikes().size());
		assertEquals(34, result.contracts().size());
		assertEquals(new BigDecimal("2.00000000"), result.pcrOi());
		assertEquals(new BigDecimal("2.00000000"), result.pcrChangeOi());
		assertEquals(DataStatus.VALID, result.pcrChangeStatus());
	}

	@Test
	void pcrChangeRatioIsUnavailableWhenCallChangeIsNotPositive() {
		StrategyProperties properties = new StrategyProperties();
		OptionChainAnalyticsService service = new OptionChainAnalyticsService(properties);
		List<OptionContract> contracts = new ArrayList<>();
		Map<String, Long> previous = new java.util.HashMap<>();
		for (int index = 0; index < 17; index++) {
			BigDecimal strike = BigDecimal.valueOf(25000 + index * 50L);
			contracts.add(contract("CE" + index, strike, OptionType.CE, 100, "NIFTY"));
			contracts.add(contract("PE" + index, strike, OptionType.PE, 200, "NIFTY"));
			previous.put("CE" + index, 110L);
			previous.put("PE" + index, 190L);
		}
		var result = service.calculate("NIFTY", LocalDate.of(2026, 11, 26),
				new BigDecimal("25400"), contracts, previous);
		assertEquals(null, result.pcrChangeOi());
		assertEquals(DataStatus.UNKNOWN, result.pcrChangeStatus());
		assertEquals(-170, result.ceChangeOi());
		assertEquals(170, result.peChangeOi());
	}

	@Test
	void pcrRejectsAnIncompleteWindow() {
		assertThrows(IllegalArgumentException.class, () -> new OptionChainAnalyticsService(new StrategyProperties())
				.calculate("NIFTY", LocalDate.of(2026, 11, 26), new BigDecimal("25000"),
						List.of(), Map.of()));
	}

	@Test
	void vwapUsesTypicalPriceWeightedByVolume() {
		FuturesAnalyticsService service = new FuturesAnalyticsService();
		var result = service.calculateSessionVwap(List.of(
				candle("10", "12", "8", "10", 2, NOW),
				candle("12", "15", "9", "12", 1, NOW.plusSeconds(60))));
		assertEquals(new BigDecimal("10.66666666666666666666666666666667"), result.value());
		assertEquals(DataStatus.VALID, result.status());
	}

	@Test
	void atrUsesWilderSmoothingAndRequiresEnoughCandles() {
		FuturesAnalyticsService service = new FuturesAnalyticsService();
		List<FuturesCandle> candles = List.of(
				candle("9", "10", "8", "9", 1, NOW),
				candle("11", "12", "10", "11", 1, NOW.plusSeconds(60)),
				candle("14", "15", "11", "14", 1, NOW.plusSeconds(120)));
		assertEquals(new BigDecimal("3.5"), service.calculateWilderAtr(candles, 2).value());
		assertEquals(DataStatus.MISSING,
				service.calculateWilderAtr(candles.subList(0, 2), 2).status());
	}

	@Test
	void riskUsesTighterStopAndCapsTargetAtTwoR() {
		StrategyProperties properties = new StrategyProperties();
		var plan = new RiskManagementService(properties).calculate(SignalDirection.LONG,
				new BigDecimal("100"), new BigDecimal("2"),
				new BigDecimal("98"), new BigDecimal("110"));
		assertEquals(new BigDecimal("98"), plan.stopLoss());
		assertEquals(new BigDecimal("104.0"), plan.target());
		assertEquals(new BigDecimal("2.00000000"), plan.rewardRisk());
	}

	@Test
	void pcrTrendUsesLinearSlopeAndRequiresTheConfiguredWindow() {
		StrategyProperties properties = new StrategyProperties();
		PcrTrendService service = new PcrTrendService(properties);
		assertEquals(PcrTrendService.Trend.RISING,
				service.calculate(List.of(BigDecimal.ONE, new BigDecimal("2"), new BigDecimal("3"),
						new BigDecimal("4"), new BigDecimal("5"))).trend());
		assertEquals(PcrTrendService.Trend.FLAT,
				service.calculate(List.of(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
						BigDecimal.ONE, BigDecimal.ONE)).trend());
		assertEquals(PcrTrendService.Trend.UNKNOWN,
				service.calculate(List.of(BigDecimal.ONE, new BigDecimal("2"))).trend());
	}

	@Test
	void signalRequiresDirectionTrendAndRiskConditions() {
		StrategyProperties properties = new StrategyProperties();
		var service = new SignalGenerator(properties, new RiskManagementService(properties));
		var rising = new PcrTrendService.Result(PcrTrendService.Trend.RISING, BigDecimal.ONE, 5);
		var falling = new PcrTrendService.Result(PcrTrendService.Trend.FALLING, BigDecimal.ONE.negate(), 5);
		assertEquals(SignalDirection.LONG, service.generate(input("101", "100", "1.2", rising)).direction());
		StrategyInput shortInput = new StrategyInput("NIFTY", new BigDecimal("99"), new BigDecimal("100"),
				new BigDecimal("2"), new BigDecimal("0.8"), null, falling,
				new BigDecimal("90"), new BigDecimal("102"), DataStatus.VALID, NOW, true);
		assertEquals(SignalDirection.SHORT, service.generate(shortInput).direction());
		assertEquals(SignalDirection.NO_TRADE,
				service.generate(input("101", "100", "1.2",
						new PcrTrendService.Result(PcrTrendService.Trend.UNKNOWN, null, 2))).direction());
		assertEquals(SignalDirection.NO_TRADE, service.generate(new StrategyInput("NIFTY",
				new BigDecimal("101"), new BigDecimal("100"), new BigDecimal("2"),
				new BigDecimal("1.2"), null, rising, new BigDecimal("98"), new BigDecimal("110"),
				DataStatus.STALE, NOW, true)).direction());
	}

	@Test
	void backtestRejectsOptionDataFromTheFuture() {
		StrategyProperties properties = new StrategyProperties();
		var trend = new PcrTrendService(properties);
		var service = new BacktestService(new FuturesAnalyticsService(), new OiLevelService(), trend,
				new SignalGenerator(properties, new RiskManagementService(properties)),
				new RiskManagementService(properties), properties,
				new MarketSessionService(new com.example.Algo_Trading_Backend.config.MarketSessionProperties()));
		var chain = new com.example.Algo_Trading_Backend.market.OptionChainSnapshot("NIFTY",
				LocalDate.of(2026, 11, 26), new BigDecimal("25000"), List.of(new BigDecimal("25000")),
				1, 1, 0, 0, BigDecimal.ONE, null, DataStatus.UNKNOWN,
				new BigDecimal("24950"), 1, new BigDecimal("25050"), 1, List.of(), NOW.plusSeconds(60));
		assertThrows(IllegalArgumentException.class, () -> service.run(List.of(
				new BacktestObservation(NOW, BigDecimal.valueOf(25000),
						candle("25000", "25010", "24990", "25000", 100, NOW), chain),
				new BacktestObservation(NOW.plusSeconds(300), BigDecimal.valueOf(25000),
						candle("25000", "25010", "24990", "25000", 100, NOW.plusSeconds(300)), chain))));
	}

	private OptionContract contract(String token, BigDecimal strike, OptionType type, long oi, String underlying) {
		return new OptionContract(underlying, token, token, LocalDate.of(2026, 11, 26), strike,
				type, oi, 10L, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, NOW);
	}

	private FuturesCandle candle(String open, String high, String low, String close, long volume, Instant timestamp) {
		return new FuturesCandle("token", new BigDecimal(open), new BigDecimal(high),
				new BigDecimal(low), new BigDecimal(close), volume, timestamp);
	}

	private StrategyInput input(String price, String vwap, String pcr, PcrTrendService.Result trend) {
		return new StrategyInput("NIFTY", new BigDecimal(price), new BigDecimal(vwap), new BigDecimal("2"),
				new BigDecimal(pcr), null, trend, new BigDecimal("98"), new BigDecimal("110"),
				DataStatus.VALID, NOW, true);
	}
}
