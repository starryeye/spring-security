package dev.starryeye.token.client;

import java.util.List;

public record ClientInfo(
		String clientId,
		List<String> redirectUris,
		List<String> scopes,
		String clientSecretHash,
		List<String> grantTypes
) {
}
