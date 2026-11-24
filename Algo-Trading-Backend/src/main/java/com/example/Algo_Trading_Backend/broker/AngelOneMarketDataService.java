package com.example.Algo_Trading_Backend.broker;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.Algo_Trading_Backend.api.MarketStateStore;
import com.example.Algo_Trading_Backend.market.DataStatus;

import tools.jackson.databind.JsonNode;

@Service
public class AngelOneMarketDataService {
	private static final Logger logger = LoggerFactory.getLogger(AngelOneMarketDataService.class);
	private static final Duration MIN_REFRESH_INTERVAL = Duration.ofMinutes(15);
	private static final Duration STALE_AFTER = Duration.ofMinutes(30);
	private static final DateTimeFormatter GREEKS_EXPIRY = DateTimeFormatter.ofPattern("ddMMMyyyy", Locale.ENGLISH);
	private final AngelOneProperties properties;
	private final AngelOneAuthService auth;
	private final MarketStateStore stateStore;
	private final RestClient restClient;
	private final Map<String, GreeksSnapshot> greeks = new ConcurrentHashMap<>();

	public record GreeksSnapshot(String underlying, LocalDate expiry, JsonNode data,
			DataStatus status, Instant updatedAt) {}

	public AngelOneMarketDataService(AngelOneProperties properties, AngelOneAuthService auth,
			MarketStateStore stateStore) {
		this.properties = properties;
		this.auth = auth;
		this.stateStore = stateStore;
		this.restClient = RestClient.create(properties.getBaseUrl());
	}

	public GreeksSnapshot getGreeks(String underlying) {
		var state = stateStore.get(underlying);
		if (state == null || state.optionChain() == null) {
			return new GreeksSnapshot(underlying, null, null, DataStatus.MISSING, null);
		}
		String key = underlying.toUpperCase();
		GreeksSnapshot current = greeks.get(key);
		if (current != null && current.updatedAt() != null
				&& current.updatedAt().isAfter(Instant.now().minus(MIN_REFRESH_INTERVAL))) {
			return staleIfOld(current);
		}
		return refreshGreeks(underlying, state.optionChain().expiry());
	}

	@Scheduled(fixedDelay = 900000, initialDelay = 900000)
	public void refreshAvailableGreeks() {
		if (auth.currentSession() == null) return;
		for (var state : stateStore.all().values()) {
			if (state.optionChain() == null) continue;
			GreeksSnapshot previous = greeks.get(state.underlying());
			if (previous != null && previous.updatedAt().isAfter(Instant.now().minus(MIN_REFRESH_INTERVAL))) continue;
			try {
				refreshGreeks(state.underlying(), state.optionChain().expiry());
			} catch (RestClientException | IllegalStateException exception) {
				logger.warn("Could not refresh Angel One Greeks for {}: {}", state.underlying(), exception.getMessage());
			}
		}
	}

	private GreeksSnapshot refreshGreeks(String underlying, LocalDate expiry) {
		var session = auth.currentSession();
		if (session == null) throw new IllegalStateException("Authenticate with Angel One before requesting Greeks");
		JsonNode response = restClient.post()
				.uri("/rest/secure/angelbroking/marketData/v1/optionGreek")
				.headers(headers -> {
					auth.populateHeaders(headers);
					headers.setBearerAuth(session.accessToken());
				})
				.body(Map.of("name", underlying.toUpperCase(),
						"expirydate", expiry.format(GREEKS_EXPIRY).toUpperCase(Locale.ENGLISH)))
				.retrieve().body(JsonNode.class);
		if (response == null || !response.path("status").asBoolean(false) || !response.has("data")) {
			String message = response == null ? "empty response" : response.path("message").asText("missing data");
			throw new IllegalStateException("Angel One Greeks request failed: " + message);
		}
		GreeksSnapshot result = new GreeksSnapshot(underlying.toUpperCase(), expiry,
				response.path("data"), DataStatus.VALID, Instant.now());
		greeks.put(underlying.toUpperCase(), result);
		return result;
	}

	private GreeksSnapshot staleIfOld(GreeksSnapshot snapshot) {
		if (snapshot.updatedAt().isBefore(Instant.now().minus(STALE_AFTER))) {
			return new GreeksSnapshot(snapshot.underlying(), snapshot.expiry(), snapshot.data(),
					DataStatus.STALE, snapshot.updatedAt());
		}
		return snapshot;
	}
}
