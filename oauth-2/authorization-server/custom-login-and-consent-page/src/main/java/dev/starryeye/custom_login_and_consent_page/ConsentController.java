package dev.starryeye.custom_login_and_consent_page;

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
     * 커스텀 consent 페이지 렌더링. (설정은 AuthorizationServerConfig 의 consentPage() 참고)
     *
     * consent 가 필요하면 OAuth2AuthorizationEndpointFilter 가 이 URI 로 redirect 하며 쿼리 파라미터 3개를 넘겨준다..
     *      client_id : 어떤 client 의 요청인지 (화면에 client 이름 표시용 + 제출 시 되돌려줘야 함)
     *      scope : client 가 요청한 scope 목록 (공백 구분)
     *      state : 진행 중인 인가(OAuth2Authorization)를 다시 찾는 내부 조회 키.. hidden 으로 유지해서 그대로 되돌려줘야 한다.
     *
     * 기본 consent 페이지가 해주던 일을 재현한다..
     *      1. openid 는 동의 대상 scope 가 아니므로 화면에서 제외
     *      2. OAuth2AuthorizationConsentService 에서 기승인 scope 를 조회하여..
     *          기승인 scope 는 "이미 동의함" 표시만 하고 (다시 물어보지 않음)
     *          신규 scope 만 체크박스로 승인받는다.
     *      3. 제출은 기본 페이지와 동일하게 POST "/oauth2/authorize" (client_id, state, 체크된 scope)
     *          기승인이 전혀 없는 상태에서 체크된 scope 없이 제출하면 거부로 처리되어 access_denied 로 redirect 된다.
     *          (기승인 scope 가 있다면 빈 제출이어도 기승인 범위로 code 가 발급된다.. main class 확인 포인트 참고)
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
