package com.example.Algo_Trading_Backend.api;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.json.JsonMapper;

@Component
public class MarketDataWebSocketHandler extends TextWebSocketHandler {
	private static final Logger logger = LoggerFactory.getLogger(MarketDataWebSocketHandler.class);
	private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws IOException {
		sessions.add(session);
		session.sendMessage(new TextMessage("{\"type\":\"connected\",\"ordersEnabled\":false}"));
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		sessions.remove(session);
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
		sessions.remove(session);
		if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
		logger.warn("Frontend market WebSocket transport failed", exception);
	}

	public void publish(MarketStateStore.MarketState state) {
		final String message;
		try {
			message = jsonMapper.writeValueAsString(state);
		} catch (tools.jackson.core.JacksonException exception) {
			logger.error("Could not serialize market state for WebSocket publication", exception);
			return;
		}
		for (WebSocketSession session : sessions) {
			if (!session.isOpen()) {
				sessions.remove(session);
				continue;
			}
			try {
				synchronized (session) {
					session.sendMessage(new TextMessage(message));
				}
			} catch (IOException exception) {
				sessions.remove(session);
				logger.warn("Could not publish market state to a frontend WebSocket", exception);
			}
		}
	}
}
