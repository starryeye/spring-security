package dev.starryeye.token.client;

import java.util.List;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record ClientInfo(
		String clientId,
		List<String> redirectUris,
		List<String> scopes,
		String clientSecretHash,
		List<String> grantTypes
) {
}
