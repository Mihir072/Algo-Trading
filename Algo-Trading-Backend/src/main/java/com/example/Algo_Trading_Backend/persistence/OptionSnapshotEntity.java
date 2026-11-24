package com.example.Algo_Trading_Backend.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.Algo_Trading_Backend.market.DataStatus;
import com.example.Algo_Trading_Backend.market.OptionChainSnapshot;
import com.example.Algo_Trading_Backend.market.OptionContract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "option_snapshot")
public class OptionSnapshotEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false, length = 32)
	private String underlying;
	@Column(nullable = false)
	private LocalDate expiry;
	@Column(name = "atm_strike", nullable = false)
	private BigDecimal atmStrike;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "selected_strikes", nullable = false, columnDefinition = "jsonb")
	private List<BigDecimal> selectedStrikes;
	@Column(name = "total_ce_oi", nullable = false)
	private long totalCeOi;
	@Column(name = "total_pe_oi", nullable = false)
	private long totalPeOi;
	@Column(name = "ce_change_oi")
	private Long ceChangeOi;
	@Column(name = "pe_change_oi")
	private Long peChangeOi;
	@Column(name = "pcr_oi", precision = 18, scale = 8)
	private BigDecimal pcrOi;
	@Column(name = "pcr_change_oi", precision = 18, scale = 8)
	private BigDecimal pcrChangeOi;
	@Column(name = "support_strike")
	private BigDecimal supportStrike;
	@Column(name = "support_oi")
	private Long supportOi;
	@Column(name = "resistance_strike")
	private BigDecimal resistanceStrike;
	@Column(name = "resistance_oi")
	private Long resistanceOi;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "contract_snapshots", nullable = false, columnDefinition = "jsonb")
	private List<OptionContract> contracts;
	@Enumerated(EnumType.STRING)
	@Column(name = "data_status", nullable = false, length = 16)
	private DataStatus dataStatus;
	@Column(name = "captured_at", nullable = false)
	private Instant capturedAt;

	protected OptionSnapshotEntity() {}

	public OptionSnapshotEntity(OptionChainSnapshot snapshot) {
		underlying = snapshot.underlying();
		expiry = snapshot.expiry();
		atmStrike = snapshot.atmStrike();
		selectedStrikes = snapshot.selectedStrikes();
		totalCeOi = snapshot.totalCeOi();
		totalPeOi = snapshot.totalPeOi();
		ceChangeOi = snapshot.ceChangeOi();
		peChangeOi = snapshot.peChangeOi();
		pcrOi = snapshot.pcrOi();
		pcrChangeOi = snapshot.pcrChangeOi();
		supportStrike = snapshot.supportStrike();
		supportOi = snapshot.supportOi();
		resistanceStrike = snapshot.resistanceStrike();
		resistanceOi = snapshot.resistanceOi();
		contracts = snapshot.contracts();
		dataStatus = snapshot.pcrChangeStatus();
		capturedAt = snapshot.calculatedAt();
	}

	public Long getId() { return id; }
	public String getUnderlying() { return underlying; }
	public LocalDate getExpiry() { return expiry; }
	public BigDecimal getAtmStrike() { return atmStrike; }
	public List<BigDecimal> getSelectedStrikes() { return selectedStrikes; }
	public long getTotalCeOi() { return totalCeOi; }
	public long getTotalPeOi() { return totalPeOi; }
	public Long getCeChangeOi() { return ceChangeOi; }
	public Long getPeChangeOi() { return peChangeOi; }
	public BigDecimal getPcrOi() { return pcrOi; }
	public BigDecimal getPcrChangeOi() { return pcrChangeOi; }
	public BigDecimal getSupportStrike() { return supportStrike; }
	public Long getSupportOi() { return supportOi; }
	public BigDecimal getResistanceStrike() { return resistanceStrike; }
	public Long getResistanceOi() { return resistanceOi; }
	public List<OptionContract> getContracts() { return contracts; }
	public DataStatus getDataStatus() { return dataStatus; }
	public Instant getCapturedAt() { return capturedAt; }
}
