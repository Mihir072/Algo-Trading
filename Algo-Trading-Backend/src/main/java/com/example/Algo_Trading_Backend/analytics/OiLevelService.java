package com.example.Algo_Trading_Backend.analytics;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.example.Algo_Trading_Backend.market.OptionChainSnapshot;

@Service
public class OiLevelService {
	public record Levels(BigDecimal basis, BigDecimal adjustedSupport, BigDecimal adjustedResistance) {}

	public Levels adjustToFutures(OptionChainSnapshot snapshot, BigDecimal futuresPrice, BigDecimal spotPrice) {
		if (futuresPrice == null || spotPrice == null) {
			throw new IllegalArgumentException("Spot and futures prices are required to calculate basis-adjusted OI levels");
		}
		BigDecimal basis = futuresPrice.subtract(spotPrice);
		return new Levels(basis, snapshot.supportStrike().add(basis), snapshot.resistanceStrike().add(basis));
	}
}
