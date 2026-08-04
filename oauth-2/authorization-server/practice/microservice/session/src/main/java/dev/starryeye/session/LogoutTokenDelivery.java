package dev.starryeye.session;

import dev.starryeye.session.client.ClientInfo;
import dev.starryeye.session.client.ClientRegistryClient;
import dev.starryeye.session.client.SigningClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogoutTokenDelivery {

	/**
	 * RP 한 곳에 logout token 을 form POST 한다. (Back-Channel Logout 1.0 2.5)
	 *
	 * 주의. backchannel_logout_uri 가 없는 client 는 통지 대상이 아니다. 사용자 세션이 없는
	 *      client_credentials 전용 client 가 그런 경우다.
	 */

	private static final String LOGOUT_TOKEN_PARAMETER = "logout_token";

	private final ClientRegistryClient clientRegistryClient;
	private final SigningClient signingClient;
	private final LogoutTokenFactory logoutTokenFactory;
	private final RestClient.Builder restClientBuilder;

	public void deliver(String sid, String sub, String clientId) {
		ClientInfo client = clientRegistryClient.getClient(clientId);
		if (!StringUtils.hasText(client.backchannelLogoutUri())) {
			log.debug("backchannel_logout_uri 가 없어 건너뛴다. clientId={}", clientId);
			return;
		}

		String logoutToken = signingClient.sign(
				logoutTokenFactory.create(sid, sub, clientId), LogoutTokenFactory.LOGOUT_TOKEN_TYP);

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add(LOGOUT_TOKEN_PARAMETER, logoutToken);

		restClientBuilder.build().post()
				.uri(client.backchannelLogoutUri())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.toBodilessEntity();

		log.info("logout token 발송 완료. sid={} clientId={}", sid, clientId);
	}
}
