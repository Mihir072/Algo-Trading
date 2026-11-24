package com.example.Algo_Trading_Backend.strategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.Algo_Trading_Backend.analytics.PcrTrendService;
import com.example.Algo_Trading_Backend.config.StrategyProperties;
import com.example.Algo_Trading_Backend.market.DataStatus;

@Service
public class SignalGenerator {
	private final StrategyProperties properties;
	private final RiskManagementService riskManagement;

	public SignalGenerator(StrategyProperties properties, RiskManagementService riskManagement) {
		this.properties = properties;
		this.riskManagement = riskManagement;
	}

	public StrategySignal generate(StrategyInput input) {
		List<String> reasons = new ArrayList<>();
		if (!properties.isEnabled()) reasons.add("Strategy is disabled");
		if (input.dataStatus() != DataStatus.VALID) reasons.add("Market data is not valid and fresh");
		if (input.futuresPrice() == null || input.vwap() == null) reasons.add("Futures price or VWAP is unavailable");
		if (input.atr() == null || input.atr().signum() <= 0) reasons.add("Valid ATR is unavailable");
		if (input.pcrOi() == null || input.pcrOi().signum() < 0) reasons.add("Valid total OI PCR is unavailable");
		if (input.pcrTrend() == null || input.pcrTrend().trend() == PcrTrendService.Trend.UNKNOWN) {
			reasons.add("PCR trend is unknown or has insufficient observations");
		}
		if (!input.oiLevelsValid() || input.adjustedSupport() == null || input.adjustedResistance() == null) {
			reasons.add("Valid OI support and resistance levels are unavailable");
		}
		if (!reasons.isEmpty()) return noTrade(input, reasons);

		BigDecimal distanceAtr = input.futuresPrice().subtract(input.vwap()).abs()
				.divide(input.atr(), java.math.MathContext.DECIMAL128);
		if (distanceAtr.compareTo(properties.getVwap().getMaxDistanceAtr()) > 0) {
			reasons.add("Futures price is too far from VWAP in ATR units");
		}
		boolean longConditions = input.futuresPrice().compareTo(input.vwap()) > 0
				&& input.pcrOi().compareTo(properties.getPcr().getBullishThreshold()) >= 0
				&& input.pcrTrend().trend() != PcrTrendService.Trend.FALLING;
		boolean shortConditions = input.futuresPrice().compareTo(input.vwap()) < 0
				&& input.pcrOi().compareTo(properties.getPcr().getBearishThreshold()) <= 0
				&& input.pcrTrend().trend() != PcrTrendService.Trend.RISING;
		if (!longConditions && !shortConditions) {
			reasons.add("Directional futures, PCR, and trend conditions are not aligned");
		}
		if (!reasons.isEmpty()) return noTrade(input, reasons);

		SignalDirection direction = longConditions ? SignalDirection.LONG : SignalDirection.SHORT;
		try {
			RiskPlan plan = riskManagement.calculate(direction, input.futuresPrice(), input.atr(),
					input.adjustedSupport(), input.adjustedResistance());
			return new StrategySignal(input.underlying(), direction, input, plan, List.of(), Instant.now());
		} catch (IllegalArgumentException exception) {
			reasons.add(exception.getMessage());
			return noTrade(input, reasons);
		}
	}

	private StrategySignal noTrade(StrategyInput input, List<String> reasons) {
		return new StrategySignal(input.underlying(), SignalDirection.NO_TRADE, input, null, reasons, Instant.now());
	}
}
