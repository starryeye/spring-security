package dev.starryeye.custom_oauth2_authorization_service.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "oauth2_authorization")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuth2AuthorizationEntity {

    /**
     * OAuth2Authorization 을 저장하기 위한 엔티티이다.
     *      컬럼 구성은 JdbcOAuth2AuthorizationService 의 공식 스키마(oauth2-authorization-schema.sql)를 따랐다.
     *      (device grant 용 device_code, user_code 컬럼은 이 프로젝트에서 다루지 않으므로 생략)
     *
     * OAuth2Authorization 은 인가 한 건의 상태 덩어리라서 구조가 복합적이다.
     *      토큰 4종(authorization code, access token, refresh token, id token) 각각의 값 + 발급/만료 시각 + metadata(Map)
     *      attributes(Map).. principal 의 Authentication 객체, OAuth2AuthorizationRequest 가 통째로 들어있다.
     *      -> Map 계열은 전부 JSON 문자열로 직렬화하여 저장한다. (JpaOAuth2AuthorizationService 참고)
     *
     * @Lob..
     *      MySQL 에서 String 필드가 longtext 컬럼으로 매핑된다.
     *      JWT 토큰 값과 직렬화된 attributes 는 수 KB 를 넘기 쉬워 varchar 로는 부족하다. (Jdbc 공식 스키마는 blob 사용)
     */

    @Id
    private String id;

    private String registeredClientId;

    private String principalName;

    private String authorizationGrantType;

    @Column(length = 1000)
    private String authorizedScopes; // comma 구분 문자열

    @Lob
    private String attributes; // JSON 문자열 (Authentication, OAuth2AuthorizationRequest 포함)

    @Column(length = 500)
    private String state; // consent 단계에서 사용하는 조회 키 (attributes 안에도 존재하지만 조회용으로 컬럼 분리, Jdbc 스키마와 동일)

    @Lob
    private String authorizationCodeValue;
    private Instant authorizationCodeIssuedAt;
    private Instant authorizationCodeExpiresAt;
    @Lob
    private String authorizationCodeMetadata; // JSON 문자열 (code 사용 후 invalidated=true 로 갱신되는 것 관찰 포인트)

    @Lob
    private String accessTokenValue;
    private Instant accessTokenIssuedAt;
    private Instant accessTokenExpiresAt;
    @Lob
    private String accessTokenMetadata; // JSON 문자열 (JWT claims 가 들어있다)
    private String accessTokenType;
    @Column(length = 1000)
    private String accessTokenScopes; // comma 구분 문자열

    @Lob
    private String refreshTokenValue;
    private Instant refreshTokenIssuedAt;
    private Instant refreshTokenExpiresAt;
    @Lob
    private String refreshTokenMetadata; // JSON 문자열

    @Lob
    private String oidcIdTokenValue;
    private Instant oidcIdTokenIssuedAt;
    private Instant oidcIdTokenExpiresAt;
    @Lob
    private String oidcIdTokenMetadata; // JSON 문자열 (id token claims 가 들어있다)

    @Builder
    private OAuth2AuthorizationEntity(String id, String registeredClientId, String principalName, String authorizationGrantType, String authorizedScopes, String attributes, String state,
                                      String authorizationCodeValue, Instant authorizationCodeIssuedAt, Instant authorizationCodeExpiresAt, String authorizationCodeMetadata,
                                      String accessTokenValue, Instant accessTokenIssuedAt, Instant accessTokenExpiresAt, String accessTokenMetadata, String accessTokenType, String accessTokenScopes,
                                      String refreshTokenValue, Instant refreshTokenIssuedAt, Instant refreshTokenExpiresAt, String refreshTokenMetadata,
                                      String oidcIdTokenValue, Instant oidcIdTokenIssuedAt, Instant oidcIdTokenExpiresAt, String oidcIdTokenMetadata) {
        this.id = id;
        this.registeredClientId = registeredClientId;
        this.principalName = principalName;
        this.authorizationGrantType = authorizationGrantType;
        this.authorizedScopes = authorizedScopes;
        this.attributes = attributes;
        this.state = state;
        this.authorizationCodeValue = authorizationCodeValue;
        this.authorizationCodeIssuedAt = authorizationCodeIssuedAt;
        this.authorizationCodeExpiresAt = authorizationCodeExpiresAt;
        this.authorizationCodeMetadata = authorizationCodeMetadata;
        this.accessTokenValue = accessTokenValue;
        this.accessTokenIssuedAt = accessTokenIssuedAt;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.accessTokenMetadata = accessTokenMetadata;
        this.accessTokenType = accessTokenType;
        this.accessTokenScopes = accessTokenScopes;
        this.refreshTokenValue = refreshTokenValue;
        this.refreshTokenIssuedAt = refreshTokenIssuedAt;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
        this.refreshTokenMetadata = refreshTokenMetadata;
        this.oidcIdTokenValue = oidcIdTokenValue;
        this.oidcIdTokenIssuedAt = oidcIdTokenIssuedAt;
        this.oidcIdTokenExpiresAt = oidcIdTokenExpiresAt;
        this.oidcIdTokenMetadata = oidcIdTokenMetadata;
    }
}
