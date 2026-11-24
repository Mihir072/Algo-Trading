package com.example.Algo_Trading_Backend.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.Algo_Trading_Backend.api.MarketStateStore;
import com.example.Algo_Trading_Backend.config.MarketAnalyticsProperties;
import com.example.Algo_Trading_Backend.config.StrategyProperties;
import com.example.Algo_Trading_Backend.market.DataStatus;
import com.example.Algo_Trading_Backend.market.MarketSessionService;
import com.example.Algo_Trading_Backend.market.OptionChainSnapshot;
import com.example.Algo_Trading_Backend.market.OptionContract;
import com.example.Algo_Trading_Backend.market.Timeframe;
import com.example.Algo_Trading_Backend.persistence.OptionSnapshotEntity;
import com.example.Algo_Trading_Backend.persistence.OptionSnapshotRepository;
import com.example.Algo_Trading_Backend.strategy.StrategyInput;

@Service
public class MarketAnalyticsService {
	private static final Logger logger = LoggerFactory.getLogger(MarketAnalyticsService.class);
	private static final long MAX_TICK_AGE_SECONDS = 60;
	private final MarketStateStore stateStore;
	private final OptionChainAnalyticsService optionChainAnalytics;
	private final FuturesAnalyticsService futuresAnalytics;
	private final OiLevelService oiLevelService;
	private final PcrTrendService pcrTrendService;
	private final StrategyProperties properties;
	private final OptionSnapshotRepository snapshots;
	private final MarketSessionService marketSession;
	private final MarketAnalyticsProperties analyticsProperties;
	private final Map<String, Map<String, Long>> previousOi = new ConcurrentHashMap<>();
	private final Map<String, ArrayDeque<BigDecimal>> pcrHistory = new ConcurrentHashMap<>();
	private final Map<String, Instant> lastSampledCandle = new ConcurrentHashMap<>();

	public MarketAnalyticsService(MarketStateStore stateStore, OptionChainAnalyticsService optionChainAnalytics,
			FuturesAnalyticsService futuresAnalytics, OiLevelService oiLevelService,
			PcrTrendService pcrTrendService, StrategyProperties properties,
			OptionSnapshotRepository snapshots, MarketSessionService marketSession,
			MarketAnalyticsProperties analyticsProperties) {
		this.stateStore = stateStore;
		this.optionChainAnalytics = optionChainAnalytics;
		this.futuresAnalytics = futuresAnalytics;
		this.oiLevelService = oiLevelService;
		this.pcrTrendService = pcrTrendService;
		this.properties = properties;
		this.snapshots = snapshots;
		this.marketSession = marketSession;
		this.analyticsProperties = analyticsProperties;
	}

	@Scheduled(fixedDelayString = "${market-analytics.poll-interval-millis:30000}",
			initialDelayString = "${market-analytics.poll-interval-millis:30000}")
	public void recalculateAvailableMarkets() {
		if (!analyticsProperties.isEnabled()) return;
		for (String underlying : properties.getMarkets()) {
			try {
				recalculate(underlying);
			} catch (IllegalArgumentException | IllegalStateException | DataAccessException exception) {
				logger.warn("Market analytics are not ready for {}: {}", underlying, exception.getMessage());
			}
		}
	}

	public MarketStateStore.MarketState recalculate(String underlying) {
		var current = stateStore.get(underlying);
		if (current == null || current.spotPrice() == null || current.futuresPrice() == null) {
			throw new IllegalStateException("Spot and futures ticks are required before analytics can be calculated");
		}
		LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
		LocalDate expiry = current.optionContracts().stream().map(OptionContract::expiry)
				.filter(date -> date != null && !date.isBefore(today)).min(LocalDate::compareTo)
				.orElseThrow(() -> new IllegalStateException("No active option expiry is available"));
		OptionChainSnapshot chain = optionChainAnalytics.calculate(underlying, expiry,
				current.spotPrice(), current.optionContracts(), previousOi.getOrDefault(underlying.toUpperCase(), Map.of()));
		snapshots.save(new OptionSnapshotEntity(chain));
		previousOi.put(underlying.toUpperCase(), chain.contracts().stream()
				.collect(java.util.stream.Collectors.toUnmodifiableMap(OptionContract::token,
						OptionContract::openInterest)));
		stateStore.updateOptionChain(underlying, chain);

		var vwap = futuresAnalytics.calculateSessionVwap(current.futuresCandles());
		var atr = futuresAnalytics.calculateWilderAtr(current.futuresCandles(), properties.getAtr().getPeriod());
		var levels = oiLevelService.adjustToFutures(chain, current.futuresPrice(), current.spotPrice());
		ArrayDeque<BigDecimal> history = pcrHistory.computeIfAbsent(underlying.toUpperCase(), ignored -> new ArrayDeque<>());
		Instant latestCandleTime = current.futuresCandles().isEmpty() ? null
				: current.futuresCandles().get(current.futuresCandles().size() - 1).timestamp();
		synchronized (history) {
			if (latestCandleTime != null && !latestCandleTime.equals(lastSampledCandle.get(underlying.toUpperCase()))) {
				history.addLast(chain.pcrOi());
				while (history.size() > properties.getPcr().getTrendWindow()) history.removeFirst();
				lastSampledCandle.put(underlying.toUpperCase(), latestCandleTime);
			}
		}
		PcrTrendService.Result trend;
		synchronized (history) {
			trend = pcrTrendService.calculate(new ArrayList<>(history));
		}
		stateStore.updateAnalytics(underlying, vwap, atr, levels, trend);
		return stateStore.get(underlying);
	}

	public StrategyInput strategyInput(String underlying) {
		var current = stateStore.get(underlying);
		if (current == null || current.optionChain() == null) {
			throw new IllegalStateException("Market analytics are not available; recalculate after loading live data");
		}
		Instant latestCandleTime = current.futuresCandles().isEmpty() ? null
				: current.futuresCandles().get(current.futuresCandles().size() - 1).timestamp();
		long candleFreshnessSeconds = Timeframe.parse(properties.getTimeframe()).duration().toSeconds() + 60;
		boolean candleFresh = latestCandleTime != null
				&& latestCandleTime.isAfter(Instant.now().minusSeconds(candleFreshnessSeconds));
		boolean fresh = isFresh(current.spotTimestamp()) && isFresh(current.futuresTimestamp()) && candleFresh
				&& current.optionChain().contracts().stream().allMatch(contract -> isFresh(contract.updatedAt()));
		boolean validOi = current.optionChain().supportStrike() != null
				&& current.optionChain().resistanceStrike() != null
				&& current.optionChain().supportOi() > 0 && current.optionChain().resistanceOi() > 0;
		DataStatus dataStatus = !marketSession.isOpen(Instant.now()) ? DataStatus.INVALID
				: fresh ? DataStatus.VALID : DataStatus.STALE;
		return new StrategyInput(underlying.toUpperCase(), current.futuresPrice(),
				current.vwap() == null ? null : current.vwap().value(),
				current.atr() == null ? null : current.atr().value(),
				current.optionChain().pcrOi(), current.optionChain().pcrChangeOi(), current.pcrTrend(),
				current.oiLevels() == null ? null : current.oiLevels().adjustedSupport(),
				current.oiLevels() == null ? null : current.oiLevels().adjustedResistance(),
				dataStatus,
				current.futuresTimestamp(), validOi);
	}

	private boolean isFresh(Instant timestamp) {
		return timestamp != null && !timestamp.isAfter(Instant.now())
				&& timestamp.isAfter(Instant.now().minusSeconds(MAX_TICK_AGE_SECONDS));
	}
}
