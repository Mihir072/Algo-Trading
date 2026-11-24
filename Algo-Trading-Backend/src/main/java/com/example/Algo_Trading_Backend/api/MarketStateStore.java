package com.example.Algo_Trading_Backend.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.example.Algo_Trading_Backend.analytics.OiLevelService;
import com.example.Algo_Trading_Backend.analytics.PcrTrendService;
import com.example.Algo_Trading_Backend.market.AnalyticsResult;
import com.example.Algo_Trading_Backend.market.FuturesCandle;
import com.example.Algo_Trading_Backend.market.OptionChainSnapshot;
import com.example.Algo_Trading_Backend.market.OptionContract;
import com.example.Algo_Trading_Backend.market.RedisMarketCache;
import com.example.Algo_Trading_Backend.strategy.StrategySignal;

@Component
public class MarketStateStore {
	public record MarketState(String underlying, BigDecimal spotPrice, BigDecimal futuresPrice,
			Instant spotTimestamp, Instant futuresTimestamp, List<FuturesCandle> futuresCandles,
			List<OptionContract> optionContracts, OptionChainSnapshot optionChain,
			AnalyticsResult vwap, AnalyticsResult atr, OiLevelService.Levels oiLevels,
			PcrTrendService.Result pcrTrend, StrategySignal latestSignal, Instant updatedAt) {
		public MarketState {
			futuresCandles = List.copyOf(futuresCandles);
			optionContracts = List.copyOf(optionContracts);
		}
	}

	private static final int MAX_CANDLES = 1000;
	private final Map<String, MutableState> state = new ConcurrentHashMap<>();
	private final RedisMarketCache redisCache;
	private final MarketDataWebSocketPublisher webSocketPublisher;

	public MarketStateStore(RedisMarketCache redisCache, MarketDataWebSocketPublisher webSocketPublisher) {
		this.redisCache = redisCache;
		this.webSocketPublisher = webSocketPublisher;
	}

	public MarketState get(String underlying) {
		MutableState current = state.get(normalize(underlying));
		return current == null ? null : current.snapshot();
	}

