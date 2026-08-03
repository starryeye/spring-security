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
	 * code 에 싣는 scope 는 union 이다..
	 *      "이번 제출로 승인한 scope" 에 "consent 에 이미 기록된 기승인 scope" 를 합치되, pending 의 요청 범위로 자른다.
	 *      이번 승인분만으로 code 를 만들면 화면에 안 뜬 기승인 scope 가 토큰에서 누락된다
	 *      (예: 기승인 openid 가 빠져 id token 이 발급되지 않는다). 범위로 자르는 것은 over-grant 방지다.
	 *
	 * 주의. access_denied 판정은 union 이 아니라 "이번 제출분"만 본다. 그래서 규칙이 비대칭이다..
	 *      기승인이 있는 사용자가 증분 scope 만 거부하면(체크 해제) 승인분이 비어 access_denied 가 되고,
	 *      기승인분까지 함께 거부된 것처럼 처리된다. "이번 화면에서 아무것도 승인하지 않았다"를 거부로 읽는 선택이다.
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
		Set<String> finalScopes = new LinkedHashSet<>(approved);
		for (String granted : grantedScopes) {
			if (requested.contains(granted)) { // pending 범위 밖은 버린다 (over-grant 방지)
				finalScopes.add(granted);
			}
		}

		String code = codeIssuer.issue(pending.clientId(), pending.redirectUri(),
				String.join(" ", finalScopes), pending.sub(), pending.codeChallenge(),
				pending.nonce(), pending.authTime(), pending.sid());

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
