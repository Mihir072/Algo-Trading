package com.example.Algo_Trading_Backend.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.Algo_Trading_Backend.config.StrategyProperties;
import com.example.Algo_Trading_Backend.market.DataStatus;
import com.example.Algo_Trading_Backend.market.OptionChainSnapshot;
import com.example.Algo_Trading_Backend.market.OptionContract;
import com.example.Algo_Trading_Backend.market.OptionType;

@Service
public class OptionChainAnalyticsService {
	private static final int RATIO_SCALE = 8;
	private final StrategyProperties properties;

	public OptionChainAnalyticsService(StrategyProperties properties) {
		this.properties = properties;
	}

	public OptionChainSnapshot calculate(String underlying, LocalDate expiry, BigDecimal spot,
			List<OptionContract> chain, Map<String, Long> previousOpenInterest) {
		Objects.requireNonNull(underlying, "underlying");
		Objects.requireNonNull(expiry, "expiry");
		Objects.requireNonNull(spot, "spot");
		if (spot.signum() <= 0) {
			throw new IllegalArgumentException("Spot price must be positive");
		}
		if (properties.getPcr().getStrikeRange() < 0) {
			throw new IllegalStateException("PCR strike range cannot be negative");
		}
		Map<String, Long> baseline = previousOpenInterest == null ? Map.of() : previousOpenInterest;

		Map<BigDecimal, Map<OptionType, OptionContract>> byStrike = chain.stream()
				.filter(contract -> contract.strike() != null && contract.expiry() != null
						&& contract.optionType() != null && contract.token() != null)
				.filter(contract -> contract.underlying() != null
						&& contract.underlying().equalsIgnoreCase(underlying))
				.filter(contract -> contract.expiry().equals(expiry))
				.collect(Collectors.groupingBy(OptionContract::strike,
						Collectors.toMap(OptionContract::optionType, Function.identity())));
		List<BigDecimal> strikes = byStrike.keySet().stream().sorted().toList();
		int atmIndex = nearestStrikeIndex(strikes, spot);
		int range = properties.getPcr().getStrikeRange();
		int from = atmIndex - range;
		int to = atmIndex + range;
		if (from < 0 || to >= strikes.size()) {
			throw new IllegalArgumentException("Option chain does not contain the complete ATM strike window");
		}
		List<BigDecimal> selected = strikes.subList(from, to + 1);
		if (selected.size() != range * 2 + 1) {
			throw new IllegalStateException("Selected option window has an unexpected strike count");
		}

		List<OptionContract> selectedContracts = new ArrayList<>(selected.size() * 2);
		long ceOi = 0;
		long peOi = 0;
		long ceChange = 0;
		long peChange = 0;
		boolean changeAvailable = true;
		BigDecimal support = null;
		BigDecimal resistance = null;
		long supportOi = -1;
		long resistanceOi = -1;
		for (BigDecimal strike : selected) {
			Map<OptionType, OptionContract> pair = byStrike.get(strike);
			OptionContract call = pair.get(OptionType.CE);
			OptionContract put = pair.get(OptionType.PE);
			if (call == null || put == null) {
				throw new IllegalArgumentException("Both CE and PE contracts are required at every selected strike");
			}
			if (call.openInterest() == null || put.openInterest() == null
					|| call.openInterest() < 0 || put.openInterest() < 0) {
				throw new IllegalArgumentException("Open interest must be available and non-negative in the selected window");
			}
			selectedContracts.add(call);
			selectedContracts.add(put);
			ceOi = Math.addExact(ceOi, call.openInterest());
			peOi = Math.addExact(peOi, put.openInterest());
			Long priorCall = baseline.get(call.token());
			Long priorPut = baseline.get(put.token());
			if (priorCall == null || priorPut == null) {
				changeAvailable = false;
			} else {
				ceChange = Math.addExact(ceChange, Math.subtractExact(call.openInterest(), priorCall));
				peChange = Math.addExact(peChange, Math.subtractExact(put.openInterest(), priorPut));
			}
			if (put.openInterest() > supportOi) {
				supportOi = put.openInterest();
				support = strike;
			}
			if (call.openInterest() > resistanceOi) {
				resistanceOi = call.openInterest();
				resistance = strike;
			}
		}
		if (ceOi <= 0) {
			throw new IllegalArgumentException("Call open interest denominator must be positive");
		}
		BigDecimal pcr = ratio(peOi, ceOi);
		boolean validChangeRatio = changeAvailable && ceChange > properties.getPcr().getMinimumChangeOiDenominator();
		BigDecimal pcrChange = validChangeRatio ? ratio(peChange, ceChange) : null;
		return new OptionChainSnapshot(underlying.toUpperCase(), expiry, strikes.get(atmIndex), selected,
				ceOi, peOi, changeAvailable ? ceChange : 0, changeAvailable ? peChange : 0,
				pcr, pcrChange, validChangeRatio ? DataStatus.VALID : DataStatus.UNKNOWN,
				support, supportOi, resistance, resistanceOi, selectedContracts, Instant.now());
	}

	private int nearestStrikeIndex(List<BigDecimal> strikes, BigDecimal spot) {
		return java.util.stream.IntStream.range(0, strikes.size()).boxed()
				.min(Comparator.comparing(index -> strikes.get(index).subtract(spot).abs()))
				.orElseThrow(() -> new IllegalArgumentException("No option strikes are available"));
	}

	private BigDecimal ratio(long numerator, long denominator) {
		return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator),
				RATIO_SCALE, RoundingMode.HALF_UP);
	}
}
