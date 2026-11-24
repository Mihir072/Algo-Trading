package com.example.Algo_Trading_Backend.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record OptionChainSnapshot(
		String underlying,
		LocalDate expiry,
		BigDecimal atmStrike,
		List<BigDecimal> selectedStrikes,
		long totalCeOi,
		long totalPeOi,
		long ceChangeOi,
		long peChangeOi,
		BigDecimal pcrOi,
		BigDecimal pcrChangeOi,
		DataStatus pcrChangeStatus,
		BigDecimal supportStrike,
		long supportOi,
		BigDecimal resistanceStrike,
		long resistanceOi,
		List<OptionContract> contracts,
		Instant calculatedAt) {
	public OptionChainSnapshot {
		selectedStrikes = List.copyOf(selectedStrikes);
		contracts = List.copyOf(contracts);
	}
}
