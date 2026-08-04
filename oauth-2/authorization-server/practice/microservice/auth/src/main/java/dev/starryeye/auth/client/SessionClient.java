package dev.starryeye.auth.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class SessionClient {

	/**
	 * session 서비스에 로그아웃을 통지한다. 발송은 그쪽에서 비동기로 한다.
	 *
	 * 주의. 통지 실패가 로그아웃을 막으면 안 된다. 세션을 끊는 것이 우선이므로 예외를 로그로 흡수한다.
	 *      이 슬라이스에서 유일하게 fail-open 인 지점이다.
	 */

	private final RestClient restClient;

	public SessionClient(RestClient.Builder builder, @Value("${my.session-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public void logout(String sid) {
		try {
			restClient.post().uri("/internal/sessions/logout")
					.body(Map.of("sid", sid))
					.retrieve()
					.toBodilessEntity();
		} catch (Exception e) {
			log.warn("로그아웃 통지 실패. RP 세션이 살아남는다. sid={}", sid, e);
		}
	}
}
