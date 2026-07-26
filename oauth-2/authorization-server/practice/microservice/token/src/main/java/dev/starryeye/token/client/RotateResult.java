package dev.starryeye.token.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RotateResult(
		String status,
		String sub,
		String scope,
		long authTime,
		String refreshToken,
		long expiresAt
) {

	public boolean isRotated() {
		return "ROTATED".equals(status);
	}
}
