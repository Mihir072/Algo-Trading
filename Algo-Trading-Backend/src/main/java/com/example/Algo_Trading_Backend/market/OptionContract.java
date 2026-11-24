package com.example.Algo_Trading_Backend.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record OptionContract(
		String underlying,
		String token,
		String tradingSymbol,
		LocalDate expiry,
		BigDecimal strike,
		OptionType optionType,
		Long openInterest,
		Long volume,
		BigDecimal ltp,
		BigDecimal bid,
		BigDecimal ask,
		Instant updatedAt) {
}
