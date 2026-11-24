package com.example.Algo_Trading_Backend.persistence;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.Algo_Trading_Backend.strategy.StrategySignal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "strategy_signal")
public class StrategySignalEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false, length = 32)
	private String underlying;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private com.example.Algo_Trading_Backend.strategy.SignalDirection signal;
	private BigDecimal entry;
	private BigDecimal vwap;
	private BigDecimal atr;
	@Column(name = "pcr_oi")
	private BigDecimal pcrOi;
	@Column(name = "pcr_change_oi")
	private BigDecimal pcrChangeOi;
	@Column(name = "pcr_trend", nullable = false, length = 16)
	private String pcrTrend;
	private BigDecimal support;
	private BigDecimal resistance;
	@Column(name = "stop_loss")
	private BigDecimal stopLoss;
	private BigDecimal target;
	@Column(name = "reward_risk")
	private BigDecimal rewardRisk;
	@Column(nullable = false, columnDefinition = "text")
	private String reasons;
	@Column(name = "captured_at", nullable = false)
	private Instant capturedAt;

	protected StrategySignalEntity() {}

	public StrategySignalEntity(StrategySignal signal) {
		this.underlying = signal.underlying();
		this.signal = signal.direction();
		this.entry = signal.inputs().futuresPrice();
		this.vwap = signal.inputs().vwap();
		this.atr = signal.inputs().atr();
		this.pcrOi = signal.inputs().pcrOi();
		this.pcrChangeOi = signal.inputs().pcrChangeOi();
		this.pcrTrend = signal.inputs().pcrTrend() == null ? "UNKNOWN" : signal.inputs().pcrTrend().trend().name();
		this.support = signal.inputs().adjustedSupport();
		this.resistance = signal.inputs().adjustedResistance();
		this.reasons = String.join("\n", signal.reasons());
		this.capturedAt = signal.generatedAt();
		if (signal.riskPlan() != null) {
			this.stopLoss = signal.riskPlan().stopLoss();
			this.target = signal.riskPlan().target();
			this.rewardRisk = signal.riskPlan().rewardRisk();
		}
	}

	public Long getId() { return id; }
	public String getUnderlying() { return underlying; }
	public com.example.Algo_Trading_Backend.strategy.SignalDirection getSignal() { return signal; }
	public BigDecimal getEntry() { return entry; }
	public BigDecimal getVwap() { return vwap; }
	public BigDecimal getAtr() { return atr; }
	public BigDecimal getPcrOi() { return pcrOi; }
	public BigDecimal getPcrChangeOi() { return pcrChangeOi; }
	public String getPcrTrend() { return pcrTrend; }
	public BigDecimal getSupport() { return support; }
	public BigDecimal getResistance() { return resistance; }
	public BigDecimal getStopLoss() { return stopLoss; }
	public BigDecimal getTarget() { return target; }
	public BigDecimal getRewardRisk() { return rewardRisk; }
	public String getReasons() { return reasons; }
	public Instant getCapturedAt() { return capturedAt; }
}
