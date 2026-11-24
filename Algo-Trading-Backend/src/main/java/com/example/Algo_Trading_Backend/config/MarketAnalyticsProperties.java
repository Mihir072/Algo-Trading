package com.example.Algo_Trading_Backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market-analytics")
public class MarketAnalyticsProperties {
	private boolean enabled;
	private long pollIntervalMillis = 30000;

	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean value) { enabled = value; }
	public long getPollIntervalMillis() { return pollIntervalMillis; }
	public void setPollIntervalMillis(long value) { pollIntervalMillis = value; }
}
