package com.example.Algo_Trading_Backend.broker;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.example.Algo_Trading_Backend.api.MarketStateStore;
import com.example.Algo_Trading_Backend.config.StrategyProperties;
import com.example.Algo_Trading_Backend.market.OptionContract;
import com.example.Algo_Trading_Backend.market.OptionType;

@Service
public class SmartStreamMarketDataClient {
	private static final Logger logger = LoggerFactory.getLogger(SmartStreamMarketDataClient.class);
	private static final String STREAM_URL = "wss://smartapisocket.angelone.in/smart-stream";
	private static final int SNAP_QUOTE_MODE = 3;
	private final AngelOneAuthService auth;
	private final InstrumentMasterService instruments;
	private final CandleAggregationService candles;
	private final MarketStateStore marketState;
	private final StrategyProperties strategyProperties;
	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final Map<String, Instrument> byToken = new ConcurrentHashMap<>();
	private final Map<String, Long> lastSequenceByToken = new ConcurrentHashMap<>();
	private final Set<String> subscribed = ConcurrentHashMap.newKeySet();
	private final AtomicBoolean connecting = new AtomicBoolean(false);
	private final AtomicBoolean stopping = new AtomicBoolean(false);
	private final AtomicInteger reconnectAttempts = new AtomicInteger();
	private final AtomicInteger malformedPackets = new AtomicInteger();
	private volatile WebSocket socket;
	private volatile String lastError = "none";

	public SmartStreamMarketDataClient(AngelOneAuthService auth, InstrumentMasterService instruments,
			CandleAggregationService candles, MarketStateStore marketState, StrategyProperties strategyProperties) {
		this.auth = auth;
		this.instruments = instruments;
		this.candles = candles;
		this.marketState = marketState;
		this.strategyProperties = strategyProperties;
	}

	public void connect() {
		if (auth.currentSession() == null) throw new IllegalStateException("Authenticate with Angel One before connecting the market stream");
		if (instruments.all().isEmpty()) throw new IllegalStateException("Load the instrument master before connecting the market stream");
		stopping.set(false);
		connectAsync();
	}

	public void refreshSubscriptions() {
		if (socket == null || socket.isOutputClosed()) return;
		subscribeInitialInstruments();
	}

	public void disconnect() {
		stopping.set(true);
		WebSocket current = socket;
		if (current != null) current.sendClose(WebSocket.NORMAL_CLOSURE, "client disconnect");
		socket = null;
		subscribed.clear();
		lastSequenceByToken.clear();
	}

	@PreDestroy
	public void shutdown() {
		disconnect();
	}

	public Map<String, Object> status() {
		return Map.of("connected", socket != null && !socket.isOutputClosed(),
				"subscribedTokens", subscribed.size(),
				"reconnectAttempts", reconnectAttempts.get(),
				"malformedPackets", malformedPackets.get(),
				"lastError", lastError);
	}

	private void connectAsync() {
		if (stopping.get() || !connecting.compareAndSet(false, true)) return;
		var session = auth.currentSession();
		if (session == null) {
			connecting.set(false);
			throw new IllegalStateException("Angel One session expired; authenticate again");
		}
		httpClient.newWebSocketBuilder()
				.header("x-client-code", authClientCode())
				.header("x-feed-token", session.feedToken())
				.header("x-client-lib", "JAVA")
				.buildAsync(URI.create(STREAM_URL), new StreamListener())
				.whenComplete((connectedSocket, error) -> {
					connecting.set(false);
					if (error != null) {
						Throwable cause = error.getCause() == null ? error : error.getCause();
						if (cause instanceof WebSocketHandshakeException handshake
								&& (handshake.getResponse().statusCode() == 401
										|| handshake.getResponse().statusCode() == 403)) {
							try {
								auth.refresh();
								lastError = "session refreshed after stream authorization expiry";
							} catch (IllegalStateException | RestClientException refreshError) {
								lastError = "session refresh failed: " + refreshError.getClass().getSimpleName();
								logger.error("Angel One session refresh failed after market stream authorization expiry",
										refreshError);
							}
						} else {
							lastError = "connection failed: " + cause.getClass().getSimpleName();
							logger.warn("Angel One market stream connection failed: {}", lastError);
						}
						scheduleReconnect();
					} else socket = connectedSocket;
				});
	}

