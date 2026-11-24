package com.example.Algo_Trading_Backend.broker;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.example.Algo_Trading_Backend.api.MarketStateStore;
import com.example.Algo_Trading_Backend.config.StrategyProperties;
import com.example.Algo_Trading_Backend.config.MarketSessionProperties;
import com.example.Algo_Trading_Backend.market.FuturesCandle;
import com.example.Algo_Trading_Backend.market.Timeframe;
import java.time.Duration;
import java.time.ZonedDateTime;
import com.example.Algo_Trading_Backend.persistence.MarketPersistenceService;

@Service
public class CandleAggregationService {
	private final MarketStateStore stateStore;
	private final MarketPersistenceService persistence;
	private final StrategyProperties properties;
	private final MarketSessionProperties marketSession;
	private final ConcurrentHashMap<String, Builder> active = new ConcurrentHashMap<>();

	public CandleAggregationService(MarketStateStore stateStore, MarketPersistenceService persistence,
			StrategyProperties properties, MarketSessionProperties marketSession) {
		this.stateStore = stateStore;
		this.persistence = persistence;
		this.properties = properties;
		this.marketSession = marketSession;
	}

	public void accept(Instrument instrument, BigDecimal price, long cumulativeVolume, Instant timestamp) {
		if (!instrument.instrumentType().equals("FUTIDX") || price.signum() <= 0 || cumulativeVolume < 0) return;
		long candleSeconds = Timeframe.parse(properties.getTimeframe()).duration().toSeconds();
		ZonedDateTime local = timestamp.atZone(marketSession.getZone());
		Instant sessionStart = local.toLocalDate().atTime(marketSession.getOpen())
				.atZone(marketSession.getZone()).toInstant();
		long elapsed = Duration.between(sessionStart, timestamp).getSeconds();
		long bucket = sessionStart.getEpochSecond() + Math.floorDiv(elapsed, candleSeconds) * candleSeconds;
		String key = instrument.token();
		Builder builder = active.computeIfAbsent(key, ignored -> new Builder(instrument));
		synchronized (builder) {
			if (builder.bucketStart == -1) {
				builder.start(bucket, price);
			} else if (bucket > builder.bucketStart) {
				FuturesCandle completed = builder.finish();
				stateStore.updateCandle(instrument.underlying(), completed);
				persistence.saveCompletedCandle(completed);
				builder.start(bucket, price);
			} else if (bucket < builder.bucketStart) {
				return;
			} else {
				builder.high = builder.high.max(price);
				builder.low = builder.low.min(price);
				builder.close = price;
			}
			if (builder.lastCumulativeVolume >= 0 && cumulativeVolume >= builder.lastCumulativeVolume) {
				builder.volume = Math.addExact(builder.volume, cumulativeVolume - builder.lastCumulativeVolume);
			}
			builder.lastCumulativeVolume = cumulativeVolume;
			stateStore.updateCandle(instrument.underlying(), builder.snapshot());
		}
	}

	private static final class Builder {
		private final Instrument instrument;
		private long bucketStart = -1;
		private BigDecimal open;
		private BigDecimal high;
		private BigDecimal low;
		private BigDecimal close;
		private long volume;
		private long lastCumulativeVolume = -1;

		private Builder(Instrument instrument) { this.instrument = instrument; }

		private void start(long bucket, BigDecimal price) {
			bucketStart = bucket;
			open = price;
			high = price;
			low = price;
			close = price;
			volume = 0;
		}

		private FuturesCandle finish() {
			return new FuturesCandle(instrument.token(), open, high, low, close, volume,
					Instant.ofEpochSecond(bucketStart));
		}

		private FuturesCandle snapshot() {
			return finish();
		}
	}
}
