package dev.starryeye.custom_oauth2_authorization_service.jpa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

public class JpaOAuth2AuthorizationService implements OAuth2AuthorizationService {

    /**
     * OAuth2AuthorizationService 를 JPA 로 직접 구현해본다.
     *      entity <-> OAuth2Authorization 변환 로직은 JdbcOAuth2AuthorizationService 의
     *      OAuth2AuthorizationParametersMapper(저장 방향) / OAuth2AuthorizationRowMapper(조회 방향) 를 참고했다.
     *
     * 구현해야 할 메서드는 4개이다.
     *      save : 인가 상태 저장/갱신 (code 발급, token 발급, code 비활성화, revoke 등.. 모든 상태 변화가 save 로 온다)
     *      remove : 인가 상태 삭제 (예. refresh token grant 에서 reuseRefreshTokens(false) 인데 재사용이 감지된 경우 등)
     *      findById : 저장소 식별자로 조회
     *      findByToken : 토큰 값으로 조회.. 프레임워크가 가장 많이 호출하는 메서드이다.
     *          tokenType 으로 어떤 토큰 컬럼을 조회할지 결정된다. (state, code, access_token, refresh_token, id_token)
     *          tokenType 이 null 이면 모든 토큰 컬럼을 대상으로 조회한다.
     *
     * 호출 시퀀스는 LoggingOAuth2AuthorizationService 로 관찰한다. (관찰 결과는 main class 주석 참고)
     */

    private final OAuth2AuthorizationEntityRepository repository;
    private final RegisteredClientRepository registeredClientRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JpaOAuth2AuthorizationService(OAuth2AuthorizationEntityRepository repository, RegisteredClientRepository registeredClientRepository) {
        this.repository = repository;
        this.registeredClientRepository = registeredClientRepository;

        /**
         * attributes, 토큰별 metadata 의 Map<String, Object> 를 JSON 으로 (역)직렬화하기 위한 ObjectMapper 설정이다.
         * custom-registered-client-repository 와 동일한 구성인데.. 여기서는 훨씬 더 중요하다.
         *      attributes 에는 principal 의 Authentication(UsernamePasswordAuthenticationToken)과 OAuth2AuthorizationRequest 가 통째로 들어있어서..
         *      SecurityJackson2Modules 가 제공하는 mixin(직렬화 방법)과 역직렬화 허용 클래스 목록(allowlist) 없이는 (역)직렬화가 불가능하다.
         */
        ClassLoader classLoader = JpaOAuth2AuthorizationService.class.getClassLoader();
        this.objectMapper.registerModules(SecurityJackson2Modules.getModules(classLoader));
        this.objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
    }

    @Override
    @Transactional
    public void save(OAuth2Authorization authorization) {
        repository.save(toEntity(authorization));
    }

