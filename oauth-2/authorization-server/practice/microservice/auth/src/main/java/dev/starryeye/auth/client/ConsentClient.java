package dev.starryeye.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class ConsentClient {

	/**
	 * consent 서비스의 동의 기록 API 를 호출한다.
	 *      getGrantedScopes 는 항상 non-null 리스트를 돌려준다. 기록이 없으면 빈 리스트다("동의한 적 없음"은 오류가 아니다).
	 *      -> 호출부는 null 을 방어하지 않는다.
	 *
	 * 주의. 조회 실패(consent 다운 등)는 예외로 전파해 fail-closed 로 처리한다.
	 *      "승인 여부를 모른다" 를 "승인했다" 로 취급하면 동의 없이 토큰이 발급된다.
	 */

	private final RestClient restClient;

	public ConsentClient(RestClient.Builder builder, @Value("${my.consent-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public List<String> getGrantedScopes(String sub, String clientId) {
		ConsentInfo consent = restClient.get()
				.uri("/internal/consents/{sub}/{clientId}", sub, clientId)
				.retrieve()
				.body(ConsentInfo.class);
		return (consent == null || consent.scopes() == null) ? List.of() : consent.scopes();
	}

	public void saveConsent(String sub, String clientId, List<String> scopes) {
		restClient.post()
				.uri("/internal/consents")
				.body(Map.of("sub", sub, "clientId", clientId, "scopes", scopes))
				.retrieve()
				.toBodilessEntity();
	}
}
