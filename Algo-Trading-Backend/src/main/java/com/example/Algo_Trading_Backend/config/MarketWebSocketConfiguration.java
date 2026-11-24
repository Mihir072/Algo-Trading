package com.example.Algo_Trading_Backend.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.example.Algo_Trading_Backend.api.MarketDataWebSocketHandler;

@Configuration
@EnableWebSocket
public class MarketWebSocketConfiguration implements WebSocketConfigurer {
	private final MarketDataWebSocketHandler handler;
	private final String[] allowedOrigins;

	public MarketWebSocketConfiguration(MarketDataWebSocketHandler handler,
			@Value("${app.cors.allowed-origins}") String origins) {
		this.handler = handler;
		this.allowedOrigins = Arrays.stream(origins.split(","))
				.map(String::trim)
				.filter(origin -> !origin.isEmpty())
				.toArray(String[]::new);
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(handler, "/ws/market-data").setAllowedOriginPatterns(allowedOrigins);
	}
}
