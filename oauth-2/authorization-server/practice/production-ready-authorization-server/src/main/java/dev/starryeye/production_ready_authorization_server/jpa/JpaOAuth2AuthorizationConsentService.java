package dev.starryeye.production_ready_authorization_server.jpa;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class JpaOAuth2AuthorizationConsentService implements OAuth2AuthorizationConsentService {

    /**
     * OAuth2AuthorizationConsentService 의 JPA 구현이다. (jpa/custom-oauth2-authorization-consent-service 프로젝트 이식)
     *      entity <-> OAuth2AuthorizationConsent 변환은 JdbcOAuth2AuthorizationConsentService 를 참고했다.
     *
     * 구현해야 할 메서드는 3개이다.
     *      save : 사용자가 consent 화면에서 동의를 제출하면 호출된다.
     *          이미 동의 기록이 있으면 합쳐진 authorities 로 갱신된다. (추가 scope 동의 시)
     *      remove : 동의 기록 삭제
     *      findById : (registeredClientId + principalName) 복합키로 조회..
     *          "/oauth2/authorize" 처리 시마다 호출되어, 요청 scope 가 전부 기승인이면 consent 화면을 건너뛴다.
     */

    private final OAuth2AuthorizationConsentEntityRepository repository;

    @Override
    @Transactional
    public void save(OAuth2AuthorizationConsent authorizationConsent) {
        repository.save(toEntity(authorizationConsent));
    }

    @Override
    @Transactional
    public void remove(OAuth2AuthorizationConsent authorizationConsent) {
        repository.deleteById(new OAuth2AuthorizationConsentEntity.CompositeId(
                authorizationConsent.getRegisteredClientId(), authorizationConsent.getPrincipalName()));
    }

    @Override
    @Transactional(readOnly = true)
    public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        return repository.findById(new OAuth2AuthorizationConsentEntity.CompositeId(registeredClientId, principalName))
                .map(this::toObject)
                .orElse(null); // 못 찾으면 null 리턴이 인터페이스 규약이다. (= 아직 동의한 적 없음)
    }

    private OAuth2AuthorizationConsentEntity toEntity(OAuth2AuthorizationConsent authorizationConsent) {

        Set<String> authorities = authorizationConsent.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        return OAuth2AuthorizationConsentEntity.builder()
                .registeredClientId(authorizationConsent.getRegisteredClientId())
                .principalName(authorizationConsent.getPrincipalName())
                .authorities(StringUtils.collectionToCommaDelimitedString(authorities))
                .build();
    }

    private OAuth2AuthorizationConsent toObject(OAuth2AuthorizationConsentEntity entity) {

        OAuth2AuthorizationConsent.Builder builder = OAuth2AuthorizationConsent.withId(
                entity.getRegisteredClientId(), entity.getPrincipalName());

        StringUtils.commaDelimitedListToSet(entity.getAuthorities())
                .forEach(authority -> builder.authority(new SimpleGrantedAuthority(authority)));

        return builder.build();
    }
}
