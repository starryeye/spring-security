package dev.starryeye.hello_jpa_authorization_server.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "registered_client")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegisteredClientEntity {

    /**
     * RegisteredClient 를 저장하기 위한 최소한의 엔티티이다.
     *
     * RegisteredClient 의 collection 필드(인증 방식, 권한 부여 방식, redirect uri, scope)는
     *      JdbcRegisteredClientRepository 와 동일하게 comma 로 구분된 단일 문자열 컬럼으로 저장한다.
     *
     * 여기서 매핑하지 않은 필드..
     *      clientIdIssuedAt, clientSecretExpiresAt, postLogoutRedirectUris
     *      ClientSettings, TokenSettings (Map 구조라 직렬화 필요, requireAuthorizationConsent 만 boolean 컬럼으로 유지)
     *      -> custom-registered-client-repository 프로젝트에서 다룬다.
     */

    @Id
    private String id;

    @Column(unique = true, nullable = false)
    private String clientId;

    private String clientSecret;

    private String clientName;

    private String clientAuthenticationMethods; // comma 구분 문자열

    private String authorizationGrantTypes; // comma 구분 문자열

    @Column(length = 1000)
    private String redirectUris; // comma 구분 문자열

    @Column(length = 1000)
    private String scopes; // comma 구분 문자열

    private boolean requireAuthorizationConsent;

    @Builder
    private RegisteredClientEntity(String id, String clientId, String clientSecret, String clientName, String clientAuthenticationMethods, String authorizationGrantTypes, String redirectUris, String scopes, boolean requireAuthorizationConsent) {
        this.id = id;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clientName = clientName;
        this.clientAuthenticationMethods = clientAuthenticationMethods;
        this.authorizationGrantTypes = authorizationGrantTypes;
        this.redirectUris = redirectUris;
        this.scopes = scopes;
        this.requireAuthorizationConsent = requireAuthorizationConsent;
    }
}
