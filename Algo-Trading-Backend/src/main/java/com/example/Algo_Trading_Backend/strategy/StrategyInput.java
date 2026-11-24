package com.example.Algo_Trading_Backend.strategy;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.Algo_Trading_Backend.analytics.PcrTrendService;
import com.example.Algo_Trading_Backend.market.DataStatus;

public record StrategyInput(
		String underlying,
		BigDecimal futuresPrice,
		BigDecimal vwap,
		BigDecimal atr,
		BigDecimal pcrOi,
		BigDecimal pcrChangeOi,
		PcrTrendService.Result pcrTrend,
		BigDecimal adjustedSupport,
		BigDecimal adjustedResistance,
		DataStatus dataStatus,
		Instant marketDataTimestamp,
		boolean oiLevelsValid) {
}
