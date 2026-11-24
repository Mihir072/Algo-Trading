package com.example.Algo_Trading_Backend.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "strategy")
public class StrategyProperties {
	private boolean enabled = true;
	private List<String> markets = new ArrayList<>(List.of("NIFTY", "BANKNIFTY"));
	private String timeframe = "5m";
	private long cooldownSeconds = 300;
	private final Pcr pcr = new Pcr();
	private final Vwap vwap = new Vwap();
	private final Atr atr = new Atr();
	private final Risk risk = new Risk();
	private final PaperTrading paperTrading = new PaperTrading();
	private final Execution execution = new Execution();

	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public List<String> getMarkets() { return markets; }
	public void setMarkets(List<String> markets) { this.markets = markets; }
	public String getTimeframe() { return timeframe; }
	public void setTimeframe(String timeframe) { this.timeframe = timeframe; }
	public long getCooldownSeconds() { return cooldownSeconds; }
	public void setCooldownSeconds(long value) { cooldownSeconds = value; }
	public Pcr getPcr() { return pcr; }
	public Vwap getVwap() { return vwap; }
	public Atr getAtr() { return atr; }
	public Risk getRisk() { return risk; }
	public PaperTrading getPaperTrading() { return paperTrading; }
	public Execution getExecution() { return execution; }

	public static class Pcr {
		private int strikeRange = 8;
		private BigDecimal bullishThreshold = new BigDecimal("1.10");
		private BigDecimal bearishThreshold = new BigDecimal("0.90");
		private int trendWindow = 5;
		private BigDecimal trendTolerance = BigDecimal.ZERO;
		private long minimumChangeOiDenominator;
		public int getStrikeRange() { return strikeRange; }
		public void setStrikeRange(int value) { strikeRange = value; }
		public BigDecimal getBullishThreshold() { return bullishThreshold; }
		public void setBullishThreshold(BigDecimal value) { bullishThreshold = value; }
		public BigDecimal getBearishThreshold() { return bearishThreshold; }
		public void setBearishThreshold(BigDecimal value) { bearishThreshold = value; }
		public int getTrendWindow() { return trendWindow; }
		public void setTrendWindow(int value) { trendWindow = value; }
		public BigDecimal getTrendTolerance() { return trendTolerance; }
		public void setTrendTolerance(BigDecimal value) { trendTolerance = value; }
		public long getMinimumChangeOiDenominator() { return minimumChangeOiDenominator; }
		public void setMinimumChangeOiDenominator(long value) { minimumChangeOiDenominator = value; }
	}

	public static class Vwap {
		private BigDecimal maxDistanceAtr = new BigDecimal("0.5");
		public BigDecimal getMaxDistanceAtr() { return maxDistanceAtr; }
		public void setMaxDistanceAtr(BigDecimal value) { maxDistanceAtr = value; }
	}

	public static class Atr {
		private int period = 14;
		private BigDecimal multiplier = new BigDecimal("1.5");
		public int getPeriod() { return period; }
		public void setPeriod(int value) { period = value; }
		public BigDecimal getMultiplier() { return multiplier; }
		public void setMultiplier(BigDecimal value) { multiplier = value; }
	}

	public static class Risk {
		private BigDecimal minimumRewardRisk = new BigDecimal("1.5");
		private BigDecimal maximumRewardRisk = new BigDecimal("2.0");
		private BigDecimal oiBuffer = BigDecimal.ZERO;
		public BigDecimal getMinimumRewardRisk() { return minimumRewardRisk; }
		public void setMinimumRewardRisk(BigDecimal value) { minimumRewardRisk = value; }
		public BigDecimal getMaximumRewardRisk() { return maximumRewardRisk; }
		public void setMaximumRewardRisk(BigDecimal value) { maximumRewardRisk = value; }
		public BigDecimal getOiBuffer() { return oiBuffer; }
		public void setOiBuffer(BigDecimal value) { oiBuffer = value; }
	}

	public static class PaperTrading {
		private boolean enabled = true;
		private int maxOpenTradesPerUnderlying = 1;
		private int maxTradesPerSession = 10;
		private BigDecimal dailyLossLimit = BigDecimal.ZERO;
		private BigDecimal brokeragePerOrder = BigDecimal.ZERO;
		private BigDecimal slippagePerUnit = BigDecimal.ZERO;
		public boolean isEnabled() { return enabled; }
		public void setEnabled(boolean value) { enabled = value; }
		public int getMaxOpenTradesPerUnderlying() { return maxOpenTradesPerUnderlying; }
		public void setMaxOpenTradesPerUnderlying(int value) { maxOpenTradesPerUnderlying = value; }
		public int getMaxTradesPerSession() { return maxTradesPerSession; }
		public void setMaxTradesPerSession(int value) { maxTradesPerSession = value; }
		public BigDecimal getDailyLossLimit() { return dailyLossLimit; }
		public void setDailyLossLimit(BigDecimal value) { dailyLossLimit = value; }
		public BigDecimal getBrokeragePerOrder() { return brokeragePerOrder; }
		public void setBrokeragePerOrder(BigDecimal value) { brokeragePerOrder = value; }
		public BigDecimal getSlippagePerUnit() { return slippagePerUnit; }
		public void setSlippagePerUnit(BigDecimal value) { slippagePerUnit = value; }
	}

	public static class Execution {
		private boolean realOrdersEnabled = false;
		public boolean isRealOrdersEnabled() { return false; }
		public void setRealOrdersEnabled(boolean value) { realOrdersEnabled = false; }
	}
}
