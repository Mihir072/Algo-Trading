package com.example.Algo_Trading_Backend.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.Algo_Trading_Backend.broker.AngelOneAuthService;
import com.example.Algo_Trading_Backend.broker.Instrument;
import com.example.Algo_Trading_Backend.broker.InstrumentMasterService;
import com.example.Algo_Trading_Backend.broker.SmartStreamMarketDataClient;

@RestController
@RequestMapping("/api/v1/broker")
public class BrokerController {
	private final AngelOneAuthService auth;
	private final InstrumentMasterService instruments;
	private final SmartStreamMarketDataClient stream;

	public BrokerController(AngelOneAuthService auth, InstrumentMasterService instruments,
			SmartStreamMarketDataClient stream) {
		this.auth = auth;
		this.instruments = instruments;
		this.stream = stream;
	}

	public record SessionStatus(boolean authenticated, Instant createdAt) {}

	@PostMapping("/session")
	public SessionStatus authenticate() {
		var session = auth.login();
		if (!instruments.all().isEmpty()) stream.connect();
		return new SessionStatus(true, session.createdAt());
	}

	@GetMapping("/session")
	public SessionStatus session() {
		var session = auth.currentSession();
		return new SessionStatus(session != null, session == null ? null : session.createdAt());
	}

	@PostMapping("/session/refresh")
	public SessionStatus refresh() {
		var session = auth.refresh();
		return new SessionStatus(true, session.createdAt());
	}

	@PostMapping("/instruments/refresh")
	public Map<String, Integer> refreshInstruments() {
		instruments.download();
		stream.refreshSubscriptions();
		return Map.of("loaded", instruments.all().size());
	}

	@GetMapping("/instruments")
	public List<Instrument> instruments() {
		return instruments.all();
	}

	@PostMapping("/market-stream/connect")
	public Map<String, Object> connectStream() {
		stream.connect();
		return stream.status();
	}

	@org.springframework.web.bind.annotation.DeleteMapping("/market-stream")
	public Map<String, Object> disconnectStream() {
		stream.disconnect();
		return stream.status();
	}

	@GetMapping("/market-stream")
	public Map<String, Object> streamStatus() {
		return stream.status();
	}
}
