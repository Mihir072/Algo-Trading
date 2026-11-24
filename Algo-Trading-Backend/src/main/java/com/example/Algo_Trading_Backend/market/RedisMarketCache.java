package com.example.Algo_Trading_Backend.market;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class RedisMarketCache {
	private static final Logger logger = LoggerFactory.getLogger(RedisMarketCache.class);
	private final StringRedisTemplate redis;
	private java.time.Instant unavailableUntil = java.time.Instant.MIN;

	public RedisMarketCache(StringRedisTemplate redis) {
		this.redis = redis;
	}

	@Async("marketCacheExecutor")
	public void put(String key, String value, Duration ttl) {
		write(key, value, ttl);
	}

	private synchronized void write(String key, String value, Duration ttl) {
		if (java.time.Instant.now().isBefore(unavailableUntil)) return;
		try {
			redis.opsForValue().set(key, value, ttl);
			unavailableUntil = java.time.Instant.MIN;
		} catch (DataAccessException exception) {
			unavailableUntil = java.time.Instant.now().plusSeconds(60);
			logger.error("Redis market cache write failed for key {}", key, exception);
		}
	}
}
