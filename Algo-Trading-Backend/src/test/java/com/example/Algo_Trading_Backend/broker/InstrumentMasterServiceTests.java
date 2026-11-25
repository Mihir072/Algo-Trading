package com.example.Algo_Trading_Backend.broker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class InstrumentMasterServiceTests {
	private final InstrumentMasterService service = new InstrumentMasterService(new AngelOneProperties(), null);
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	void parsesDerivativeContractsWithUppercaseExpiryMonths() throws Exception {
		var future = service.parse(jsonMapper.readTree("""
				{
				  "token": "123",
				  "symbol": "NIFTY29DEC26FUT",
				  "name": "NIFTY",
				  "expiry": "29DEC2026",
				  "strike": "0.000000",
				  "lotsize": "65",
				  "instrumenttype": "FUTIDX",
				  "exch_seg": "NFO"
				}
				""")).orElseThrow();
		var call = service.parse(jsonMapper.readTree("""
				{
				  "token": "456",
				  "symbol": "NIFTY29DEC2625000CE",
				  "name": "NIFTY",
				  "expiry": "29DEC2026",
				  "strike": "2500000.000000",
				  "lotsize": "65",
				  "instrumenttype": "OPTIDX",
				  "exch_seg": "NFO"
				}
				""")).orElseThrow();

		assertEquals(LocalDate.of(2026, 12, 29), future.expiry());
		assertEquals(LocalDate.of(2026, 12, 29), call.expiry());
		assertEquals(new BigDecimal("25000.000000"), call.strike());
		assertEquals(com.example.Algo_Trading_Backend.market.OptionType.CE, call.optionType());
	}
}
