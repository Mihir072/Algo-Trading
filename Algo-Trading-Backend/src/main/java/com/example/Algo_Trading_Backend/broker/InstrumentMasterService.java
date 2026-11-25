package com.example.Algo_Trading_Backend.broker;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.example.Algo_Trading_Backend.market.OptionType;
import tools.jackson.databind.JsonNode;

@Service
public class InstrumentMasterService {
	private static final DateTimeFormatter ANGEL_EXPIRY = new DateTimeFormatterBuilder()
			.parseCaseInsensitive().appendPattern("ddMMMyyyy").toFormatter(Locale.ENGLISH);
	private final AngelOneProperties properties;
	private final RestClient restClient;
	private final JdbcTemplate jdbcTemplate;
	private final AtomicReference<List<Instrument>> instruments = new AtomicReference<>(List.of());

	public InstrumentMasterService(AngelOneProperties properties, JdbcTemplate jdbcTemplate) {
		this.properties = properties;
		this.jdbcTemplate = jdbcTemplate;
		this.restClient = RestClient.create();
	}

	@Scheduled(initialDelay = 1000, fixedDelayString = "${angelone.instrument-refresh-millis:21600000}")
	public void refresh() {
		if (!properties.getInstrumentMaster().isEnabled()) return;
		download();
	}

	public void download() {
		JsonNode root = restClient.get().uri(properties.getInstrumentMasterUrl())
				.retrieve().body(JsonNode.class);
		if (root == null || !root.isArray()) {
			throw new IllegalStateException("Angel One instrument master response is not a JSON array");
		}
		List<Instrument> parsed = new java.util.ArrayList<>();
		root.forEach(node -> parse(node).ifPresent(parsed::add));
		if (parsed.isEmpty()) throw new IllegalStateException("Angel One instrument master contained no supported NIFTY/BANKNIFTY instruments");
		persist(parsed);
		instruments.set(List.copyOf(parsed));
	}

	private void persist(List<Instrument> values) {
		jdbcTemplate.batchUpdate("""
				INSERT INTO instrument (symbol, token, exchange, instrument_type, expiry, strike, option_type, lot_size, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (exchange, token) DO UPDATE SET
					symbol = EXCLUDED.symbol,
					instrument_type = EXCLUDED.instrument_type,
					expiry = EXCLUDED.expiry,
					strike = EXCLUDED.strike,
					option_type = EXCLUDED.option_type,
					lot_size = EXCLUDED.lot_size,
					updated_at = EXCLUDED.updated_at
				""", values, 500, (statement, instrument) -> {
			statement.setString(1, instrument.underlying());
			statement.setString(2, instrument.token());
			statement.setString(3, instrument.exchange());
			statement.setString(4, instrument.instrumentType());
			statement.setObject(5, instrument.expiry(), Types.DATE);
			statement.setBigDecimal(6, instrument.strike());
			statement.setString(7, instrument.optionType() == null ? null : instrument.optionType().name());
			statement.setInt(8, instrument.lotSize());
			statement.setTimestamp(9, Timestamp.from(Instant.now()));
		});
	}

	public List<Instrument> find(String underlying) {
		return instruments.get().stream().filter(item -> item.underlying().equalsIgnoreCase(underlying))
				.sorted(Comparator.comparing(Instrument::expiry, Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(Instrument::strike, Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();
	}

	public List<Instrument> all() {
		return instruments.get();
	}

	public Instrument nearestMonthlyFuture(String underlying, LocalDate date) {
		return find(underlying).stream()
				.filter(item -> item.instrumentType().equals("FUTIDX") && item.expiry() != null
						&& !item.expiry().isBefore(date))
				.min(Comparator.comparing(Instrument::expiry))
				.orElseThrow(() -> new IllegalStateException("No current or next-month future contract for " + underlying));
	}

	java.util.Optional<Instrument> parse(JsonNode node) {
		String underlying = text(node, "name");
		String type = text(node, "instrumenttype");
		String exchange = text(node, "exch_seg");
		if (underlying == null || type == null || exchange == null
				|| !(underlying.equals("NIFTY") || underlying.equals("BANKNIFTY"))) return java.util.Optional.empty();
		boolean derivative = exchange.equals("NFO");
		boolean index = (exchange.equals("NSE") || exchange.equals("NSE_CM")) && type.equals("AMXIDX");
		if (!derivative && !index) return java.util.Optional.empty();
		try {
			String expiryText = text(node, "expiry");
			LocalDate expiry = expiryText == null || expiryText.isBlank() ? null
					: LocalDate.parse(expiryText, ANGEL_EXPIRY);
			String option = text(node, "symbol");
			OptionType optionType = option != null && option.endsWith("CE") ? OptionType.CE
					: option != null && option.endsWith("PE") ? OptionType.PE : null;
			String token = text(node, "token");
			String symbol = text(node, "symbol");
			if (token == null || symbol == null) return java.util.Optional.empty();
			BigDecimal strike = null;
			if (optionType != null) {
				BigDecimal rawStrike = new BigDecimal(text(node, "strike"));
				strike = rawStrike.divide(new BigDecimal("100"));
			}
			int lotSize = Integer.parseInt(text(node, "lotsize"));
			return java.util.Optional.of(new Instrument(underlying, token, symbol, exchange,
					type, expiry, strike, optionType, lotSize));
		} catch (NumberFormatException | DateTimeParseException exception) {
			return java.util.Optional.empty();
		}
	}

	private String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? null : value.asText();
	}
}
