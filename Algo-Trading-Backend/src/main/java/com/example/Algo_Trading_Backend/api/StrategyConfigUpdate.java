package com.example.Algo_Trading_Backend.api;

import java.math.BigDecimal;

public record StrategyConfigUpdate(
		Boolean enabled,
		Integer strikeRange,
		BigDecimal bullishThreshold,
		BigDecimal bearishThreshold,
		Integer trendWindow,
		BigDecimal trendTolerance,
		BigDecimal maxDistanceAtr,
		Integer atrPeriod,
		BigDecimal atrMultiplier,
		BigDecimal minimumRewardRisk,
		BigDecimal maximumRewardRisk,
		BigDecimal oiBuffer,
		Long minimumChangeOiDenominator,
		Long cooldownSeconds,
		Boolean paperTradingEnabled,
		Integer maxOpenTradesPerUnderlying,
		Integer maxTradesPerSession,
		BigDecimal dailyLossLimit,
		BigDecimal brokeragePerOrder,
		BigDecimal slippagePerUnit) {
}
