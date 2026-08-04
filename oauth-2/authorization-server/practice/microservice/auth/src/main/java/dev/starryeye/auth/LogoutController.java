package dev.starryeye.auth;

import dev.starryeye.auth.client.ClientInfo;
import dev.starryeye.auth.client.ClientRegistryClient;
import dev.starryeye.auth.client.SessionClient;
import dev.starryeye.auth.security.SessionIdIssuer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequiredArgsConstructor
public class LogoutController {

	/**
	 * RP-Initiated Logout 1.0 의 end_session_endpoint 다.
	 *
	 * 주의. 로그아웃 자체는 어떤 경우에도 수행한다. 세션을 끊는 것은 이 서비스 안에서 끝나는 로컬 작업이라
	 *      외부 의존성이 없다. 검증은 오직 "어디로 돌려보낼지" 를 정할 때만 필요하다. 이 저장소가 다른 곳에서
	 *      지키는 fail-closed 가 여기서는 반대다 — 로그아웃 실패는 세션을 살려두므로 더 위험하다.
	 *
	 * 주의. 미등록 post_logout_redirect_uri 로는 돌려보내지 않는다. authorize 의 redirect_uri 정확 일치와
	 *      같은 원칙이다(open redirect 방지).
	 */

	private final SessionIdIssuer sessionIdIssuer;
	private final SessionClient sessionClient;
	private final IdTokenHintVerifier hintVerifier;
	private final ClientRegistryClient clientRegistryClient;

	@GetMapping("/oauth2/logout")
	public Object logout(
			HttpServletRequest request,
			@RequestParam(value = "id_token_hint", required = false) String idTokenHint,
			@RequestParam(value = "post_logout_redirect_uri", required = false) String postLogoutRedirectUri,
			@RequestParam(value = "state", required = false) String state
	) {
		String redirectTo = resolveRedirect(idTokenHint, postLogoutRedirectUri);

		HttpSession session = request.getSession(false);
		if (session != null) {
			String sid = sessionIdIssuer.currentSid(session);
			if (StringUtils.hasText(sid)) {
				sessionClient.logout(sid);
			}
			session.invalidate();
		}
		SecurityContextHolder.clearContext();

		if (redirectTo == null) {
			return ResponseEntity.ok("logged out");
		}
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(redirectTo);
		if (StringUtils.hasText(state)) {
			builder.queryParam("state", state);
		}
		return new RedirectView(builder.encode().build().toUriString());
	}

	private String resolveRedirect(String idTokenHint, String postLogoutRedirectUri) {
		if (!StringUtils.hasText(idTokenHint) || !StringUtils.hasText(postLogoutRedirectUri)) {
			return null;
		}
		String clientId;
		try {
			clientId = hintVerifier.verify(idTokenHint);
		} catch (IdTokenHintVerifier.InvalidHintException e) {
			return null; // 어느 client 기준으로 검증할지 알 수 없으므로 돌려보내지 않는다
		}
		ClientInfo client;
		try {
			client = clientRegistryClient.getClient(clientId);
		} catch (Exception e) {
			return null;
		}
		return client.postLogoutRedirectUris().contains(postLogoutRedirectUri) ? postLogoutRedirectUri : null;
	}
}
