package dev.starryeye.token.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class SigningClient {

	/**
	 * signing 서비스에 claims 를 넘겨 서명된 JWT 를 받는다. jwks 도 signing 이 소유하므로 여기서 프록시한다.
	 */

	private final RestClient restClient;

	public SigningClient(RestClient.Builder builder, @Value("${my.signing-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public String sign(Map<String, Object> claims) {
		Map<String, Object> body = Map.of("claims", claims, "header", Map.of());
		Map<?, ?> response = restClient.post().uri("/internal/sign").body(body).retrieve().body(Map.class);
		return (String) response.get("jwt");
	}

	public Map<?, ?> jwks() {
		return restClient.get().uri("/oauth2/jwks").retrieve().body(Map.class);
	}
}
