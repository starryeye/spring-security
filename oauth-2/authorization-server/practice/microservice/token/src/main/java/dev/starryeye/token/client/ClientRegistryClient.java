package dev.starryeye.token.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ClientRegistryClient {

	/**
	 * client-registry 의 client 조회 API 를 호출한다. 없으면(404) ClientNotFoundException 을 던진다.
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
