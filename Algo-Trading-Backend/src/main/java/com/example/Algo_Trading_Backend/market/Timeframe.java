package com.example.Algo_Trading_Backend.market;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Timeframe(Duration duration) {
	private static final Pattern VALUE = Pattern.compile("^(\\d+)([smh])$");

	public static Timeframe parse(String value) {
		Matcher matcher = VALUE.matcher(value == null ? "" : value.trim().toLowerCase());
		if (!matcher.matches()) throw new IllegalArgumentException("Timeframe must use a supported value such as 1m, 5m, or 1h");
		long amount = Long.parseLong(matcher.group(1));
		if (amount < 1 || amount > 1440) throw new IllegalArgumentException("Timeframe amount must be between 1 and 1440");
		Duration duration = switch (matcher.group(2)) {
			case "s" -> Duration.ofSeconds(amount);
			case "m" -> Duration.ofMinutes(amount);
			case "h" -> Duration.ofHours(amount);
			default -> throw new IllegalArgumentException("Unsupported timeframe unit");
		};
		if (duration.compareTo(Duration.ofDays(1)) > 0) throw new IllegalArgumentException("Timeframe cannot exceed one day");
		return new Timeframe(duration);
	}
}
