package com.example.Algo_Trading_Backend.api;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MarketDataWebSocketPublisher {
	private final MarketDataWebSocketHandler handler;
	private final Map<String, Instant> lastPublished = new ConcurrentHashMap<>();

	public MarketDataWebSocketPublisher(MarketDataWebSocketHandler handler) {
		this.handler = handler;
	}

	@Async("marketCacheExecutor")
	public void publish(MarketStateStore.MarketState state) {
		Instant now = Instant.now();
		Instant previous = lastPublished.put(state.underlying(), now);
		if (previous != null && previous.isAfter(now.minusSeconds(1))) return;
		handler.publish(state);
	}
}
