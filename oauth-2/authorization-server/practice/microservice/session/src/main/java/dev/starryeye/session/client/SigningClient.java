package dev.starryeye.session.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class SigningClient {

	private final RestClient restClient;

	public SigningClient(RestClient.Builder builder, @Value("${my.signing-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public String sign(Map<String, Object> claims, String typ) {
		Map<String, Object> body = Map.of("claims", claims, "typ", typ);
		Map<?, ?> response = restClient.post().uri("/internal/sign").body(body).retrieve().body(Map.class);
		return (String) response.get("jwt");
	}
}