	public Map<String, MarketState> all() {
		return state.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
				Map.Entry::getKey, entry -> entry.getValue().snapshot()));
	}

	public MarketState updateSpot(String underlying, BigDecimal price, Instant timestamp) {
		String key = normalize(underlying);
		redisCache.put("market:spot:" + key, price + "|" + timestamp, java.time.Duration.ofHours(24));
		return mutate(underlying, current -> {
			current.spotPrice = price;
			current.spotTimestamp = timestamp;
		});
	}

	public MarketState updateFuturesPrice(String underlying, BigDecimal price, Instant timestamp) {
		String key = normalize(underlying);
		redisCache.put("market:futures:" + key, price + "|" + timestamp, java.time.Duration.ofHours(24));
		return mutate(underlying, current -> {
			current.futuresPrice = price;
			current.futuresTimestamp = timestamp;
		});
	}

	public MarketState updateOption(OptionContract contract) {
		String value = String.join("|", contract.tradingSymbol(), String.valueOf(contract.expiry()),
				String.valueOf(contract.strike()), String.valueOf(contract.optionType()),
				String.valueOf(contract.openInterest()), String.valueOf(contract.volume()),
				String.valueOf(contract.ltp()), String.valueOf(contract.bid()), String.valueOf(contract.ask()),
				String.valueOf(contract.updatedAt()));
		redisCache.put("market:option:" + contract.token(), value, java.time.Duration.ofHours(24));
		return mutate(contract.underlying(), current -> {
			current.options.put(contract.token(), contract);
		});
	}

	public MarketState updateOptionChain(String underlying, OptionChainSnapshot snapshot) {
		String value = String.join("|", String.valueOf(snapshot.expiry()), String.valueOf(snapshot.atmStrike()),
				String.valueOf(snapshot.totalCeOi()), String.valueOf(snapshot.totalPeOi()),
				String.valueOf(snapshot.pcrOi()), String.valueOf(snapshot.pcrChangeOi()),
				snapshot.selectedStrikes().toString(), String.valueOf(snapshot.calculatedAt()));
		redisCache.put("market:option-chain:" + normalize(underlying), value, java.time.Duration.ofHours(24));
		return mutate(underlying, current -> current.optionChain = snapshot);
	}

	public MarketState updateCandle(String underlying, FuturesCandle candle) {
		String value = String.join("|", candle.open().toPlainString(), candle.high().toPlainString(),
				candle.low().toPlainString(), candle.close().toPlainString(),
				Long.toString(candle.volume()), candle.timestamp().toString());
		redisCache.put("market:candle:" + candle.token() + ":" + candle.timestamp(), value,
				java.time.Duration.ofDays(7));
		return mutate(underlying, current -> {
			current.candles.removeIf(existing -> existing.timestamp().equals(candle.timestamp())
					&& existing.token().equals(candle.token()));
			current.candles.add(candle);
			current.candles.sort(java.util.Comparator.comparing(FuturesCandle::timestamp));
			while (current.candles.size() > MAX_CANDLES) current.candles.remove(0);
		});
	}

	public MarketState updateAnalytics(String underlying, AnalyticsResult vwap, AnalyticsResult atr,
			OiLevelService.Levels levels, PcrTrendService.Result trend) {
		String key = normalize(underlying);
		if (vwap != null && vwap.value() != null) {
			redisCache.put("market:vwap:" + key, vwap.value() + "|" + vwap.calculatedAt(),
					java.time.Duration.ofHours(24));
		}
		if (atr != null && atr.value() != null) {
			redisCache.put("market:atr:" + key, atr.value() + "|" + atr.calculatedAt(),
					java.time.Duration.ofHours(24));
		}
		if (trend != null) {
			redisCache.put("market:pcr-trend:" + key, trend.trend() + "|" + trend.slope(),
					java.time.Duration.ofHours(24));
		}
		return mutate(underlying, current -> {
			if (vwap != null) current.vwap = vwap;
			if (atr != null) current.atr = atr;
			if (levels != null) current.oiLevels = levels;
			if (trend != null) current.pcrTrend = trend;
		});
	}

	public MarketState updateSignal(String underlying, StrategySignal signal) {
		redisCache.put("strategy:signal:" + normalize(underlying),
				signal.direction() + "|" + signal.generatedAt() + "|" + String.join("; ", signal.reasons()),
				java.time.Duration.ofHours(24));
		return mutate(underlying, current -> current.signal = signal);
	}

	private MarketState mutate(String underlying, java.util.function.Consumer<MutableState> update) {
		String key = normalize(underlying);
		MutableState current = state.computeIfAbsent(key, MutableState::new);
		MarketState snapshot;
		synchronized (current) {
			update.accept(current);
			current.updatedAt = Instant.now();
			snapshot = current.snapshot();
		}
		webSocketPublisher.publish(snapshot);
		return snapshot;
	}

	private String normalize(String underlying) {
		if (underlying == null || underlying.isBlank()) throw new IllegalArgumentException("Underlying is required");
		return underlying.trim().toUpperCase();
	}

	private static class MutableState {
		private final String underlying;
		private BigDecimal spotPrice;
		private BigDecimal futuresPrice;
		private Instant spotTimestamp;
		private Instant futuresTimestamp;
		private final List<FuturesCandle> candles = new ArrayList<>();
		private final Map<String, OptionContract> options = new ConcurrentHashMap<>();
		private OptionChainSnapshot optionChain;
		private AnalyticsResult vwap;
		private AnalyticsResult atr;
		private OiLevelService.Levels oiLevels;
		private PcrTrendService.Result pcrTrend;
		private StrategySignal signal;
		private Instant updatedAt = Instant.now();

		private MutableState(String underlying) { this.underlying = underlying; }

		private MarketState snapshot() {
			synchronized (this) {
				return new MarketState(underlying, spotPrice, futuresPrice, spotTimestamp, futuresTimestamp,
						candles, new ArrayList<>(options.values()), optionChain, vwap, atr, oiLevels,
						pcrTrend, signal, updatedAt);
			}
		}
	}
}
