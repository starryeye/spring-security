package dev.starryeye.token;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record AuthorizationCodeData(
		String clientId,
		String redirectUri,
		String scope,
		String sub,
		String codeChallenge,
		String nonce,
		long authTime
) {
}
