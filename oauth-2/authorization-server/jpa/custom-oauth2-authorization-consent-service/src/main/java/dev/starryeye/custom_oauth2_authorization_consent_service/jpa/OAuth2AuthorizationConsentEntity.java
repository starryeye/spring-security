package dev.starryeye.custom_oauth2_authorization_consent_service.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@Entity
@Table(name = "oauth2_authorization_consent")
@IdClass(OAuth2AuthorizationConsentEntity.CompositeId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuth2AuthorizationConsentEntity {

    /**
     * OAuth2AuthorizationConsent 를 저장하기 위한 엔티티이다.
     *      컬럼 구성은 JdbcOAuth2AuthorizationConsentService 의 공식 스키마(oauth2-authorization-consent-schema.sql)를 따랐다.
     *
     * "어떤 사용자(principalName)가 어떤 client 에 어떤 scope 를 동의했는가" 가 저장 대상의 전부라..
     *      영속화 대상 3개 중 구조가 가장 단순하다. (JSON 직렬화도 필요 없음)
     *      식별자가 (registeredClientId + principalName) 복합키라는 점만 유의. -> @IdClass 사용
     *
     * authorities..
     *      동의한 scope 가 "SCOPE_{scope}" 형태의 권한 문자열로 저장된다. (comma 구분)
     */

    @Id
    private String registeredClientId;

    @Id
    private String principalName;

    @Column(length = 1000)
    private String authorities; // comma 구분 문자열 (예. "SCOPE_profile,SCOPE_custom-scope")

    @Builder
    private OAuth2AuthorizationConsentEntity(String registeredClientId, String principalName, String authorities) {
        this.registeredClientId = registeredClientId;
        this.principalName = principalName;
        this.authorities = authorities;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class CompositeId implements Serializable {
        private String registeredClientId;
        private String principalName;
    }
}
