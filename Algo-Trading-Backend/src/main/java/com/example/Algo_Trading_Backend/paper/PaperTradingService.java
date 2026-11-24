package com.example.Algo_Trading_Backend.paper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Algo_Trading_Backend.config.StrategyProperties;
import com.example.Algo_Trading_Backend.persistence.PaperTradeEntity;
import com.example.Algo_Trading_Backend.persistence.PaperTradeRepository;
import com.example.Algo_Trading_Backend.persistence.StrategySignalEntity;
import com.example.Algo_Trading_Backend.persistence.StrategySignalRepository;
import com.example.Algo_Trading_Backend.market.MarketSessionService;
import com.example.Algo_Trading_Backend.strategy.SignalDirection;
import com.example.Algo_Trading_Backend.strategy.StrategySignal;

@Service
public class PaperTradingService {
	private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
	private final StrategyProperties properties;
	private final PaperTradeRepository trades;
	private final StrategySignalRepository signals;
	private final MarketSessionService marketSession;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final ConcurrentHashMap<String, Instant> lastEntryByUnderlying = new ConcurrentHashMap<>();

	public record Performance(long totalTrades, long winningTrades, long losingTrades,
			BigDecimal winRatePercent, BigDecimal grossPnl, BigDecimal netPnl,
			BigDecimal profitFactor, BigDecimal averageRewardRisk, BigDecimal maximumDrawdown,
			int maximumConsecutiveLosses) {}

	public PaperTradingService(StrategyProperties properties, PaperTradeRepository trades,
			StrategySignalRepository signals, MarketSessionService marketSession) {
		this.properties = properties;
		this.trades = trades;
		this.signals = signals;
		this.marketSession = marketSession;
	}

	public boolean isRunning() {
		return running.get() && properties.getPaperTrading().isEnabled();
	}

	public boolean start() {
		if (!properties.getPaperTrading().isEnabled()) {
			throw new IllegalStateException("Paper trading is disabled by configuration");
		}
		running.set(true);
		return true;
	}

	public boolean stop() {
		running.set(false);
		return false;
	}

	@Transactional
	public synchronized PaperTradeEntity acceptSignal(StrategySignal signal, int quantity) {
		StrategySignalEntity savedSignal = signals.save(new StrategySignalEntity(signal));
		return openTrade(savedSignal, quantity);
	}

	@Transactional
	public synchronized PaperTradeEntity openTrade(long signalId, int quantity) {
		StrategySignalEntity signal = signals.findById(signalId)
				.orElseThrow(() -> new IllegalArgumentException("Strategy signal not found"));
		return openTrade(signal, quantity);
	}

