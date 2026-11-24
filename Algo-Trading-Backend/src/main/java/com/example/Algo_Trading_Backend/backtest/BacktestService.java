package com.example.Algo_Trading_Backend.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.example.Algo_Trading_Backend.analytics.FuturesAnalyticsService;
import com.example.Algo_Trading_Backend.analytics.OiLevelService;
import com.example.Algo_Trading_Backend.analytics.PcrTrendService;
import com.example.Algo_Trading_Backend.config.StrategyProperties;
import com.example.Algo_Trading_Backend.market.DataStatus;
import com.example.Algo_Trading_Backend.market.FuturesCandle;
import com.example.Algo_Trading_Backend.market.OptionChainSnapshot;
import com.example.Algo_Trading_Backend.market.MarketSessionService;
import com.example.Algo_Trading_Backend.strategy.RiskManagementService;
import com.example.Algo_Trading_Backend.strategy.RiskPlan;
import com.example.Algo_Trading_Backend.strategy.SignalDirection;
import com.example.Algo_Trading_Backend.strategy.SignalGenerator;
import com.example.Algo_Trading_Backend.strategy.StrategyInput;
import com.example.Algo_Trading_Backend.strategy.StrategySignal;

@Service
public class BacktestService {
	private final FuturesAnalyticsService futuresAnalytics;
	private final OiLevelService oiLevels;
	private final PcrTrendService pcrTrend;
	private final SignalGenerator signalGenerator;
	private final RiskManagementService riskManagement;
	private final StrategyProperties properties;
	private final MarketSessionService marketSession;

	public record SimulatedTrade(Instant entryTime, Instant exitTime, SignalDirection direction,
			BigDecimal entryPrice, BigDecimal exitPrice, BigDecimal stopLoss, BigDecimal target,
			BigDecimal grossPnl, BigDecimal charges, BigDecimal netPnl, String exitReason,
			BigDecimal rewardRisk) {}

	public record Result(String underlying, int candles, int signals, int totalTrades, int winningTrades,
			int losingTrades, BigDecimal winRatePercent, BigDecimal grossPnl, BigDecimal netPnl,
			BigDecimal profitFactor, BigDecimal averageRewardRisk, BigDecimal maximumDrawdown,
			int maximumConsecutiveLosses, List<SimulatedTrade> trades, List<String> rejectedReasons) {
		public Result {
			trades = List.copyOf(trades);
			rejectedReasons = List.copyOf(rejectedReasons);
		}
	}

	public BacktestService(FuturesAnalyticsService futuresAnalytics, OiLevelService oiLevels,
			PcrTrendService pcrTrend, SignalGenerator signalGenerator,
			RiskManagementService riskManagement, StrategyProperties properties,
			MarketSessionService marketSession) {
		this.futuresAnalytics = futuresAnalytics;
		this.oiLevels = oiLevels;
		this.pcrTrend = pcrTrend;
		this.signalGenerator = signalGenerator;
		this.riskManagement = riskManagement;
		this.properties = properties;
		this.marketSession = marketSession;
	}