    @Override
    @Transactional
    public void remove(OAuth2Authorization authorization) {
        repository.deleteById(authorization.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public OAuth2Authorization findById(String id) {
        return repository.findById(id)
                .map(this::toObject)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {

        if (tokenType == null) {
            return repository.findByAnyTokenValue(token).map(this::toObject).orElse(null);
        }

        return switch (tokenType.getValue()) {
            case OAuth2ParameterNames.STATE -> repository.findByState(token).map(this::toObject).orElse(null);
            case OAuth2ParameterNames.CODE -> repository.findByAuthorizationCodeValue(token).map(this::toObject).orElse(null);
            case OAuth2ParameterNames.ACCESS_TOKEN -> repository.findByAccessTokenValue(token).map(this::toObject).orElse(null);
            case OAuth2ParameterNames.REFRESH_TOKEN -> repository.findByRefreshTokenValue(token).map(this::toObject).orElse(null);
            case OidcParameterNames.ID_TOKEN -> repository.findByOidcIdTokenValue(token).map(this::toObject).orElse(null);
            default -> null;
        };
    }

    private OAuth2AuthorizationEntity toEntity(OAuth2Authorization authorization) {

        OAuth2AuthorizationEntity.OAuth2AuthorizationEntityBuilder builder = OAuth2AuthorizationEntity.builder()
                .id(authorization.getId())
                .registeredClientId(authorization.getRegisteredClientId())
                .principalName(authorization.getPrincipalName())
                .authorizationGrantType(authorization.getAuthorizationGrantType().getValue())
                .authorizedScopes(StringUtils.collectionToCommaDelimitedString(authorization.getAuthorizedScopes()))
                .attributes(writeMap(authorization.getAttributes()))
                .state(authorization.getAttribute(OAuth2ParameterNames.STATE));

        OAuth2Authorization.Token<OAuth2AuthorizationCode> authorizationCode = authorization.getToken(OAuth2AuthorizationCode.class);
        if (authorizationCode != null) {
            builder
                    .authorizationCodeValue(authorizationCode.getToken().getTokenValue())
                    .authorizationCodeIssuedAt(authorizationCode.getToken().getIssuedAt())
                    .authorizationCodeExpiresAt(authorizationCode.getToken().getExpiresAt())
                    .authorizationCodeMetadata(writeMap(authorizationCode.getMetadata()));
        }

        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getToken(OAuth2AccessToken.class);
        if (accessToken != null) {
            builder
                    .accessTokenValue(accessToken.getToken().getTokenValue())
                    .accessTokenIssuedAt(accessToken.getToken().getIssuedAt())
                    .accessTokenExpiresAt(accessToken.getToken().getExpiresAt())
                    .accessTokenMetadata(writeMap(accessToken.getMetadata()))
                    .accessTokenType(accessToken.getToken().getTokenType().getValue())
                    .accessTokenScopes(StringUtils.collectionToCommaDelimitedString(accessToken.getToken().getScopes()));
        }

        OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getToken(OAuth2RefreshToken.class);
        if (refreshToken != null) {
            builder
                    .refreshTokenValue(refreshToken.getToken().getTokenValue())
                    .refreshTokenIssuedAt(refreshToken.getToken().getIssuedAt())
                    .refreshTokenExpiresAt(refreshToken.getToken().getExpiresAt())
                    .refreshTokenMetadata(writeMap(refreshToken.getMetadata()));
        }

        OAuth2Authorization.Token<OidcIdToken> oidcIdToken = authorization.getToken(OidcIdToken.class);
        if (oidcIdToken != null) {
            builder
                    .oidcIdTokenValue(oidcIdToken.getToken().getTokenValue())
                    .oidcIdTokenIssuedAt(oidcIdToken.getToken().getIssuedAt())
                    .oidcIdTokenExpiresAt(oidcIdToken.getToken().getExpiresAt())
                    .oidcIdTokenMetadata(writeMap(oidcIdToken.getMetadata()));
        }

        return builder.build();
    }

    private OAuth2Authorization toObject(OAuth2AuthorizationEntity entity) {

        /**
         * OAuth2Authorization 은 RegisteredClient 를 기반으로 복원해야 하므로 RegisteredClientRepository 의존이 필요하다.
         *      JdbcOAuth2AuthorizationService 의 OAuth2AuthorizationRowMapper 도 동일한 구조이다.
         */
        RegisteredClient registeredClient = registeredClientRepository.findById(entity.getRegisteredClientId());
        if (registeredClient == null) {
            throw new DataRetrievalFailureException("The RegisteredClient with id '%s' was not found in the RegisteredClientRepository.".formatted(entity.getRegisteredClientId()));
        }

        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .id(entity.getId())
                .principalName(entity.getPrincipalName())
                .authorizationGrantType(resolveAuthorizationGrantType(entity.getAuthorizationGrantType()))
                .authorizedScopes(StringUtils.commaDelimitedListToSet(entity.getAuthorizedScopes()))
                .attributes(attributes -> attributes.putAll(parseMap(entity.getAttributes())));

        if (entity.getState() != null) {
            builder.attribute(OAuth2ParameterNames.STATE, entity.getState());
        }

        if (entity.getAuthorizationCodeValue() != null) {
            OAuth2AuthorizationCode authorizationCode = new OAuth2AuthorizationCode(
                    entity.getAuthorizationCodeValue(), entity.getAuthorizationCodeIssuedAt(), entity.getAuthorizationCodeExpiresAt());
            builder.token(authorizationCode, metadata -> metadata.putAll(parseMap(entity.getAuthorizationCodeMetadata())));
        }

        if (entity.getAccessTokenValue() != null) {
            OAuth2AccessToken accessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER, // 현재 표준 토큰 타입은 Bearer 뿐이다.
                    entity.getAccessTokenValue(), entity.getAccessTokenIssuedAt(), entity.getAccessTokenExpiresAt(),
                    StringUtils.commaDelimitedListToSet(entity.getAccessTokenScopes()));
            builder.token(accessToken, metadata -> metadata.putAll(parseMap(entity.getAccessTokenMetadata())));
        }

        if (entity.getRefreshTokenValue() != null) {
            OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                    entity.getRefreshTokenValue(), entity.getRefreshTokenIssuedAt(), entity.getRefreshTokenExpiresAt());
            builder.token(refreshToken, metadata -> metadata.putAll(parseMap(entity.getRefreshTokenMetadata())));
        }

        if (entity.getOidcIdTokenValue() != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = (Map<String, Object>) parseMap(entity.getOidcIdTokenMetadata())
                    .get(OAuth2Authorization.Token.CLAIMS_METADATA_NAME); // id token 의 claims 는 metadata 안에 저장되어 있다.
            OidcIdToken oidcIdToken = new OidcIdToken(
                    entity.getOidcIdTokenValue(), entity.getOidcIdTokenIssuedAt(), entity.getOidcIdTokenExpiresAt(), claims);
            builder.token(oidcIdToken, metadata -> metadata.putAll(parseMap(entity.getOidcIdTokenMetadata())));
        }

        return builder.build();
    }

    private static AuthorizationGrantType resolveAuthorizationGrantType(String authorizationGrantType) {
        if (AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.AUTHORIZATION_CODE;
        } else if (AuthorizationGrantType.CLIENT_CREDENTIALS.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.CLIENT_CREDENTIALS;
        } else if (AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(authorizationGrantType)) {
            return AuthorizationGrantType.REFRESH_TOKEN;
        }
        return new AuthorizationGrantType(authorizationGrantType);
    }

    private String writeMap(Map<String, Object> data) {
        try {
            return this.objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private Map<String, Object> parseMap(String data) {
        try {
            return this.objectMapper.readValue(data, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
