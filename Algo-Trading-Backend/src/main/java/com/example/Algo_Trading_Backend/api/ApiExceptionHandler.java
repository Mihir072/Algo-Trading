package com.example.Algo_Trading_Backend.api;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class ApiExceptionHandler {
	public record ApiError(Instant timestamp, int status, String error, String message) {}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiError> badRequest(IllegalArgumentException exception) {
		return response(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ApiError> conflict(IllegalStateException exception) {
		return response(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(RestClientException.class)
	public ResponseEntity<ApiError> brokerFailure(RestClientException exception) {
		return response(HttpStatus.BAD_GATEWAY, "External market-data service request failed");
	}

	private ResponseEntity<ApiError> response(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(),
				status.getReasonPhrase(), message));
	}
}