	public Result run(List<BacktestObservation> observations) {
		if (observations == null || observations.size() < 2 || observations.size() > 10000) {
			throw new IllegalArgumentException("Backtest requires between 2 and 10000 observations");
		}
		List<BacktestObservation> data = List.copyOf(observations);
		String underlying = data.get(0).optionSnapshot().underlying();
		Instant previousTimestamp = null;
		List<FuturesCandle> candles = new ArrayList<>();
		List<BigDecimal> pcrValues = new ArrayList<>();
		List<SimulatedTrade> trades = new ArrayList<>();
		List<String> rejected = new ArrayList<>();
		int signalCount = 0;
		int nextSignalIndex = 0;
		for (int index = 0; index < data.size(); index++) {
			BacktestObservation observation = data.get(index);
			validateObservation(observation, underlying, previousTimestamp);
			previousTimestamp = observation.timestamp();
			candles.add(observation.futuresCandle());
			pcrValues.add(observation.optionSnapshot().pcrOi());
			if (index < nextSignalIndex || index == data.size() - 1 || !marketSession.isOpen(observation.timestamp())) continue;

			var vwap = futuresAnalytics.calculateSessionVwap(candles);
			var atr = futuresAnalytics.calculateWilderAtr(candles, properties.getAtr().getPeriod());
			OptionChainSnapshot snapshot = observation.optionSnapshot();
			var levels = oiLevels.adjustToFutures(snapshot, observation.futuresCandle().close(), observation.spotPrice());
			PcrTrendService.Result trend = pcrTrend.calculate(pcrValues);
			StrategyInput input = new StrategyInput(underlying, observation.futuresCandle().close(),
					vwap.value(), atr.value(), snapshot.pcrOi(), snapshot.pcrChangeOi(), trend, levels.adjustedSupport(),
					levels.adjustedResistance(), DataStatus.VALID, observation.timestamp(), true);
			StrategySignal signal = signalGenerator.generate(input);
			if (signal.direction() == SignalDirection.NO_TRADE) {
				if (!signal.reasons().isEmpty()) rejected.add(signal.reasons().get(0));
				continue;
			}
			signalCount++;
			BacktestObservation entryBar = data.get(index + 1);
			RiskPlan plan;
			try {
				plan = riskManagement.calculate(signal.direction(), entryBar.futuresCandle().open(),
						atr.value(), levels.adjustedSupport(), levels.adjustedResistance());
			} catch (IllegalArgumentException exception) {
				rejected.add(exception.getMessage());
				continue;
			}
			SimulatedTrade trade = simulate(data, index + 1, signal.direction(), plan);
			trades.add(trade);
			nextSignalIndex = indexBeforeNextSignal(data, trade.exitTime(), index + 1) + 1;
			while (pcrValues.size() > properties.getPcr().getTrendWindow()) pcrValues.remove(0);
		}
		return summarize(underlying, data.size(), signalCount, trades, rejected);
	}

	private void validateObservation(BacktestObservation observation, String underlying, Instant previous) {
		if (observation == null || observation.timestamp() == null || observation.spotPrice() == null
				|| observation.futuresCandle() == null || observation.optionSnapshot() == null) {
			throw new IllegalArgumentException("Backtest observation has missing mandatory data");
		}
		if (previous != null && !observation.timestamp().isAfter(previous)) {
			throw new IllegalArgumentException("Backtest observations must be strictly ordered by timestamp");
		}
		if (!observation.optionSnapshot().underlying().equalsIgnoreCase(underlying)
				|| observation.optionSnapshot().calculatedAt() == null
				|| observation.optionSnapshot().calculatedAt().isAfter(observation.timestamp())
				|| observation.optionSnapshot().pcrOi() == null
				|| observation.optionSnapshot().selectedStrikes().size() != properties.getPcr().getStrikeRange() * 2 + 1
				|| observation.futuresCandle().timestamp().isAfter(observation.timestamp())
				|| observation.spotPrice().signum() <= 0) {
			throw new IllegalArgumentException("Backtest data contains a future or invalid source timestamp");
		}
		if (observation.optionSnapshot().contracts().stream()
				.anyMatch(contract -> contract.updatedAt() == null || contract.updatedAt().isAfter(observation.timestamp()))) {
			throw new IllegalArgumentException("Option data contains information from after the simulated timestamp");
		}
	}

