package dev.starryeye.custom_oauth2_authorization_consent_service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;

import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class LoggingOAuth2AuthorizationConsentService implements OAuth2AuthorizationConsentService {

    /**
     * 프레임워크가 OAuth2AuthorizationConsentService 를 언제 호출하는지 관찰하기 위한 로깅 데코레이터이다.
     *      실제 저장은 delegate(JpaOAuth2AuthorizationConsentService)에 위임하고 호출 내용만 로그로 남긴다.
     *      grant flow 를 수행하면서 호출 시퀀스를 관찰해볼 것. (관찰 결과는 main class 주석에 정리해놓음)
     */

    private final OAuth2AuthorizationConsentService delegate;

    @Override
    public void save(OAuth2AuthorizationConsent authorizationConsent) {
        log.info("[save] principal={}, 동의한 authorities={}",
                authorizationConsent.getPrincipalName(), authorities(authorizationConsent));
        delegate.save(authorizationConsent);
    }

    @Override
    public void remove(OAuth2AuthorizationConsent authorizationConsent) {
        log.info("[remove] principal={}, authorities={}",
                authorizationConsent.getPrincipalName(), authorities(authorizationConsent));
        delegate.remove(authorizationConsent);
    }

    @Override
    public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        OAuth2AuthorizationConsent result = delegate.findById(registeredClientId, principalName);
        log.info("[findById] principal={}, 조회결과={}",
                principalName, result != null ? "기승인 authorities=" + authorities(result) : "null(동의 기록 없음)");
        return result;
    }

    private String authorities(OAuth2AuthorizationConsent authorizationConsent) {
        return authorizationConsent.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(", "));
    }
}
