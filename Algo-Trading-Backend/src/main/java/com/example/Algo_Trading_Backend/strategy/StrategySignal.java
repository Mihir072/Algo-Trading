package com.example.Algo_Trading_Backend.strategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StrategySignal(
		String underlying,
		SignalDirection direction,
		StrategyInput inputs,
		RiskPlan riskPlan,
		List<String> reasons,
		Instant generatedAt) {
	public StrategySignal {
		reasons = List.copyOf(reasons);
	}
}
