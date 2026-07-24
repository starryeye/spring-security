package dev.starryeye.auth;

import dev.starryeye.auth.client.ClientInfo;
import dev.starryeye.auth.client.ClientRegistryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class AuthorizeController {

	/**
	 * authorization code + PKCE 의 authorize 엔드포인트를 직접 구현한다.
	 *      인증은 Spring Security 가 강제하므로(SecurityConfig) 이 메서드 진입 시 principal 은 이미 로그인된 사용자(sub)이다.
	 *      client/redirect_uri/scope/PKCE 를 검증하고 code 를 발급해 redirect 한다.
	 *
	 * 주의. redirect_uri 가 등록값과 다르면 그 주소로 redirect 하지 않고 에러 페이지로 처리한다. (open redirect 방지)
	 */

	private final ClientRegistryClient clientRegistryClient;
	private final AuthorizationCodeIssuer codeIssuer;

	@GetMapping("/oauth2/authorize")
	public Object authorize(
			Principal principal,
			@RequestParam("response_type") String responseType,
			@RequestParam("client_id") String clientId,
			@RequestParam("redirect_uri") String redirectUri,
			@RequestParam(value = "scope", required = false) String scope,
			@RequestParam(value = "state", required = false) String state,
			@RequestParam(value = "code_challenge", required = false) String codeChallenge,
			@RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod
	) {
		ClientInfo client;
		try {
			client = clientRegistryClient.getClient(clientId);
		} catch (ClientRegistryClient.ClientNotFoundException e) {
			return errorPage("unknown client_id");
		}

		// redirect_uri 정확 일치 검증 (여기 실패는 redirect 하지 않는다)
		if (!client.redirectUris().contains(redirectUri)) {
			return errorPage("redirect_uri mismatch");
		}

		// 여기서부터의 오류는 redirect_uri 로 error 를 실어 보낸다.
		if (!"code".equals(responseType)) {
			return errorRedirect(redirectUri, "unsupported_response_type", state);
		}
		if (!StringUtils.hasText(codeChallenge) || !"S256".equals(codeChallengeMethod)) {
			return errorRedirect(redirectUri, "invalid_request", state); // 첫 슬라이스는 PKCE(S256) 필수
		}
		String effectiveScope = StringUtils.hasText(scope) ? scope : String.join(" ", client.scopes());
		for (String requested : effectiveScope.split(" ")) {
			if (!client.scopes().contains(requested)) {
				return errorRedirect(redirectUri, "invalid_scope", state);
			}
		}

		String code = codeIssuer.issue(clientId, redirectUri, effectiveScope, principal.getName(), codeChallenge);

		UriComponentsBuilder builder =
				UriComponentsBuilder.fromUriString(redirectUri).queryParam("code", code);
		if (StringUtils.hasText(state)) {
			builder.queryParam("state", state);
		}
		return new RedirectView(builder.encode().build().toUriString());
	}

	private RedirectView errorRedirect(String redirectUri, String error, String state) {
		UriComponentsBuilder builder =
				UriComponentsBuilder.fromUriString(redirectUri).queryParam("error", error);
		if (StringUtils.hasText(state)) {
			builder.queryParam("state", state);
		}
		return new RedirectView(builder.encode().build().toUriString());
	}

	private ResponseEntity<String> errorPage(String message) {
		return ResponseEntity.badRequest().body("authorization error: " + message);
	}
}