	private String authClientCode() {
		return auth.clientCodeForFeed();
	}

	private void scheduleReconnect() {
		if (stopping.get()) return;
		int attempt = reconnectAttempts.incrementAndGet();
		long delaySeconds = Math.min(60, 1L << Math.min(attempt, 6));
		CompletableFuture.delayedExecutor(delaySeconds, java.util.concurrent.TimeUnit.SECONDS)
				.execute(this::connectAsync);
	}

	private void subscribeInitialInstruments() {
		List<Instrument> available = instruments.all();
		byToken.clear();
		available.forEach(instrument -> byToken.put(instrument.token(), instrument));
		for (String underlying : strategyProperties.getMarkets()) {
			List<Instrument> matching = available.stream()
					.filter(instrument -> instrument.underlying().equalsIgnoreCase(underlying)).toList();
			matching.stream().filter(instrument -> instrument.instrumentType().equals("AMXIDX"))
					.findFirst().ifPresent(this::subscribe);
			try {
				subscribe(instruments.nearestMonthlyFuture(underlying, LocalDate.now()));
			} catch (IllegalStateException ignored) {
				// The instrument master may not list a current or next-month future for every configured market.
			}
		}
	}

	private void subscribeOptions(String underlying, java.math.BigDecimal spot) {
		LocalDate today = LocalDate.now();
		List<Instrument> options = instruments.find(underlying).stream()
				.filter(instrument -> instrument.optionType() != null && instrument.expiry() != null
						&& !instrument.expiry().isBefore(today))
				.toList();
		LocalDate expiry = options.stream().map(Instrument::expiry).min(LocalDate::compareTo).orElse(null);
		if (expiry == null) return;
		List<java.math.BigDecimal> strikes = options.stream().filter(item -> item.expiry().equals(expiry))
				.map(Instrument::strike).distinct().sorted().toList();
		if (strikes.isEmpty()) return;
		int atm = java.util.stream.IntStream.range(0, strikes.size()).boxed()
				.min(Comparator.comparing(index -> strikes.get(index).subtract(spot).abs())).orElseThrow();
		int range = strategyProperties.getPcr().getStrikeRange();
		if (atm < range || atm + range >= strikes.size()) return;
		Set<java.math.BigDecimal> selected = Set.copyOf(strikes.subList(atm - range, atm + range + 1));
		List<Instrument> selection = options.stream()
				.filter(item -> item.expiry().equals(expiry) && selected.contains(item.strike())).toList();
		subscribeAll(selection);
	}

	private void subscribe(Instrument instrument) {
		subscribeAll(List.of(instrument));
	}

	private void subscribeAll(List<Instrument> candidates) {
		WebSocket current = socket;
		if (current == null || current.isOutputClosed()) return;
		Map<Integer, List<String>> byExchange = new java.util.HashMap<>();
		for (Instrument instrument : candidates) {
			int exchangeType = instrument.exchange().equals("NFO") ? 2 : 1;
			if (subscribed.add(exchangeType + ":" + instrument.token())) {
				byExchange.computeIfAbsent(exchangeType, ignored -> new ArrayList<>()).add(instrument.token());
			}
		}
		byExchange.forEach((exchangeType, tokens) -> {
			for (int offset = 0; offset < tokens.size(); offset += 50) {
				List<String> batch = tokens.subList(offset, Math.min(offset + 50, tokens.size()));
				String jsonTokens = batch.stream().map(token -> "\"" + escape(token) + "\"")
						.collect(java.util.stream.Collectors.joining(","));
				String request = "{\"action\":1,\"params\":{\"mode\":" + SNAP_QUOTE_MODE
						+ ",\"tokenList\":[{\"exchangeType\":" + exchangeType + ",\"tokens\":["
						+ jsonTokens + "]}]}}";
				current.sendText(request, true).whenComplete((ignored, error) -> {
					if (error != null) {
						lastError = "subscription send failed: " + error.getClass().getSimpleName();
						logger.warn("SmartAPI subscription request failed: {}", lastError);
					}
				});
			}
		});
	}

