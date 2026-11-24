package com.example.Algo_Trading_Backend.persistence;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Algo_Trading_Backend.market.FuturesCandle;

@Service
public class MarketPersistenceService {
	private final FuturesCandleRepository candles;

	public MarketPersistenceService(FuturesCandleRepository candles) {
		this.candles = candles;
	}

	@Async("marketCacheExecutor")
	@Transactional
	public void saveCompletedCandle(FuturesCandle candle) {
		candles.save(new FuturesCandleEntity(candle));
	}
}
