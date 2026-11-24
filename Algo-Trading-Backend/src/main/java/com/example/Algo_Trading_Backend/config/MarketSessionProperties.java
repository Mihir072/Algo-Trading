package com.example.Algo_Trading_Backend.config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market-session")
public class MarketSessionProperties {
	private ZoneId zone = ZoneId.of("Asia/Kolkata");
	private LocalTime open = LocalTime.of(9, 15);
	private LocalTime close = LocalTime.of(15, 30);
	private List<LocalDate> holidays = new ArrayList<>();

	public ZoneId getZone() { return zone; }
	public void setZone(ZoneId value) { zone = value; }
	public LocalTime getOpen() { return open; }
	public void setOpen(LocalTime value) { open = value; }
	public LocalTime getClose() { return close; }
	public void setClose(LocalTime value) { close = value; }
	public List<LocalDate> getHolidays() { return holidays; }
	public void setHolidays(List<LocalDate> value) { holidays = value; }
}
