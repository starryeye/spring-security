package dev.starryeye.auth;

import dev.starryeye.auth.client.ConsentClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class ConsentPageController {

	/**
	 * 동의 화면 제출을 처리한다. (화면 렌더는 AuthorizeController 가 담당)
	 *      승인 scope 는 "제출값 ∩ pending 의 scope" 로 계산한다. 폼을 조작해 상위 scope 를 승인할 수 없게 하기 위함이다.
	 *      승인이 하나도 없으면 표준 에러 access_denied 로 redirect 한다.
	 *
	 * 주의. pending 이 없거나 만료됐으면 redirect 하지 않고 에러 페이지로 끝낸다.
	 *      pending 이 없으면 redirect_uri 를 신뢰할 수 없어 open redirect 통로가 된다.
	 */

	private final PendingAuthorizationStore pendingStore;
	private final ConsentClient consentClient;
	private final AuthorizationCodeIssuer codeIssuer;

	@PostMapping("/oauth2/consent")
	public Object consent(
			@RequestParam("pending_id") String pendingId,
			@RequestParam(value = "scope", required = false) List<String> submittedScopes,
			Principal principal
	) {
		Optional<PendingAuthorization> maybePending = pendingStore.consume(pendingId);
		if (maybePending.isEmpty()) {
			return ResponseEntity.badRequest().body("consent error: pending authorization not found or expired");
		}
		PendingAuthorization pending = maybePending.get();

		if (!pending.sub().equals(principal.getName())) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body("consent error: pending authorization belongs to a different user");
		}

		List<String> requested = List.of(pending.scope().split(" "));
		List<String> approved = new ArrayList<>();
		if (submittedScopes != null) {
			for (String submitted : submittedScopes) {
				if (requested.contains(submitted)) { // pending 범위 밖은 버린다
					approved.add(submitted);
				}
			}
		}

		if (approved.isEmpty()) {
			return errorRedirect(pending.redirectUri(), "access_denied", pending.state());
		}

		consentClient.saveConsent(pending.sub(), pending.clientId(), approved);

		// 기승인 scope 를 union 한다 (pending 요청 범위로 제한). 이번에 새로 승인한 것만으로 code 를 발급하면
		// 이미 승인했던 scope 가 누락되어 토큰 scope 가 좁아진다 (예: 기승인 openid 가 빠져 id token 미발급).
		List<String> grantedScopes = consentClient.getGrantedScopes(pending.sub(), pending.clientId());
		if (grantedScopes == null) {
			grantedScopes = List.of();
		}
		Set<String> finalScopes = new LinkedHashSet<>(approved);
		for (String granted : grantedScopes) {
			if (requested.contains(granted)) { // pending 범위 밖은 버린다 (over-grant 방지)
				finalScopes.add(granted);
			}
		}

		String code = codeIssuer.issue(pending.clientId(), pending.redirectUri(),
				String.join(" ", finalScopes), pending.sub(), pending.codeChallenge(),
				pending.nonce(), pending.authTime());

		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(pending.redirectUri())
				.queryParam("code", code);
		if (StringUtils.hasText(pending.state())) {
			builder.queryParam("state", pending.state());
		}
		return new RedirectView(builder.encode().build().toUriString());
	}

	private RedirectView errorRedirect(String redirectUri, String error, String state) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(redirectUri).queryParam("error", error);
		if (StringUtils.hasText(state)) {
			builder.queryParam("state", state);
		}
		return new RedirectView(builder.encode().build().toUriString());
	}
}
