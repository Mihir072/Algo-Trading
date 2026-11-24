package com.example.Algo_Trading_Backend.broker;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

@Service
public class AngelOneAuthService {
	private final AngelOneProperties properties;
	private final RestClient restClient;
	private final AtomicReference<Session> session = new AtomicReference<>();

	public record Session(String accessToken, String refreshToken, String feedToken, Instant createdAt) {}

	public AngelOneAuthService(AngelOneProperties properties) {
		this.properties = properties;
		this.restClient = RestClient.create(properties.getBaseUrl());
	}

	public Session login() {
		requireConfigured();
		JsonNode response = restClient.post()
				.uri("/rest/auth/angelbroking/user/v1/loginByPassword")
				.headers(this::populateHeaders)
				.body(java.util.Map.of("clientcode", properties.getClientCode(),
						"password", properties.getPin(), "totp", currentTotp()))
				.retrieve().body(JsonNode.class);
		JsonNode data = response == null ? null : response.path("data");
		Session authenticated = new Session(requiredText(data, "jwtToken"),
				requiredText(data, "refreshToken"), requiredText(data, "feedToken"), Instant.now());
		session.set(authenticated);
		return authenticated;
	}

	public Session refresh() {
		Session current = session.get();
		if (current == null) {
			throw new IllegalStateException("No Angel One session is available to refresh");
		}
		String checksum = sha256(properties.getApiKey() + current.refreshToken() + current.accessToken());
		JsonNode response = restClient.post()
				.uri("/rest/auth/angelbroking/jwt/v1/generateTokens")
				.headers(headers -> {
					populateHeaders(headers);
					headers.setBearerAuth(current.accessToken());
				})
				.body(java.util.Map.of("refreshToken", current.refreshToken(), "checksum", checksum))
				.retrieve().body(JsonNode.class);
		JsonNode data = response == null ? null : response.path("data");
		Session refreshed = new Session(requiredText(data, "jwtToken"),
				requiredText(data, "refreshToken"),
				data.path("feedToken").asText(current.feedToken()), Instant.now());
		session.set(refreshed);
		return refreshed;
	}

	public Session currentSession() {
		return session.get();
	}

	public String clientCodeForFeed() {
		return properties.getClientCode();
	}

	private void requireConfigured() {
		if (blank(properties.getApiKey()) || blank(properties.getClientCode()) || blank(properties.getPin())
				|| blank(properties.getTotpSecret())) {
			throw new IllegalStateException("Set ANGELONE_API_KEY, ANGELONE_CLIENT_CODE, ANGELONE_PIN, and ANGELONE_TOTP_SECRET");
		}
		if (blank(properties.getClientLocalIp()) || blank(properties.getClientPublicIp())
				|| blank(properties.getMacAddress())) {
			throw new IllegalStateException("Set ANGELONE_CLIENT_LOCAL_IP, ANGELONE_CLIENT_PUBLIC_IP, and ANGELONE_MAC_ADDRESS for SmartAPI");
		}
	}

	public void populateHeaders(HttpHeaders headers) {
		headers.set("X-PrivateKey", properties.getApiKey());
		headers.set("X-ClientLocalIP", properties.getClientLocalIp());
		headers.set("X-ClientPublicIP", properties.getClientPublicIp());
		headers.set("X-MACAddress", properties.getMacAddress());
		headers.set("X-UserType", "USER");
		headers.set("X-SourceID", "WEB");
		headers.set(HttpHeaders.ACCEPT, "application/json");
	}

	private String currentTotp() {
		String secret = properties.getTotpSecret().replace(" ", "").replace("=", "").toUpperCase(Locale.ROOT);
		byte[] key = decodeBase32(secret);
		byte[] counter = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN)
				.putLong(Instant.now().getEpochSecond() / 30).array();
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(key, "HmacSHA1"));
			byte[] digest = mac.doFinal(counter);
			int offset = digest[digest.length - 1] & 0x0f;
			int binary = ((digest[offset] & 0x7f) << 24) | ((digest[offset + 1] & 0xff) << 16)
					| ((digest[offset + 2] & 0xff) << 8) | (digest[offset + 3] & 0xff);
			return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
		} catch (NoSuchAlgorithmException | InvalidKeyException exception) {
			throw new IllegalStateException("Unable to generate TOTP", exception);
		}
	}

	private byte[] decodeBase32(String value) {
		String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
		byte[] output = new byte[value.length() * 5 / 8];
		int buffer = 0;
		int bitsLeft = 0;
		int index = 0;
		for (char character : value.toCharArray()) {
			int digit = alphabet.indexOf(character);
			if (digit < 0) throw new IllegalStateException("Angel One TOTP secret is not valid Base32");
			buffer = (buffer << 5) | digit;
			bitsLeft += 5;
			if (bitsLeft >= 8) {
				output[index++] = (byte) (buffer >> (bitsLeft - 8));
				bitsLeft -= 8;
			}
		}
		if (index == 0) throw new IllegalStateException("Angel One TOTP secret is empty");
		return java.util.Arrays.copyOf(output, index);
	}

	private String sha256(String value) {
		try {
			byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private String requiredText(JsonNode node, String field) {
		String value = node == null ? null : node.path(field).asText(null);
		if (blank(value)) throw new IllegalStateException("Angel One response is missing " + field);
		return value;
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