	private PaperTradeEntity openTrade(StrategySignalEntity savedSignal, int quantity) {
		if (savedSignal.getSignal() == SignalDirection.NO_TRADE) {
			throw new IllegalArgumentException("Only a valid LONG or SHORT signal can open a paper trade");
		}
		if (savedSignal.getEntry() == null || savedSignal.getStopLoss() == null || savedSignal.getTarget() == null) {
			throw new IllegalArgumentException("Strategy signal has no valid entry, stop loss, or target");
		}
		if (!isRunning()) throw new IllegalStateException("Paper trading is not running");
		Instant now = Instant.now();
		if (!marketSession.isOpen(now)) throw new IllegalStateException("Paper trading entries are only allowed during the configured market session");
		if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
		String underlying = savedSignal.getUnderlying().toUpperCase();
		if (trades.countByUnderlyingAndStatus(underlying, PaperTradeEntity.Status.OPEN)
				>= properties.getPaperTrading().getMaxOpenTradesPerUnderlying()) {
			throw new IllegalStateException("Maximum open paper trades reached for " + underlying);
		}
		Instant dayStart = LocalDate.now(MARKET_ZONE).atStartOfDay(MARKET_ZONE).toInstant();
		if (trades.countByEntryTimeAfter(dayStart) >= properties.getPaperTrading().getMaxTradesPerSession()) {
			throw new IllegalStateException("Maximum paper trades for the market session reached");
		}
		Instant lastEntry = lastEntryByUnderlying.get(underlying);
		if (lastEntry == null) {
			PaperTradeEntity previousTrade = trades.findFirstByUnderlyingOrderByEntryTimeDesc(underlying);
			if (previousTrade != null) lastEntry = previousTrade.getEntryTime();
		}
		if (trades.existsBySignal_Id(savedSignal.getId())
				|| (lastEntry != null && now.isBefore(lastEntry.plusSeconds(properties.getCooldownSeconds())))) {
			throw new IllegalStateException("Duplicate signal blocked by the configured cooldown");
		}
		if (dailyNetPnl(dayStart).compareTo(properties.getPaperTrading().getDailyLossLimit().negate()) <= 0
				&& properties.getPaperTrading().getDailyLossLimit().signum() > 0) {
			throw new IllegalStateException("Daily paper-trading loss limit reached");
		}
		BigDecimal slippagePerUnit = properties.getPaperTrading().getSlippagePerUnit();
		BigDecimal totalEntrySlippage = slippagePerUnit.multiply(BigDecimal.valueOf(quantity));
		BigDecimal executionEntry = savedSignal.getEntry().add(
				savedSignal.getSignal() == SignalDirection.LONG ? slippagePerUnit : slippagePerUnit.negate());
		PaperTradeEntity trade = trades.save(new PaperTradeEntity(savedSignal, quantity, executionEntry,
				totalEntrySlippage, now));
		lastEntryByUnderlying.put(underlying, now);
		return trade;
	}

	@Transactional
	public synchronized List<PaperTradeEntity> updatePrice(String underlying, BigDecimal futuresPrice) {
		if (futuresPrice == null || futuresPrice.signum() <= 0) {
			throw new IllegalArgumentException("Futures price must be positive");
		}
		List<PaperTradeEntity> closed = new java.util.ArrayList<>();
		for (PaperTradeEntity trade : trades.findByStatusAndUnderlyingOrderByEntryTimeAsc(
				PaperTradeEntity.Status.OPEN, underlying.toUpperCase())) {
			PaperTradeEntity.ExitReason reason = exitReason(trade, futuresPrice);
			if (reason != null) {
				closed.add(close(trade, adverseExitPrice(trade, futuresPrice), reason));
			}
		}
		return List.copyOf(closed);
	}

	@Transactional
	public synchronized PaperTradeEntity closeManually(long id, BigDecimal futuresPrice) {
		PaperTradeEntity trade = trades.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Paper trade not found"));
		if (trade.getStatus() != PaperTradeEntity.Status.OPEN) {
			throw new IllegalStateException("Paper trade is already closed");
		}
		if (futuresPrice == null || futuresPrice.signum() <= 0) {
			throw new IllegalArgumentException("Futures price must be positive");
		}
		return close(trade, adverseExitPrice(trade, futuresPrice), PaperTradeEntity.ExitReason.MANUAL);
	}

	public List<PaperTradeEntity> positions() {
		return trades.findByStatusOrderByEntryTimeDesc(PaperTradeEntity.Status.OPEN);
	}

	public List<PaperTradeEntity> history() {
		return trades.findTop500ByOrderByEntryTimeDesc();
	}

