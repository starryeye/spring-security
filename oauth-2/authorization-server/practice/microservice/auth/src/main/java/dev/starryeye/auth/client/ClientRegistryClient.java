package dev.starryeye.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ClientRegistryClient {

	/**
	 * client-registry 조회. authorize 단계에서 client_id 유효성과 redirect_uri/scope 검증에 쓴다.
	 */

	private final RestClient restClient;

	public ClientRegistryClient(RestClient.Builder builder, @Value("${my.client-registry-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public ClientInfo getClient(String clientId) {
		return restClient.get()
				.uri("/internal/clients/{clientId}", clientId)
				.retrieve()
				.onStatus(status -> status.value() == 404, (req, res) -> { throw new ClientNotFoundException(); })
				.body(ClientInfo.class);
	}

	public static class ClientNotFoundException extends RuntimeException {
	}
}
