package dev.starryeye.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PendingAuthorization(
		String clientId,
		String redirectUri,
		String scope,
		String sub,
		String codeChallenge,
		String state,
		String nonce,
		long authTime,
		String sid
) {
}
