package dev.starryeye.token.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserDirectoryClient {

	/**
	 * user-directory 에서 사용자 프로필을 조회한다. (id token claim, userinfo 응답의 원본)
	 *      user-directory 는 내부 전용 API 라 외부에 노출되지 않으며, 이 서비스가 토큰 검증을 마친 뒤에만 호출한다.
	 */

	private final RestClient restClient;

	public UserDirectoryClient(RestClient.Builder builder, @Value("${my.user-directory-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public UserProfile getUser(String sub) {
		return restClient.get()
				.uri("/internal/users/{sub}", sub)
				.retrieve()
				.onStatus(status -> status.value() == 404, (req, res) -> { throw new UserNotFoundException(); })
				.body(UserProfile.class);
	}

	public static class UserNotFoundException extends RuntimeException {
	}
}
