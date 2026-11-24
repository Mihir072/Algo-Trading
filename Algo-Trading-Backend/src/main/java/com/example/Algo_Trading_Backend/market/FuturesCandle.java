package com.example.Algo_Trading_Backend.market;

import java.math.BigDecimal;
import java.time.Instant;

public record FuturesCandle(
		String token,
		BigDecimal open,
		BigDecimal high,
		BigDecimal low,
		BigDecimal close,
		long volume,
		Instant timestamp) {
}
