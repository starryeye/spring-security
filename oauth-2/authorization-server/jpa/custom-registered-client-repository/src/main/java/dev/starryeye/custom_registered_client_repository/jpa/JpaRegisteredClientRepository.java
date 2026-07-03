package dev.starryeye.custom_registered_client_repository.jpa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class JpaRegisteredClientRepository implements RegisteredClientRepository {

    /**
     * RegisteredClientRepository 를 JPA 로 구현하며 RegisteredClient 의 전체 필드를 매핑한다.
     *      entity <-> RegisteredClient 변환 로직은 JdbcRegisteredClientRepository 의
     *      RegisteredClientParametersMapper(저장 방향) / RegisteredClientRowMapper(조회 방향) 를 참고했다.
     */

    private final RegisteredClientEntityRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JpaRegisteredClientRepository(RegisteredClientEntityRepository repository) {
        this.repository = repository;

        /**
         * ClientSettings, TokenSettings 의 Map<String, Object> 를 JSON 으로 직렬화/역직렬화하기 위한 ObjectMapper 설정이다.
         * 기본 ObjectMapper 로는 TokenSettings 내부의 Duration, OAuth2TokenFormat, SignatureAlgorithm 을 다룰 수 없다.
         * JdbcRegisteredClientRepository$RegisteredClientRowMapper 와 동일하게 두 모듈을 등록한다.
         *      SecurityJackson2Modules.getModules(classLoader)
         *          spring security 가 제공하는 jackson 모듈 목록.. JSON 에 @class 타입 정보를 남기는 default typing 과
         *          역직렬화를 허용할 클래스 목록(allowlist) 관리가 포함된다.
         *      OAuth2AuthorizationServerJackson2Module
         *          Duration, OAuth2TokenFormat 등 authorization server 쪽 타입의 (역)직렬화 mixin 제공
         */
        ClassLoader classLoader = JpaRegisteredClientRepository.class.getClassLoader();
        this.objectMapper.registerModules(SecurityJackson2Modules.getModules(classLoader));
        this.objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
    }

    @Override
    @Transactional
    public void save(RegisteredClient registeredClient) {
        repository.save(toEntity(registeredClient));
    }

    @Override
    @Transactional(readOnly = true)
    public RegisteredClient findById(String id) {
        return repository.findById(id)
                .map(this::toObject)
                .orElse(null); // 못 찾으면 null 리턴이 인터페이스 규약이다.
    }

    @Override
    @Transactional(readOnly = true)
    public RegisteredClient findByClientId(String clientId) {
        return repository.findByClientId(clientId)
                .map(this::toObject)
                .orElse(null);
    }

    private RegisteredClientEntity toEntity(RegisteredClient registeredClient) {

        Set<String> clientAuthenticationMethods = registeredClient.getClientAuthenticationMethods().stream()
                .map(ClientAuthenticationMethod::getValue)
                .collect(Collectors.toSet());
        Set<String> authorizationGrantTypes = registeredClient.getAuthorizationGrantTypes().stream()
                .map(AuthorizationGrantType::getValue)
                .collect(Collectors.toSet());

        return RegisteredClientEntity.builder()
                .id(registeredClient.getId())
                .clientId(registeredClient.getClientId())
                .clientIdIssuedAt(registeredClient.getClientIdIssuedAt())
                .clientSecret(registeredClient.getClientSecret())
                .clientSecretExpiresAt(registeredClient.getClientSecretExpiresAt())
                .clientName(registeredClient.getClientName())
                .clientAuthenticationMethods(StringUtils.collectionToCommaDelimitedString(clientAuthenticationMethods))
                .authorizationGrantTypes(StringUtils.collectionToCommaDelimitedString(authorizationGrantTypes))
                .redirectUris(StringUtils.collectionToCommaDelimitedString(registeredClient.getRedirectUris()))
                .postLogoutRedirectUris(StringUtils.collectionToCommaDelimitedString(registeredClient.getPostLogoutRedirectUris()))
                .scopes(StringUtils.collectionToCommaDelimitedString(registeredClient.getScopes()))
                .clientSettings(writeMap(registeredClient.getClientSettings().getSettings()))
                .tokenSettings(writeMap(registeredClient.getTokenSettings().getSettings()))
                .build();
    }

    private RegisteredClient toObject(RegisteredClientEntity entity) {

        return RegisteredClient.withId(entity.getId())
                .clientId(entity.getClientId())
                .clientIdIssuedAt(entity.getClientIdIssuedAt())
                .clientSecret(entity.getClientSecret())
                .clientSecretExpiresAt(entity.getClientSecretExpiresAt())
                .clientName(entity.getClientName())
                .clientAuthenticationMethods(methods ->
                        StringUtils.commaDelimitedListToSet(entity.getClientAuthenticationMethods()).forEach(value ->
                                methods.add(resolveClientAuthenticationMethod(value))
                        )
                )
                .authorizationGrantTypes(grantTypes ->
                        StringUtils.commaDelimitedListToSet(entity.getAuthorizationGrantTypes()).forEach(value ->
                                grantTypes.add(resolveAuthorizationGrantType(value))
                        )
                )
                .redirectUris(uris -> uris.addAll(StringUtils.commaDelimitedListToSet(entity.getRedirectUris())))
                .postLogoutRedirectUris(uris -> uris.addAll(StringUtils.commaDelimitedListToSet(entity.getPostLogoutRedirectUris())))
                .scopes(scopes -> scopes.addAll(StringUtils.commaDelimitedListToSet(entity.getScopes())))
                .clientSettings(ClientSettings.withSettings(parseMap(entity.getClientSettings())).build())
                .tokenSettings(TokenSettings.withSettings(parseMap(entity.getTokenSettings())).build())
                .build();
    }

    /**
     * 아래 resolve 메서드들..
     * AuthorizationGrantType, ClientAuthenticationMethod 의 equals 는 value 문자열 기반이라 new 로 생성해도 동작은 하지만..
     * JdbcRegisteredClientRepository 와 동일하게 표준 값이면 상수를 재사용하고, 그 외(커스텀 grant 등)만 new 로 생성한다.
     */
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

    private static ClientAuthenticationMethod resolveClientAuthenticationMethod(String clientAuthenticationMethod) {
        if (ClientAuthenticationMethod.CLIENT_SECRET_BASIC.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
        } else if (ClientAuthenticationMethod.CLIENT_SECRET_POST.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.CLIENT_SECRET_POST;
        } else if (ClientAuthenticationMethod.NONE.getValue().equals(clientAuthenticationMethod)) {
            return ClientAuthenticationMethod.NONE;
        }
        return new ClientAuthenticationMethod(clientAuthenticationMethod);
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
