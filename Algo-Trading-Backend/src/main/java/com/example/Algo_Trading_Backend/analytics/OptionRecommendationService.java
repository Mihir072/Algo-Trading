package com.example.Algo_Trading_Backend.analytics;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.example.Algo_Trading_Backend.market.DataStatus;
import com.example.Algo_Trading_Backend.market.OptionContract;
import com.example.Algo_Trading_Backend.market.OptionType;
import com.example.Algo_Trading_Backend.strategy.SignalDirection;

@Service
public class OptionRecommendationService {
	public record Recommendation(DataStatus status, String tradingSymbol, BigDecimal strike,
			java.time.LocalDate expiry, OptionType optionType, BigDecimal ltp, BigDecimal bid,
			BigDecimal ask, Long volume, Long openInterest, Instant marketDataTimestamp,
			String note) {}

	public Recommendation recommend(SignalDirection signal, BigDecimal spot, java.util.List<OptionContract> contracts) {
		if (signal == null || signal == SignalDirection.NO_TRADE || spot == null || spot.signum() <= 0) {
			return unavailable("No directional signal and valid spot price are available");
		}
		OptionType requiredType = signal == SignalDirection.LONG ? OptionType.CE : OptionType.PE;
		OptionContract selected = contracts.stream()
				.filter(contract -> contract.optionType() == requiredType)
				.filter(contract -> contract.ltp() != null && contract.ltp().signum() > 0)
				.filter(contract -> contract.strike() != null)
				.min(java.util.Comparator.comparing(contract -> contract.strike().subtract(spot).abs()))
				.orElse(null);
		if (selected == null) return unavailable("A live ATM option quote is not available");
		if (selected.updatedAt() == null || selected.updatedAt().isAfter(Instant.now())
				|| selected.updatedAt().isBefore(Instant.now().minusSeconds(60))) {
			return new Recommendation(DataStatus.STALE, selected.tradingSymbol(), selected.strike(),
					selected.expiry(), selected.optionType(), selected.ltp(), selected.bid(), selected.ask(),
					selected.volume(), selected.openInterest(), selected.updatedAt(),
					"Option quote is stale; recommendation is not available for execution");
		}
		return new Recommendation(DataStatus.VALID, selected.tradingSymbol(), selected.strike(),
				selected.expiry(), selected.optionType(), selected.ltp(), selected.bid(), selected.ask(),
				selected.volume(), selected.openInterest(), selected.updatedAt(),
				"Recommendation only; futures levels are not option-premium stop or target prices");
	}

	private Recommendation unavailable(String note) {
		return new Recommendation(DataStatus.MISSING, null, null, null, null,
				null, null, null, null, null, null, note);
	}
}
