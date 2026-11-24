package com.example.Algo_Trading_Backend.market;

import java.time.Instant;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

import com.example.Algo_Trading_Backend.config.MarketSessionProperties;

@Service
public class MarketSessionService {
	private final MarketSessionProperties properties;

	public MarketSessionService(MarketSessionProperties properties) {
		this.properties = properties;
	}

	public boolean isOpen(Instant instant) {
		ZonedDateTime local = instant.atZone(properties.getZone());
		return local.getDayOfWeek().getValue() <= 5
				&& !properties.getHolidays().contains(local.toLocalDate())
				&& !local.toLocalTime().isBefore(properties.getOpen())
				&& local.toLocalTime().isBefore(properties.getClose());
	}
}
