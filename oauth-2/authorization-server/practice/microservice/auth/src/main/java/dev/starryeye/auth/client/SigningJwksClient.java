package dev.starryeye.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class SigningJwksClient {

	/**
	 * signing 서비스의 공개 JWKS 조회. id_token_hint 서명 검증에 쓴다.
	 */

	private final RestClient restClient;

	public SigningJwksClient(RestClient.Builder builder, @Value("${my.signing-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> jwks() {
		return restClient.get().uri("/oauth2/jwks").retrieve().body(Map.class);
	}
}
