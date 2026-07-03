package dev.starryeye.custom_registered_client_repository.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "registered_client")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegisteredClientEntity {

    /**
     * RegisteredClient 의 전체 필드를 매핑한 엔티티이다.
     *      컬럼 구성과 길이는 JdbcRegisteredClientRepository 의 공식 스키마(oauth2-registered-client-schema.sql)를 따랐다.
     *
     * collection 필드 -> comma 구분 문자열
     * ClientSettings, TokenSettings -> JSON 문자열 (JpaRegisteredClientRepository 의 ObjectMapper 로 직렬화)
     * Instant -> datetime(6), 만료 없음은 null (Instant.MAX 는 MySQL datetime 범위를 벗어남)
     */

    @Id
    private String id;

    @Column(unique = true, nullable = false)
    private String clientId;

    private Instant clientIdIssuedAt;

    private String clientSecret; // 인코딩된 값으로 저장 ({bcrypt}..), public client 는 null

    private Instant clientSecretExpiresAt;

    private String clientName;

    @Column(length = 1000)
    private String clientAuthenticationMethods; // comma 구분 문자열

    @Column(length = 1000)
    private String authorizationGrantTypes; // comma 구분 문자열

    @Column(length = 1000)
    private String redirectUris; // comma 구분 문자열

    @Column(length = 1000)
    private String postLogoutRedirectUris; // comma 구분 문자열

    @Column(length = 1000)
    private String scopes; // comma 구분 문자열

    @Column(length = 2000)
    private String clientSettings; // JSON 문자열

    @Column(length = 2000)
    private String tokenSettings; // JSON 문자열

    @Builder
    private RegisteredClientEntity(String id, String clientId, Instant clientIdIssuedAt, String clientSecret, Instant clientSecretExpiresAt, String clientName, String clientAuthenticationMethods, String authorizationGrantTypes, String redirectUris, String postLogoutRedirectUris, String scopes, String clientSettings, String tokenSettings) {
        this.id = id;
        this.clientId = clientId;
        this.clientIdIssuedAt = clientIdIssuedAt;
        this.clientSecret = clientSecret;
        this.clientSecretExpiresAt = clientSecretExpiresAt;
        this.clientName = clientName;
        this.clientAuthenticationMethods = clientAuthenticationMethods;
        this.authorizationGrantTypes = authorizationGrantTypes;
        this.redirectUris = redirectUris;
        this.postLogoutRedirectUris = postLogoutRedirectUris;
        this.scopes = scopes;
        this.clientSettings = clientSettings;
        this.tokenSettings = tokenSettings;
    }
}
