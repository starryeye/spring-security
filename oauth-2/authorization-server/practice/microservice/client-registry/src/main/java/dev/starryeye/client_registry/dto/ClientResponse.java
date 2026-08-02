package dev.starryeye.client_registry.dto;

import java.util.List;

public record ClientResponse(
		String clientId,
		List<String> redirectUris,
		List<String> scopes,
		String clientSecretHash,
		List<String> grantTypes,
		List<String> clientScopes,
		String backchannelLogoutUri,
		List<String> postLogoutRedirectUris
) {
}