	public Performance performance() {
		List<PaperTradeEntity> closed = trades.findAll().stream()
				.filter(trade -> trade.getStatus() == PaperTradeEntity.Status.CLOSED).toList();
		long wins = closed.stream().filter(trade -> trade.getNetPnl().signum() > 0).count();
		long losses = closed.stream().filter(trade -> trade.getNetPnl().signum() < 0).count();
		BigDecimal gross = closed.stream().map(PaperTradeEntity::getGrossPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal net = closed.stream().map(PaperTradeEntity::getNetPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal winRate = closed.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(wins * 100)
				.divide(BigDecimal.valueOf(closed.size()), 2, java.math.RoundingMode.HALF_UP);
		BigDecimal avgRisk = closed.stream().map(trade -> trade.getSignal().getRewardRisk())
				.filter(value -> value != null).reduce(BigDecimal.ZERO, BigDecimal::add);
		long rrCount = closed.stream().filter(trade -> trade.getSignal().getRewardRisk() != null).count();
		if (rrCount > 0) avgRisk = avgRisk.divide(BigDecimal.valueOf(rrCount), 4, java.math.RoundingMode.HALF_UP);
		BigDecimal profit = closed.stream().map(PaperTradeEntity::getNetPnl)
				.filter(value -> value.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal loss = closed.stream().map(PaperTradeEntity::getNetPnl)
				.filter(value -> value.signum() < 0).map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal profitFactor = loss.signum() == 0 ? null
				: profit.divide(loss, 4, java.math.RoundingMode.HALF_UP);
		return new Performance(closed.size(), wins, losses, winRate, gross, net, profitFactor, avgRisk,
				maximumDrawdown(closed), maximumConsecutiveLosses(closed));
	}

	private PaperTradeEntity close(PaperTradeEntity trade, BigDecimal executionPrice,
			PaperTradeEntity.ExitReason reason) {
		BigDecimal charges = properties.getPaperTrading().getBrokeragePerOrder().multiply(BigDecimal.valueOf(2));
		BigDecimal exitSlippage = properties.getPaperTrading().getSlippagePerUnit()
				.multiply(BigDecimal.valueOf(trade.getQuantity()));
		trade.close(executionPrice, charges, exitSlippage, reason, Instant.now());
		return trades.save(trade);
	}

	private PaperTradeEntity.ExitReason exitReason(PaperTradeEntity trade, BigDecimal price) {
		if (trade.getDirection().equals("LONG")) {
			if (price.compareTo(trade.getStopLoss()) <= 0) return PaperTradeEntity.ExitReason.STOP_LOSS;
			if (price.compareTo(trade.getTarget()) >= 0) return PaperTradeEntity.ExitReason.TARGET;
		} else {
			if (price.compareTo(trade.getStopLoss()) >= 0) return PaperTradeEntity.ExitReason.STOP_LOSS;
			if (price.compareTo(trade.getTarget()) <= 0) return PaperTradeEntity.ExitReason.TARGET;
		}
		return null;
	}

	private BigDecimal exitPrice(PaperTradeEntity trade, PaperTradeEntity.ExitReason reason) {
		return reason == PaperTradeEntity.ExitReason.STOP_LOSS ? trade.getStopLoss() : trade.getTarget();
	}

	private BigDecimal adverseExitPrice(PaperTradeEntity trade, BigDecimal marketPrice) {
		BigDecimal slippage = properties.getPaperTrading().getSlippagePerUnit();
		return trade.getDirection().equals("LONG") ? marketPrice.subtract(slippage) : marketPrice.add(slippage);
	}

	private BigDecimal dailyNetPnl(Instant dayStart) {
		return trades.findByStatusAndExitTimeAfter(PaperTradeEntity.Status.CLOSED, dayStart).stream()
				.map(PaperTradeEntity::getNetPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private BigDecimal maximumDrawdown(List<PaperTradeEntity> closed) {
		BigDecimal equity = BigDecimal.ZERO;
		BigDecimal peak = BigDecimal.ZERO;
		BigDecimal maxDrawdown = BigDecimal.ZERO;
		for (PaperTradeEntity trade : closed.stream()
				.sorted(java.util.Comparator.comparing(PaperTradeEntity::getExitTime)).toList()) {
			equity = equity.add(trade.getNetPnl());
			peak = peak.max(equity);
			maxDrawdown = maxDrawdown.max(peak.subtract(equity));
		}
		return maxDrawdown;
	}

	private int maximumConsecutiveLosses(List<PaperTradeEntity> closed) {
		int current = 0;
		int maximum = 0;
		for (PaperTradeEntity trade : closed.stream()
				.sorted(java.util.Comparator.comparing(PaperTradeEntity::getExitTime)).toList()) {
			current = trade.getNetPnl().signum() < 0 ? current + 1 : 0;
			maximum = Math.max(maximum, current);
		}
		return maximum;
	}
}
