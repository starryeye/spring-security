package dev.starryeye.session.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ClientRegistryClient {

	private final RestClient restClient;

	public ClientRegistryClient(RestClient.Builder builder,
			@Value("${my.client-registry-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public ClientInfo getClient(String clientId) {
		return restClient.get().uri("/internal/clients/{clientId}", clientId).retrieve().body(ClientInfo.class);
	}
}
