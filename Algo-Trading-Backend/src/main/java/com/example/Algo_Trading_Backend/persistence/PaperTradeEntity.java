package com.example.Algo_Trading_Backend.persistence;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "paper_trade")
public class PaperTradeEntity {
	public enum Status { OPEN, CLOSED }
	public enum ExitReason { STOP_LOSS, TARGET, MANUAL }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.EAGER, optional = false)
	@JoinColumn(name = "signal_id", nullable = false)
	private StrategySignalEntity signal;
	@Column(nullable = false, length = 32)
	private String underlying;
	@Column(nullable = false, length = 8)
	private String direction;
	@Column(name = "entry_price", nullable = false)
	private BigDecimal entryPrice;
	@Column(name = "exit_price")
	private BigDecimal exitPrice;
	@Column(name = "stop_loss", nullable = false)
	private BigDecimal stopLoss;
	@Column(nullable = false)
	private BigDecimal target;
	@Column(nullable = false)
	private int quantity;
	@Column(name = "gross_pnl")
	private BigDecimal grossPnl;
	@Column(name = "estimated_charges")
	private BigDecimal estimatedCharges;
	private BigDecimal slippage;
	@Column(name = "net_pnl")
	private BigDecimal netPnl;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private Status status;
	@Column(name = "exit_reason", length = 32)
	private String exitReason;
	@Column(name = "entry_time", nullable = false)
	private Instant entryTime;
	@Column(name = "exit_time")
	private Instant exitTime;

	protected PaperTradeEntity() {}

	public PaperTradeEntity(StrategySignalEntity signal, int quantity, BigDecimal entryPrice,
			BigDecimal slippage, Instant entryTime) {
		this.signal = signal;
		this.underlying = signal.getUnderlying();
		this.direction = signal.getSignal().name();
		this.entryPrice = entryPrice;
		this.stopLoss = signal.getStopLoss();
		this.target = signal.getTarget();
		this.quantity = quantity;
		this.slippage = slippage;
		this.status = Status.OPEN;
		this.entryTime = entryTime;
	}

	public void close(BigDecimal exitPrice, BigDecimal charges, BigDecimal exitSlippage,
			ExitReason reason, Instant time) {
		if (status != Status.OPEN) throw new IllegalStateException("Paper trade is already closed");
		this.exitPrice = exitPrice;
		this.estimatedCharges = charges;
		this.slippage = slippage.add(exitSlippage);
		BigDecimal points = direction.equals("LONG") ? exitPrice.subtract(entryPrice) : entryPrice.subtract(exitPrice);
		this.grossPnl = points.multiply(BigDecimal.valueOf(quantity));
		this.netPnl = grossPnl.subtract(charges);
		this.exitReason = reason.name();
		this.exitTime = time;
		this.status = Status.CLOSED;
	}

	public Long getId() { return id; }
	public String getUnderlying() { return underlying; }
	public String getDirection() { return direction; }
	public BigDecimal getEntryPrice() { return entryPrice; }
	public BigDecimal getExitPrice() { return exitPrice; }
	public BigDecimal getStopLoss() { return stopLoss; }
	public BigDecimal getTarget() { return target; }
	public int getQuantity() { return quantity; }
	public BigDecimal getGrossPnl() { return grossPnl; }
	public BigDecimal getEstimatedCharges() { return estimatedCharges; }
	public BigDecimal getSlippage() { return slippage; }
	public BigDecimal getNetPnl() { return netPnl; }
	public Status getStatus() { return status; }
	public String getExitReason() { return exitReason; }
	public Instant getEntryTime() { return entryTime; }
	public Instant getExitTime() { return exitTime; }
	public StrategySignalEntity getSignal() { return signal; }
}
