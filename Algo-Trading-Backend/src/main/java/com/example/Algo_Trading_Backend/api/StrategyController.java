package com.example.Algo_Trading_Backend.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.Algo_Trading_Backend.config.StrategyProperties;
import com.example.Algo_Trading_Backend.analytics.MarketAnalyticsService;
import com.example.Algo_Trading_Backend.broker.InstrumentMasterService;
import com.example.Algo_Trading_Backend.paper.PaperTradingService;
import com.example.Algo_Trading_Backend.persistence.PaperTradeEntity;
import com.example.Algo_Trading_Backend.persistence.StrategySignalEntity;
import com.example.Algo_Trading_Backend.persistence.StrategySignalRepository;
import com.example.Algo_Trading_Backend.strategy.SignalGenerator;
import com.example.Algo_Trading_Backend.strategy.StrategyInput;
import com.example.Algo_Trading_Backend.strategy.StrategySignal;

@RestController
@RequestMapping("/api/v1")
public class StrategyController {
	private final StrategyProperties properties;
	private final SignalGenerator signalGenerator;
	private final StrategySignalRepository signalRepository;
	private final PaperTradingService paperTrading;
	private final MarketStateStore marketState;
	private final MarketAnalyticsService analytics;
	private final InstrumentMasterService instruments;

	public StrategyController(StrategyProperties properties, SignalGenerator signalGenerator,
			StrategySignalRepository signalRepository, PaperTradingService paperTrading,
			MarketStateStore marketState, MarketAnalyticsService analytics, InstrumentMasterService instruments) {
		this.properties = properties;
		this.signalGenerator = signalGenerator;
		this.signalRepository = signalRepository;
		this.paperTrading = paperTrading;
		this.marketState = marketState;
		this.analytics = analytics;
		this.instruments = instruments;
	}

	public record PaperTradeRequest(int quantity) {}
	public record ManualCloseRequest(java.math.BigDecimal futuresPrice) {}
	public record StrategyStatus(boolean enabled, boolean paperTradingEnabled, boolean paperTradingRunning,
			boolean realOrdersEnabled, List<String> configuredMarkets, int instrumentsLoaded,
			Map<String, MarketStateStore.MarketState> latestMarketState, Instant checkedAt) {}

	@GetMapping("/strategy/config")
	public StrategyProperties configuration() {
		return properties;
	}

	@PutMapping("/strategy/config")
	public StrategyProperties updateConfiguration(@RequestBody StrategyConfigUpdate update) {
		if (update == null) throw new IllegalArgumentException("Configuration update is required");
		var pcr = properties.getPcr();
		var risk = properties.getRisk();
		var paper = properties.getPaperTrading();
		int strikeRange = valueOr(update.strikeRange(), pcr.getStrikeRange());
		java.math.BigDecimal bullish = valueOr(update.bullishThreshold(), pcr.getBullishThreshold());
		java.math.BigDecimal bearish = valueOr(update.bearishThreshold(), pcr.getBearishThreshold());
		int trendWindow = valueOr(update.trendWindow(), pcr.getTrendWindow());
		java.math.BigDecimal trendTolerance = valueOr(update.trendTolerance(), pcr.getTrendTolerance());
		java.math.BigDecimal maxDistanceAtr = valueOr(update.maxDistanceAtr(), properties.getVwap().getMaxDistanceAtr());
		int atrPeriod = valueOr(update.atrPeriod(), properties.getAtr().getPeriod());
		java.math.BigDecimal atrMultiplier = valueOr(update.atrMultiplier(), properties.getAtr().getMultiplier());
		java.math.BigDecimal minimumRewardRisk = valueOr(update.minimumRewardRisk(), risk.getMinimumRewardRisk());
		java.math.BigDecimal maximumRewardRisk = valueOr(update.maximumRewardRisk(), risk.getMaximumRewardRisk());
		java.math.BigDecimal oiBuffer = valueOr(update.oiBuffer(), risk.getOiBuffer());
		long minOiDenominator = valueOr(update.minimumChangeOiDenominator(), pcr.getMinimumChangeOiDenominator());
		long cooldown = valueOr(update.cooldownSeconds(), properties.getCooldownSeconds());
		int maxOpen = valueOr(update.maxOpenTradesPerUnderlying(), paper.getMaxOpenTradesPerUnderlying());
		int maxSessionTrades = valueOr(update.maxTradesPerSession(), paper.getMaxTradesPerSession());
		java.math.BigDecimal dailyLoss = valueOr(update.dailyLossLimit(), paper.getDailyLossLimit());
		java.math.BigDecimal brokerage = valueOr(update.brokeragePerOrder(), paper.getBrokeragePerOrder());
		java.math.BigDecimal slippage = valueOr(update.slippagePerUnit(), paper.getSlippagePerUnit());

		if (strikeRange < 1 || strikeRange > 100) throw new IllegalArgumentException("strikeRange must be between 1 and 100");
		if (bullish.signum() <= 0 || bearish.signum() < 0 || bearish.compareTo(bullish) >= 0) {
			throw new IllegalArgumentException("bearishThreshold must be less than bullishThreshold");
		}
		if (trendWindow < 2 || trendTolerance.signum() < 0) throw new IllegalArgumentException("Trend window must be >= 2 and tolerance must be non-negative");
		if (maxDistanceAtr.signum() <= 0) throw new IllegalArgumentException("maxDistanceAtr must be positive");
		if (atrPeriod < 1 || atrMultiplier.signum() <= 0) throw new IllegalArgumentException("ATR period and multiplier must be positive");
		if (minimumRewardRisk.signum() <= 0
				|| maximumRewardRisk.compareTo(minimumRewardRisk) < 0) {
			throw new IllegalArgumentException("Reward-to-risk limits must be positive and maximum must be >= minimum");
		}
		if (oiBuffer.signum() < 0 || minOiDenominator < 0 || cooldown < 0
				|| maxOpen < 1 || maxSessionTrades < 1
				|| dailyLoss.signum() < 0 || brokerage.signum() < 0 || slippage.signum() < 0) {
			throw new IllegalArgumentException("Risk, paper-trading limits, and cost values must be non-negative and trade limits positive");
		}

		pcr.setStrikeRange(strikeRange);
		pcr.setBullishThreshold(bullish);
		pcr.setBearishThreshold(bearish);
		pcr.setTrendWindow(trendWindow);
		pcr.setTrendTolerance(trendTolerance);
		pcr.setMinimumChangeOiDenominator(minOiDenominator);
		properties.getVwap().setMaxDistanceAtr(maxDistanceAtr);
		properties.getAtr().setPeriod(atrPeriod);
		properties.getAtr().setMultiplier(atrMultiplier);
		risk.setMinimumRewardRisk(minimumRewardRisk);
		risk.setMaximumRewardRisk(maximumRewardRisk);
		risk.setOiBuffer(oiBuffer);
		properties.setCooldownSeconds(cooldown);
		paper.setMaxOpenTradesPerUnderlying(maxOpen);
		paper.setMaxTradesPerSession(maxSessionTrades);
		paper.setDailyLossLimit(dailyLoss);
		paper.setBrokeragePerOrder(brokerage);
		paper.setSlippagePerUnit(slippage);
		if (update.paperTradingEnabled() != null) {
			paper.setEnabled(update.paperTradingEnabled());
			if (!update.paperTradingEnabled()) paperTrading.stop();
		}
		if (update.enabled() != null) properties.setEnabled(update.enabled());
		return properties;
	}