	private SimulatedTrade simulate(List<BacktestObservation> data, int entryIndex,
			SignalDirection direction, RiskPlan plan) {
		BigDecimal slip = properties.getPaperTrading().getSlippagePerUnit();
		BigDecimal entryFill = direction == SignalDirection.LONG ? plan.entry().add(slip) : plan.entry().subtract(slip);
		BigDecimal exitFill = null;
		String exitReason = "END_OF_DATA";
		Instant exitTime = data.get(data.size() - 1).timestamp();
		int exitIndex = data.size() - 1;
		for (int index = entryIndex; index < data.size(); index++) {
			FuturesCandle candle = data.get(index).futuresCandle();
			boolean stopHit = direction == SignalDirection.LONG
					? candle.low().compareTo(plan.stopLoss()) <= 0 : candle.high().compareTo(plan.stopLoss()) >= 0;
			boolean targetHit = direction == SignalDirection.LONG
					? candle.high().compareTo(plan.target()) >= 0 : candle.low().compareTo(plan.target()) <= 0;
			if (stopHit || targetHit) {
				boolean chooseStop = stopHit;
				exitReason = chooseStop ? "STOP_LOSS" : "TARGET";
				BigDecimal level = chooseStop ? plan.stopLoss() : plan.target();
				boolean gapBeyondStop = chooseStop && (direction == SignalDirection.LONG
						? candle.open().compareTo(level) < 0 : candle.open().compareTo(level) > 0);
				exitFill = gapBeyondStop ? candle.open() : level;
				exitIndex = index;
				exitTime = data.get(index).timestamp();
				break;
			}
		}
		if (exitFill == null) exitFill = data.get(exitIndex).futuresCandle().close();
		exitFill = direction == SignalDirection.LONG ? exitFill.subtract(slip) : exitFill.add(slip);
		BigDecimal points = direction == SignalDirection.LONG
				? exitFill.subtract(entryFill) : entryFill.subtract(exitFill);
		BigDecimal gross = points;
		BigDecimal charges = properties.getPaperTrading().getBrokeragePerOrder().multiply(BigDecimal.valueOf(2));
		return new SimulatedTrade(data.get(entryIndex).timestamp(), exitTime, direction, entryFill, exitFill,
				plan.stopLoss(), plan.target(), gross, charges, gross.subtract(charges), exitReason, plan.rewardRisk());
	}

	private int indexBeforeNextSignal(List<BacktestObservation> data, Instant exitTime, int start) {
		for (int index = start; index < data.size(); index++) {
			if (!data.get(index).timestamp().isBefore(exitTime)) return index;
		}
		return data.size() - 1;
	}

	private Result summarize(String underlying, int candles, int signals,
			List<SimulatedTrade> trades, List<String> rejected) {
		int wins = (int) trades.stream().filter(trade -> trade.netPnl().signum() > 0).count();
		int losses = (int) trades.stream().filter(trade -> trade.netPnl().signum() < 0).count();
		BigDecimal gross = trades.stream().map(SimulatedTrade::grossPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal net = trades.stream().map(SimulatedTrade::netPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal profits = trades.stream().map(SimulatedTrade::netPnl).filter(value -> value.signum() > 0)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal lossesValue = trades.stream().map(SimulatedTrade::netPnl).filter(value -> value.signum() < 0)
				.map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal factor = lossesValue.signum() == 0 ? null
				: profits.divide(lossesValue, 4, RoundingMode.HALF_UP);
		BigDecimal winRate = trades.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(wins * 100L)
				.divide(BigDecimal.valueOf(trades.size()), 2, RoundingMode.HALF_UP);
		BigDecimal avgRisk = trades.stream().map(SimulatedTrade::rewardRisk).reduce(BigDecimal.ZERO, BigDecimal::add);
		if (!trades.isEmpty()) avgRisk = avgRisk.divide(BigDecimal.valueOf(trades.size()), 4, RoundingMode.HALF_UP);
		BigDecimal equity = BigDecimal.ZERO;
		BigDecimal peak = BigDecimal.ZERO;
		BigDecimal drawdown = BigDecimal.ZERO;
		int streak = 0;
		int maxStreak = 0;
		for (SimulatedTrade trade : trades.stream().sorted(Comparator.comparing(SimulatedTrade::exitTime)).toList()) {
			equity = equity.add(trade.netPnl());
			peak = peak.max(equity);
			drawdown = drawdown.max(peak.subtract(equity));
			streak = trade.netPnl().signum() < 0 ? streak + 1 : 0;
			maxStreak = Math.max(maxStreak, streak);
		}
		return new Result(underlying, candles, signals, trades.size(), wins, losses, winRate,
				gross, net, factor, avgRisk, drawdown, maxStreak, trades,
				rejected.stream().distinct().limit(50).toList());
	}
}
