package dev.starryeye.token.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class SessionClient {

	/**
	 * OP 세션 레지스트리에 "이 RP 가 이 세션을 갖는다" 를 등록한다.
	 *
	 * 주의. 실패를 삼키지 않는다. 등록이 안 되면 그 RP 는 영원히 로그아웃 통지를 받지 못하고, RP 는 그 사실을
	 *      알 방법이 없다. discovery 가 backchannel_logout_supported 를 광고하는 이상 조용히 약속을 깨면 안 된다.
	 */

	private final RestClient restClient;

	public SessionClient(RestClient.Builder builder, @Value("${my.session-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public void register(String sid, String sub, String clientId) {
		restClient.post().uri("/internal/sessions")
				.body(Map.of("sid", sid, "sub", sub, "clientId", clientId))
				.retrieve()
				.toBodilessEntity();
	}
}