	private void onPacket(byte[] bytes) {
		if (bytes.length < 51) {
			malformedPackets.incrementAndGet();
			lastError = "market packet shorter than the minimum protocol header";
			return;
		}
		ByteBuffer packet = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		int mode = Byte.toUnsignedInt(packet.get(0));
		packet.get(1);
		String token = readToken(packet);
		Instrument instrument = byToken.get(token);
		if (instrument == null) return;
		long millis = bytes.length >= 43 ? packet.getLong(35) : 0;
		Instant timestamp = millis > 0 ? Instant.ofEpochMilli(millis) : Instant.now();
		long rawPrice = packet.getLong(43);
		java.math.BigDecimal price = java.math.BigDecimal.valueOf(rawPrice, 2);
		if (price.signum() <= 0) return;
		long sequence = packet.getLong(27);
		Long lastSequence = lastSequenceByToken.put(token, sequence);
		if (lastSequence != null && sequence <= lastSequence) return;
		if (mode != SNAP_QUOTE_MODE || bytes.length < 139) {
			malformedPackets.incrementAndGet();
			lastError = "unsupported or incomplete SmartAPI packet";
			return;
		}
		long volume = packet.getLong(67);
		long oi = packet.getLong(131);
		if (instrument.instrumentType().equals("AMXIDX")) {
			marketState.updateSpot(instrument.underlying(), price, timestamp);
			subscribeOptions(instrument.underlying(), price);
		} else if (instrument.instrumentType().equals("FUTIDX")) {
			if (volume < 0) {
				malformedPackets.incrementAndGet();
				lastError = "negative futures volume in SmartAPI packet";
				return;
			}
			marketState.updateFuturesPrice(instrument.underlying(), price, timestamp);
			candles.accept(instrument, price, volume, timestamp);
		} else if (instrument.optionType() != null) {
			java.math.BigDecimal bid = bytes.length >= 167 ? java.math.BigDecimal.valueOf(packet.getLong(157), 2) : null;
			java.math.BigDecimal ask = bytes.length >= 267 ? java.math.BigDecimal.valueOf(packet.getLong(257), 2) : null;
			marketState.updateOption(new OptionContract(instrument.underlying(), token,
					instrument.tradingSymbol(), instrument.expiry(), instrument.strike(), instrument.optionType(),
					oi < 0 ? null : oi, volume < 0 ? null : volume, price, bid, ask, timestamp));
		}
	}

	private String readToken(ByteBuffer packet) {
		byte[] tokenBytes = new byte[25];
		packet.position(2);
		packet.get(tokenBytes);
		int end = 0;
		while (end < tokenBytes.length && tokenBytes[end] != 0) end++;
		return new String(tokenBytes, 0, end, StandardCharsets.UTF_8).trim();
	}

	private String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private final class StreamListener implements WebSocket.Listener {
		private final ByteArrayOutputStream fragments = new ByteArrayOutputStream();

		@Override
		public void onOpen(WebSocket webSocket) {
			socket = webSocket;
			reconnectAttempts.set(0);
			lastError = "none";
			subscribed.clear();
			lastSequenceByToken.clear();
			subscribeInitialInstruments();
			webSocket.request(1);
		}

		@Override
		public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
			byte[] chunk = new byte[data.remaining()];
			data.get(chunk);
			synchronized (fragments) {
				fragments.writeBytes(chunk);
				if (last) {
					try {
						onPacket(fragments.toByteArray());
					} catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
						malformedPackets.incrementAndGet();
						lastError = "invalid SmartAPI packet: " + exception.getClass().getSimpleName();
						logger.warn("Ignoring malformed SmartAPI binary packet: {}", lastError);
					}
					fragments.reset();
				}
			}
			webSocket.request(1);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
			webSocket.request(1);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			socket = null;
			subscribed.clear();
			lastSequenceByToken.clear();
			lastError = "market stream closed with status " + statusCode;
			scheduleReconnect();
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			socket = null;
			subscribed.clear();
			lastSequenceByToken.clear();
			lastError = "market stream error: " + error.getClass().getSimpleName();
			logger.warn("Angel One market stream reported {}", lastError);
			scheduleReconnect();
		}
	}
}