	private <T> T valueOr(T value, T fallback) {
		return value == null ? fallback : value;
	}

	@PostMapping("/strategy/evaluate")
	public StrategySignal evaluate(@RequestBody StrategyInput input) {
		if (input.underlying() == null || !properties.getMarkets().contains(input.underlying().toUpperCase())) {
			throw new IllegalArgumentException("Underlying is not in the configured markets list");
		}
		StrategySignal signal = signalGenerator.generate(input);
		signalRepository.save(new StrategySignalEntity(signal));
		marketState.updateSignal(signal.underlying(), signal);
		return signal;
	}

	@PostMapping("/strategy/evaluate/{symbol}")
	public StrategySignal evaluateLatest(@PathVariable String symbol) {
		StrategyInput input = analytics.strategyInput(symbol);
		StrategySignal signal = signalGenerator.generate(input);
		signalRepository.save(new StrategySignalEntity(signal));
		marketState.updateSignal(symbol, signal);
		return signal;
	}

	@GetMapping("/strategy/signal/{symbol}")
	public StrategySignalEntity latestSignal(@PathVariable String symbol) {
		return signalRepository.findByUnderlyingAndCapturedAtAfterOrderByCapturedAtDesc(symbol.toUpperCase(),
				Instant.EPOCH).stream().findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No signal history exists for " + symbol));
	}

	@GetMapping("/strategy/history")
	public List<StrategySignalEntity> history(@RequestParam(required = false) String symbol) {
		return symbol == null ? signalRepository.findTop100ByOrderByCapturedAtDesc()
				: signalRepository.findByUnderlyingAndCapturedAtAfterOrderByCapturedAtDesc(symbol.toUpperCase(), Instant.EPOCH);
	}

	@GetMapping("/strategy/status")
	public StrategyStatus status() {
		return new StrategyStatus(properties.isEnabled(), properties.getPaperTrading().isEnabled(),
				paperTrading.isRunning(), false, List.copyOf(properties.getMarkets()), instruments.all().size(),
				marketState.all(), Instant.now());
	}

	@PostMapping("/paper-trading/start")
	public Map<String, Boolean> startPaperTrading() {
		return Map.of("running", paperTrading.start(), "realOrdersEnabled", false);
	}

	@PostMapping("/paper-trading/stop")
	public Map<String, Boolean> stopPaperTrading() {
		return Map.of("running", paperTrading.stop(), "realOrdersEnabled", false);
	}

	@GetMapping("/paper-trading/positions")
	public List<PaperTradeEntity> positions() {
		return paperTrading.positions();
	}

	@GetMapping("/paper-trading/trades")
	public List<PaperTradeEntity> trades() {
		return paperTrading.history();
	}

	@GetMapping("/paper-trading/performance")
	public PaperTradingService.Performance performance() {
		return paperTrading.performance();
	}

	@PostMapping("/paper-trading/signals/{signalId}")
	public PaperTradeEntity openPaperTrade(@PathVariable long signalId, @RequestBody PaperTradeRequest request) {
		return paperTrading.openTrade(signalId, request.quantity());
	}

	@PostMapping("/paper-trading/positions/{symbol}/mark")
	public List<PaperTradeEntity> markToMarket(@PathVariable String symbol, @RequestParam java.math.BigDecimal price) {
		return paperTrading.updatePrice(symbol, price);
	}

	@PostMapping("/paper-trading/trades/{tradeId}/close")
	public PaperTradeEntity closePaperTrade(@PathVariable long tradeId, @RequestBody ManualCloseRequest request) {
		return paperTrading.closeManually(tradeId, request.futuresPrice());
	}
}
