package dev.starryeye.token.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TokenStateClient {

	/**
	 * token-state 의 refresh token 상태 API 를 호출한다.
	 *
	 * 주의. 어떤 호출도 예외를 잡지 않는다(fail-closed). 상태를 바꾸지 못했거나 확인하지 못했는데 성공한 것처럼
	 *      진행하면, 폐기되지 않은 토큰을 폐기했다고 응답하거나 살아있는 토큰을 죽었다고 응답하게 된다.
	 *      전파된 예외는 OAuth2ExceptionHandler 가 server_error 로 정규화한다.
	 */

	private final RestClient restClient;

	public TokenStateClient(RestClient.Builder builder, @Value("${my.token-state-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public IssuedRefreshToken issue(String clientId, String sub, String scope, long authTime) {
		return restClient.post()
				.uri("/internal/refresh-tokens")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("clientId", clientId, "sub", sub, "scope", scope, "authTime", authTime))
				.retrieve()
				.body(IssuedRefreshToken.class);
	}

	/**
	 * requestedScope 는 축소 요청(RFC 6749 6)이며 없으면 null 이다. 검증은 token-state 가 회전과 같은 트랜잭션 안에서
	 *      하므로 여기서 미리 거르지 않는다. 여기서 걸러도 소용이 없다 — 저장된 scope 를 알려면 회전 응답이
	 *      필요한데, 그때는 이미 이전 토큰이 소진된 뒤다.
	 */
	public RotateResult rotate(String refreshToken, String clientId, String requestedScope) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("refreshToken", refreshToken);
		body.put("clientId", clientId);
		body.put("requestedScope", requestedScope); // Map.of 는 null 값을 담지 못한다

		return restClient.post()
				.uri("/internal/refresh-tokens/rotate")
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(RotateResult.class);
	}

	public boolean revoke(String refreshToken, String clientId) {
		Map<?, ?> response = restClient.post()
				.uri("/internal/refresh-tokens/revoke")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("refreshToken", refreshToken, "clientId", clientId))
				.retrieve()
				.body(Map.class);
		return response != null && Boolean.TRUE.equals(response.get("revoked"));
	}

	public RefreshTokenInfo introspect(String refreshToken) {
		return restClient.post()
				.uri("/internal/refresh-tokens/introspect")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("refreshToken", refreshToken))
				.retrieve()
				.body(RefreshTokenInfo.class);
	}
}
