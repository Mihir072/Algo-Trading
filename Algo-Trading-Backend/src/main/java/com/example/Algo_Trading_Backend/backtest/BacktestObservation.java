package com.example.Algo_Trading_Backend.backtest;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.Algo_Trading_Backend.market.FuturesCandle;
import com.example.Algo_Trading_Backend.market.OptionChainSnapshot;

public record BacktestObservation(
		Instant timestamp,
		BigDecimal spotPrice,
		FuturesCandle futuresCandle,
		OptionChainSnapshot optionSnapshot) {
}
