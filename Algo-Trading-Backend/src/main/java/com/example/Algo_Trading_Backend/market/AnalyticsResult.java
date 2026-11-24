package com.example.Algo_Trading_Backend.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AnalyticsResult(
		BigDecimal value,
		DataStatus status,
		Instant calculatedAt,
		List<Instant> sourceTimestamps) {
	public AnalyticsResult {
		sourceTimestamps = List.copyOf(sourceTimestamps);
	}
}
