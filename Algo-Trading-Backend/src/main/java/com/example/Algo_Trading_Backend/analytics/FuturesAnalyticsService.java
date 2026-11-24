package com.example.Algo_Trading_Backend.analytics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.Algo_Trading_Backend.market.AnalyticsResult;
import com.example.Algo_Trading_Backend.market.DataStatus;
import com.example.Algo_Trading_Backend.market.FuturesCandle;

@Service
public class FuturesAnalyticsService {
	private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");

	public AnalyticsResult calculateSessionVwap(List<FuturesCandle> candles) {
		if (candles.isEmpty()) {
			return new AnalyticsResult(null, DataStatus.MISSING, Instant.now(), List.of());
		}
		List<FuturesCandle> ordered = candles.stream().sorted(Comparator.comparing(FuturesCandle::timestamp)).toList();
		LocalDate sessionDate = ordered.get(ordered.size() - 1).timestamp().atZone(MARKET_ZONE).toLocalDate();
		BigDecimal priceVolume = BigDecimal.ZERO;
		long totalVolume = 0;
		List<Instant> sources = new ArrayList<>();
		for (FuturesCandle candle : ordered) {
			if (!candle.timestamp().atZone(MARKET_ZONE).toLocalDate().equals(sessionDate)) {
				continue;
			}
			validateCandle(candle);
			if (candle.volume() > 0) {
				BigDecimal typicalPrice = candle.high().add(candle.low()).add(candle.close())
						.divide(BigDecimal.valueOf(3), MathContext.DECIMAL128);
				priceVolume = priceVolume.add(typicalPrice.multiply(BigDecimal.valueOf(candle.volume())));
				totalVolume = Math.addExact(totalVolume, candle.volume());
			}
			sources.add(candle.timestamp());
		}
		if (totalVolume == 0) {
			return new AnalyticsResult(null, DataStatus.MISSING, Instant.now(), sources);
		}
		BigDecimal vwap = priceVolume.divide(BigDecimal.valueOf(totalVolume), MathContext.DECIMAL128);
		return new AnalyticsResult(vwap, DataStatus.VALID, Instant.now(), sources);
	}

	public AnalyticsResult calculateWilderAtr(List<FuturesCandle> candles, int period) {
		if (period < 1) {
			throw new IllegalArgumentException("ATR period must be positive");
		}
		List<FuturesCandle> ordered = candles.stream().sorted(Comparator.comparing(FuturesCandle::timestamp)).toList();
		if (ordered.size() < period + 1) {
			return new AnalyticsResult(null, DataStatus.MISSING, Instant.now(),
					ordered.stream().map(FuturesCandle::timestamp).toList());
		}
		List<BigDecimal> ranges = new ArrayList<>();
		List<Instant> sources = new ArrayList<>();
		FuturesCandle previous = null;
		for (FuturesCandle candle : ordered) {
			validateCandle(candle);
			if (previous != null) {
				BigDecimal highLow = candle.high().subtract(candle.low());
				BigDecimal highClose = candle.high().subtract(previous.close()).abs();
				BigDecimal lowClose = candle.low().subtract(previous.close()).abs();
				ranges.add(highLow.max(highClose).max(lowClose));
				sources.add(candle.timestamp());
			}
			previous = candle;
		}
		if (ranges.size() < period) {
			return new AnalyticsResult(null, DataStatus.MISSING, Instant.now(), sources);
		}
		BigDecimal atr = ranges.subList(0, period).stream().reduce(BigDecimal.ZERO, BigDecimal::add)
				.divide(BigDecimal.valueOf(period), MathContext.DECIMAL128);
		for (int index = period; index < ranges.size(); index++) {
			atr = atr.multiply(BigDecimal.valueOf(period - 1)).add(ranges.get(index))
					.divide(BigDecimal.valueOf(period), MathContext.DECIMAL128);
		}
		return new AnalyticsResult(atr, DataStatus.VALID, Instant.now(), sources);
	}

	private void validateCandle(FuturesCandle candle) {
		if (candle.open() == null || candle.high() == null || candle.low() == null || candle.close() == null
				|| candle.high().compareTo(candle.low()) < 0 || candle.volume() < 0) {
			throw new IllegalArgumentException("Candle has invalid OHLCV data at " + candle.timestamp());
		}
	}
}
