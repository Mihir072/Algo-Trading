package com.example.Algo_Trading_Backend.strategy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import com.example.Algo_Trading_Backend.config.StrategyProperties;

@Service
public class RiskManagementService {
	private final StrategyProperties properties;

	public RiskManagementService(StrategyProperties properties) {
		this.properties = properties;
	}

	public RiskPlan calculate(SignalDirection direction, BigDecimal entry, BigDecimal atr,
			BigDecimal adjustedSupport, BigDecimal adjustedResistance) {
		if (direction == SignalDirection.NO_TRADE || entry == null || atr == null
				|| adjustedSupport == null || adjustedResistance == null
				|| entry.signum() <= 0 || atr.signum() <= 0) {
			throw new IllegalArgumentException("Positive entry and ATR and both OI walls are required");
		}
		BigDecimal atrDistance = atr.multiply(properties.getAtr().getMultiplier());
		BigDecimal buffer = properties.getRisk().getOiBuffer();
		BigDecimal atrStop;
		BigDecimal oiStop;
		BigDecimal stop;
		BigDecimal wall;
		if (direction == SignalDirection.LONG) {
			atrStop = entry.subtract(atrDistance);
			oiStop = adjustedSupport.subtract(buffer);
			if (oiStop.signum() <= 0 || oiStop.compareTo(entry) >= 0) {
				throw new IllegalArgumentException("Adjusted OI support is not below the long entry");
			}
			stop = atrStop.max(oiStop);
			wall = adjustedResistance;
		} else {
			atrStop = entry.add(atrDistance);
			oiStop = adjustedResistance.add(buffer);
			if (oiStop.compareTo(entry) <= 0) {
				throw new IllegalArgumentException("Adjusted OI resistance is not above the short entry");
			}
			stop = atrStop.min(oiStop);
			wall = adjustedSupport;
		}
		if (stop.signum() <= 0 || (direction == SignalDirection.LONG && stop.compareTo(entry) >= 0)
				|| (direction == SignalDirection.SHORT && stop.compareTo(entry) <= 0)) {
			throw new IllegalArgumentException("Calculated stop loss is invalid");
		}
		BigDecimal risk = entry.subtract(stop).abs();
		if (risk.signum() == 0) {
			throw new IllegalArgumentException("Risk distance must be positive");
		}
		BigDecimal maxDistance = risk.multiply(properties.getRisk().getMaximumRewardRisk());
		BigDecimal target = direction == SignalDirection.LONG
				? wall.min(entry.add(maxDistance))
				: wall.max(entry.subtract(maxDistance));
		if ((direction == SignalDirection.LONG && target.compareTo(entry) <= 0)
				|| (direction == SignalDirection.SHORT && target.compareTo(entry) >= 0)) {
			throw new IllegalArgumentException("Opposite OI wall is on the wrong side of entry");
		}
		BigDecimal reward = target.subtract(entry).abs();
		BigDecimal rewardRisk = reward.divide(risk, 8, RoundingMode.HALF_UP);
		if (rewardRisk.compareTo(properties.getRisk().getMinimumRewardRisk()) < 0) {
			throw new IllegalArgumentException("Reward-to-risk is below the configured minimum");
		}
		return new RiskPlan(entry, stop, target, risk, reward, rewardRisk, atrStop, oiStop, wall);
	}
}
