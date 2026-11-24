package com.example.Algo_Trading_Backend.broker;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "angelone")
public class AngelOneProperties {
	private String baseUrl = "https://apiconnect.angelone.in";
	private String instrumentMasterUrl = "https://margincalculator.angelone.in/OpenAPI_File/files/OpenAPIScripMaster.json";
	private String apiKey;
	private String clientCode;
	private String pin;
	private String totpSecret;
	private String clientLocalIp;
	private String clientPublicIp;
	private String macAddress;
	private long instrumentRefreshMinutes = 360;
	private final InstrumentMaster instrumentMaster = new InstrumentMaster();

	public String getBaseUrl() { return baseUrl; }
	public void setBaseUrl(String value) { baseUrl = value; }
	public String getInstrumentMasterUrl() { return instrumentMasterUrl; }
	public void setInstrumentMasterUrl(String value) { instrumentMasterUrl = value; }
	public String getApiKey() { return apiKey; }
	public void setApiKey(String value) { apiKey = value; }
	public String getClientCode() { return clientCode; }
	public void setClientCode(String value) { clientCode = value; }
	public String getPin() { return pin; }
	public void setPin(String value) { pin = value; }
	public String getTotpSecret() { return totpSecret; }
	public void setTotpSecret(String value) { totpSecret = value; }
	public String getClientLocalIp() { return clientLocalIp; }
	public void setClientLocalIp(String value) { clientLocalIp = value; }
	public String getClientPublicIp() { return clientPublicIp; }
	public void setClientPublicIp(String value) { clientPublicIp = value; }
	public String getMacAddress() { return macAddress; }
	public void setMacAddress(String value) { macAddress = value; }
	public long getInstrumentRefreshMinutes() { return instrumentRefreshMinutes; }
	public void setInstrumentRefreshMinutes(long value) { instrumentRefreshMinutes = value; }
	public InstrumentMaster getInstrumentMaster() { return instrumentMaster; }

	public static class InstrumentMaster {
		private boolean enabled;
		public boolean isEnabled() { return enabled; }
		public void setEnabled(boolean value) { enabled = value; }
	}
}
