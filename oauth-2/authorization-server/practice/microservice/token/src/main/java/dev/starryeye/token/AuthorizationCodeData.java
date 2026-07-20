package dev.starryeye.token;

public record AuthorizationCodeData(
		String clientId,
		String redirectUri,
		String scope,
		String sub,
		String codeChallenge
) {
}
