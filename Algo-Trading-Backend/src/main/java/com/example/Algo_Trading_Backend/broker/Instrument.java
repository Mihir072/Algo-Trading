package com.example.Algo_Trading_Backend.broker;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.Algo_Trading_Backend.market.OptionType;

public record Instrument(
		String underlying,
		String token,
		String tradingSymbol,
		String exchange,
		String instrumentType,
		LocalDate expiry,
		BigDecimal strike,
		OptionType optionType,
		int lotSize) {
}
