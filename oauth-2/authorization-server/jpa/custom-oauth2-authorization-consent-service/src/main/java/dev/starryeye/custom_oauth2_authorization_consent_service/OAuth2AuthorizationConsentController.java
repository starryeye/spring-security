package dev.starryeye.custom_oauth2_authorization_consent_service;

import dev.starryeye.custom_oauth2_authorization_consent_service.jpa.OAuth2AuthorizationConsentEntity;
import dev.starryeye.custom_oauth2_authorization_consent_service.jpa.OAuth2AuthorizationConsentEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class OAuth2AuthorizationConsentController {

    /**
     * DB 에 저장된 동의 기록을 관찰하는 controller 이다.
     *
     * "/oauth2-authorization-consents"
     *      OAuth2AuthorizationConsent 로 변환된 결과 조회 (authorities 가 GrantedAuthority 로 복원된 모습)
     * "/oauth2-authorization-consents/raw"
     *      entity 원문 조회.. 동의한 scope 가 "SCOPE_{scope}" comma 문자열로 저장된 것을 볼 수 있다.
     */

    private final OAuth2AuthorizationConsentService oAuth2AuthorizationConsentService;
    private final OAuth2AuthorizationConsentEntityRepository entityRepository;

    @GetMapping("/oauth2-authorization-consents")
    public List<OAuth2AuthorizationConsent> oauth2AuthorizationConsents() {
        return entityRepository.findAll().stream()
                .map(entity -> oAuth2AuthorizationConsentService.findById(entity.getRegisteredClientId(), entity.getPrincipalName()))
                .toList();
    }

    @GetMapping("/oauth2-authorization-consents/raw")
    public List<OAuth2AuthorizationConsentEntity> oauth2AuthorizationConsentEntities() {
        return entityRepository.findAll();
    }
}
