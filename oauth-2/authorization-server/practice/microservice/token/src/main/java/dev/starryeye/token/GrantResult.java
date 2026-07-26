package dev.starryeye.token;

import dev.starryeye.token.dto.TokenResponse;

public record GrantResult(boolean success, String error, String errorDescription, TokenResponse response) {

	public static GrantResult ok(TokenResponse response) {
		return new GrantResult(true, null, null, response);
	}

	public static GrantResult failed(String error, String errorDescription) {
		return new GrantResult(false, error, errorDescription, null);
	}
}
