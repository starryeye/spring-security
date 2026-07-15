package dev.starryeye.production_ready_authorization_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class ConsentController {

    /**
     * 커스텀 consent 페이지 렌더링. (custom-login-and-consent-page 프로젝트 이식.. 동작 상세는 출처 프로젝트 주석 참고)
     *      기승인 scope 는 "이미 동의함" 표시만 하고 신규 scope 만 체크박스로 승인받는다.
     *
     * 이 프로젝트에서 달라진 점.. 조회 대상 저장소가 전부 외부 저장소이다.
     *      기승인 scope -> JPA/MySQL (OAuth2AuthorizationConsentService)
     *      state 로 찾아지는 진행 중 인가 -> Redis.. consent 로 redirect 한 인스턴스와 이 페이지를 렌더링하는 인스턴스,
     *      제출(POST "/oauth2/authorize")을 처리하는 인스턴스가 전부 달라도 동작한다. (main class 확인 포인트 2)
     */

    private final RegisteredClientRepository registeredClientRepository;
    private final OAuth2AuthorizationConsentService oAuth2AuthorizationConsentService;

    @GetMapping("/oauth2/consent")
    public String consent(
            Principal principal,
            Model model,
            @RequestParam(OAuth2ParameterNames.CLIENT_ID) String clientId,
            @RequestParam(OAuth2ParameterNames.SCOPE) String scope,
            @RequestParam(OAuth2ParameterNames.STATE) String state
    ) {

        RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);

        // 기승인 scope 조회 (동의 기록이 없으면 빈 집합)
        OAuth2AuthorizationConsent currentConsent = oAuth2AuthorizationConsentService.findById(registeredClient.getId(), principal.getName());
        Set<String> previouslyApprovedScopes = currentConsent != null ? currentConsent.getScopes() : Set.of();

        Set<String> scopesToApprove = new LinkedHashSet<>();
        Set<String> approvedScopesToDisplay = new LinkedHashSet<>();
        for (String requestedScope : StringUtils.delimitedListToStringArray(scope, " ")) {
            if (OidcScopes.OPENID.equals(requestedScope)) {
                continue; // openid 는 동의 대상이 아니다.
            }
            if (previouslyApprovedScopes.contains(requestedScope)) {
                approvedScopesToDisplay.add(requestedScope);
            } else {
                scopesToApprove.add(requestedScope);
            }
        }

        model.addAttribute("clientId", clientId);
        model.addAttribute("clientName", registeredClient.getClientName());
        model.addAttribute("state", state);
        model.addAttribute("principalName", principal.getName());
        model.addAttribute("scopesToApprove", scopesToApprove);
        model.addAttribute("previouslyApprovedScopes", approvedScopesToDisplay);

        return "consent";
    }
}
