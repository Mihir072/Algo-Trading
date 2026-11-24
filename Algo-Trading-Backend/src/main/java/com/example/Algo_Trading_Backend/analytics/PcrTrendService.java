package com.example.Algo_Trading_Backend.analytics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.Algo_Trading_Backend.config.StrategyProperties;

@Service
public class PcrTrendService {
	public enum Trend { RISING, FALLING, FLAT, UNKNOWN }

	public record Result(Trend trend, BigDecimal slope, int observations) {}

	private final StrategyProperties properties;

	public PcrTrendService(StrategyProperties properties) {
		this.properties = properties;
	}

	public Result calculate(List<BigDecimal> observations) {
		int window = properties.getPcr().getTrendWindow();
		if (window < 2) {
			throw new IllegalStateException("PCR trend window must be at least two observations");
		}
		if (observations.size() < window) {
			return new Result(Trend.UNKNOWN, null, observations.size());
		}
		List<BigDecimal> values = observations.subList(observations.size() - window, observations.size());
		if (values.stream().anyMatch(value -> value == null || value.signum() < 0)) {
			return new Result(Trend.UNKNOWN, null, values.size());
		}
		BigDecimal n = BigDecimal.valueOf(window);
		BigDecimal sumY = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal sumXY = BigDecimal.ZERO;
		BigDecimal sumX = BigDecimal.ZERO;
		BigDecimal sumXX = BigDecimal.ZERO;
		for (int index = 0; index < window; index++) {
			BigDecimal x = BigDecimal.valueOf(index);
			sumX = sumX.add(x);
			sumXX = sumXX.add(x.multiply(x));
			sumXY = sumXY.add(x.multiply(values.get(index)));
		}
		BigDecimal numerator = n.multiply(sumXY).subtract(sumX.multiply(sumY));
		BigDecimal denominator = n.multiply(sumXX).subtract(sumX.multiply(sumX));
		BigDecimal slope = numerator.divide(denominator, MathContext.DECIMAL128);
		BigDecimal tolerance = properties.getPcr().getTrendTolerance();
		if (slope.abs().compareTo(tolerance) <= 0) {
			return new Result(Trend.FLAT, slope, window);
		}
		return new Result(slope.signum() > 0 ? Trend.RISING : Trend.FALLING, slope, window);
	}
}
