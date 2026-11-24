package com.example.Algo_Trading_Backend.persistence;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.Algo_Trading_Backend.market.FuturesCandle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "futures_candle")
public class FuturesCandleEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "instrument_token", nullable = false, length = 64)
	private String instrumentToken;
	@Column(nullable = false)
	private BigDecimal open;
	@Column(nullable = false)
	private BigDecimal high;
	@Column(nullable = false)
	private BigDecimal low;
	@Column(nullable = false)
	private BigDecimal close;
	@Column(nullable = false)
	private long volume;
	@Column(name = "open_interest")
	private Long openInterest;
	@Column(nullable = false)
	private Instant timestamp;

	protected FuturesCandleEntity() {}

	public FuturesCandleEntity(FuturesCandle candle) {
		instrumentToken = candle.token();
		open = candle.open();
		high = candle.high();
		low = candle.low();
		close = candle.close();
		volume = candle.volume();
		timestamp = candle.timestamp();
	}

	public Long getId() { return id; }
	public String getInstrumentToken() { return instrumentToken; }
	public BigDecimal getOpen() { return open; }
	public BigDecimal getHigh() { return high; }
	public BigDecimal getLow() { return low; }
	public BigDecimal getClose() { return close; }
	public long getVolume() { return volume; }
	public Long getOpenInterest() { return openInterest; }
	public Instant getTimestamp() { return timestamp; }
}
