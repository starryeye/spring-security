package dev.starryeye.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class UserDirectoryClient {

	/**
	 * user-directory 의 credential 검증 API 를 호출한다. 성공 시 sub/authorities 를 받고, 실패(401)면 예외.
	 */

	private final RestClient restClient;

	public UserDirectoryClient(RestClient.Builder builder, @Value("${my.user-directory-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public record AuthenticatedUser(String sub, List<String> authorities) {
	}

	public AuthenticatedUser authenticate(String username, String password) {
		return restClient.post()
				.uri("/internal/users/authenticate")
				.body(Map.of("username", username, "password", password))
				.retrieve()
				.onStatus(status -> status.value() == 401, (req, res) -> { throw new BadCredentialsRemoteException(); })
				.body(AuthenticatedUser.class);
	}

	public static class BadCredentialsRemoteException extends RuntimeException {
	}
}
