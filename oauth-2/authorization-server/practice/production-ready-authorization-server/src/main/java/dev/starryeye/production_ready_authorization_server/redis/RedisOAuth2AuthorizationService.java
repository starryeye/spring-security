package dev.starryeye.production_ready_authorization_server.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
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
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class RedisOAuth2AuthorizationService implements OAuth2AuthorizationService {

    /**
     * OAuth2AuthorizationService 의 Redis 구현이다. (jpa/advance/custom-redis-oauth2-authorization-service 프로젝트 이식)
     *
     * 저장 구조.. (JPA 프로젝트의 entity 컬럼 구성을 그대로 hash field 로 옮겼다)
     *      "oauth2:authorization:{id}" : hash.. OAuth2Authorization 한 건 (field = entity 컬럼 대응)
     *      "oauth2:authorization:{토큰종류}:{토큰값}" : string.. 토큰 값 -> id 역색인 (findByToken 용)
     *          RDB 에서는 토큰 컬럼 조회(where)로 해결했지만 Redis 는 key-value 라 역색인 키를 직접 만들어야 한다.
     *
     * TTL.. 이 프로젝트의 핵심
     *      본체 hash : 보유한 토큰 중 가장 늦은 만료 시각까지
     *      역색인 : 해당 토큰 자신의 만료 시각까지
     *      state 만 있는 진행 중 인가 : 고정 TTL(5분)
     *      -> oauth2-authorization-purge 프로젝트에서 배치로 지웠던 것들이 Redis 에서는 TTL 로 스스로 사라진다.
     *          심지어 배치에서 제외할 수밖에 없었던 state-only 데이터도 고정 TTL 로 정리된다.
     *
     * 참고.
     * 공식 가이드(how-to-redis)는 spring-data-redis(@RedisHash + repository + converter) 방식으로 구현한다.
     *      여기서는 TTL 과 저장 구조가 눈에 보이도록 RedisTemplate 로 직접 구현했다.
     */

    private static final String KEY_PREFIX = "oauth2:authorization:";
    private static final Duration STATE_ONLY_TTL = Duration.ofMinutes(5); // 진행 중 인가(로그인/동의 대기)의 유효 시간

    private final StringRedisTemplate redisTemplate;
    private final RegisteredClientRepository registeredClientRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RedisOAuth2AuthorizationService(StringRedisTemplate redisTemplate, RegisteredClientRepository registeredClientRepository) {
        this.redisTemplate = redisTemplate;
        this.registeredClientRepository = registeredClientRepository;

        // attributes, 토큰별 metadata 의 JSON (역)직렬화용.. custom-oauth2-authorization-service 프로젝트와 동일한 구성
        ClassLoader classLoader = RedisOAuth2AuthorizationService.class.getClassLoader();
        this.objectMapper.registerModules(SecurityJackson2Modules.getModules(classLoader));
        this.objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
    }

    @Override
    public void save(OAuth2Authorization authorization) {

        String key = KEY_PREFIX + authorization.getId();

        // consent 단계(state) -> code 발급 단계로 넘어갈 때 없어지는 field 가 남지 않도록 갈아끼운다.
        redisTemplate.delete(key);
        redisTemplate.opsForHash().putAll(key, toHash(authorization));
        redisTemplate.expire(key, mainTtl(authorization));

        // 토큰 값 -> id 역색인.. 각 토큰 자신의 만료 시각을 TTL 로 설정
        // 참고. consent 단계에 만든 state 역색인은 code 발급 후에도 TTL 만료까지 남지만.. 프레임워크가 state 조회를 하는 것은 consent 처리 단계뿐이라 무해하고, TTL 로 알아서 정리된다.
        saveIndex(OAuth2ParameterNames.STATE, authorization.getAttribute(OAuth2ParameterNames.STATE), STATE_ONLY_TTL, authorization.getId());
        OAuth2Authorization.Token<OAuth2AuthorizationCode> authorizationCode = authorization.getToken(OAuth2AuthorizationCode.class);
        if (authorizationCode != null) {
            saveIndex(OAuth2ParameterNames.CODE, authorizationCode.getToken().getTokenValue(), remainingTtl(authorizationCode.getToken()), authorization.getId());
        }
        if (authorization.getAccessToken() != null) {
            saveIndex(OAuth2ParameterNames.ACCESS_TOKEN, authorization.getAccessToken().getToken().getTokenValue(), remainingTtl(authorization.getAccessToken().getToken()), authorization.getId());
        }
        if (authorization.getRefreshToken() != null) {
            saveIndex(OAuth2ParameterNames.REFRESH_TOKEN, authorization.getRefreshToken().getToken().getTokenValue(), remainingTtl(authorization.getRefreshToken().getToken()), authorization.getId());
        }
        OAuth2Authorization.Token<OidcIdToken> oidcIdToken = authorization.getToken(OidcIdToken.class);
        if (oidcIdToken != null) {
            saveIndex(OidcParameterNames.ID_TOKEN, oidcIdToken.getToken().getTokenValue(), remainingTtl(oidcIdToken.getToken()), authorization.getId());
        }
    }

    @Override
    public void remove(OAuth2Authorization authorization) {

        redisTemplate.delete(KEY_PREFIX + authorization.getId());

        deleteIndex(OAuth2ParameterNames.STATE, authorization.getAttribute(OAuth2ParameterNames.STATE));
        OAuth2Authorization.Token<OAuth2AuthorizationCode> authorizationCode = authorization.getToken(OAuth2AuthorizationCode.class);
        if (authorizationCode != null) {
            deleteIndex(OAuth2ParameterNames.CODE, authorizationCode.getToken().getTokenValue());
        }
        if (authorization.getAccessToken() != null) {
            deleteIndex(OAuth2ParameterNames.ACCESS_TOKEN, authorization.getAccessToken().getToken().getTokenValue());
        }
        if (authorization.getRefreshToken() != null) {
            deleteIndex(OAuth2ParameterNames.REFRESH_TOKEN, authorization.getRefreshToken().getToken().getTokenValue());
        }
        OAuth2Authorization.Token<OidcIdToken> oidcIdToken = authorization.getToken(OidcIdToken.class);
        if (oidcIdToken != null) {
            deleteIndex(OidcParameterNames.ID_TOKEN, oidcIdToken.getToken().getTokenValue());
        }
    }

    @Override
    public OAuth2Authorization findById(String id) {

        Map<Object, Object> hash = redisTemplate.opsForHash().entries(KEY_PREFIX + id);
        if (hash.isEmpty()) {
            return null;
        }

        return toObject(hash);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {

        String id;
        if (tokenType == null) {
            id = findIdByAnyIndex(token);
        } else {
            id = redisTemplate.opsForValue().get(indexKey(tokenType.getValue(), token));
        }

        return id != null ? findById(id) : null;
    }

    private String findIdByAnyIndex(String token) {
        for (String type : new String[]{OAuth2ParameterNames.STATE, OAuth2ParameterNames.CODE, OAuth2ParameterNames.ACCESS_TOKEN, OAuth2ParameterNames.REFRESH_TOKEN, OidcParameterNames.ID_TOKEN}) {
            String id = redisTemplate.opsForValue().get(indexKey(type, token));
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private void saveIndex(String tokenType, String tokenValue, Duration ttl, String id) {
        if (tokenValue == null || ttl.isNegative() || ttl.isZero()) {
            return; // 이미 만료된 토큰은 역색인을 만들지 않는다.
        }
        redisTemplate.opsForValue().set(indexKey(tokenType, tokenValue), id, ttl);
    }

    private void deleteIndex(String tokenType, String tokenValue) {
        if (tokenValue != null) {
            redisTemplate.delete(indexKey(tokenType, tokenValue));
        }
    }

    private String indexKey(String tokenType, String tokenValue) {
        return KEY_PREFIX + tokenType + ":" + tokenValue;
    }

    // 보유한 토큰 중 가장 늦은 만료 시각까지 본체를 유지한다. 토큰이 없으면(state 만) 고정 TTL.
    private Duration mainTtl(OAuth2Authorization authorization) {

        Instant latest = null;
        for (Class<? extends OAuth2Token> type : java.util.List.of(OAuth2AuthorizationCode.class, OAuth2AccessToken.class, OAuth2RefreshToken.class, OidcIdToken.class)) {
            OAuth2Authorization.Token<? extends OAuth2Token> token = authorization.getToken(type);
            if (token != null && token.getToken().getExpiresAt() != null) {
                Instant expiresAt = token.getToken().getExpiresAt();
                latest = (latest == null || expiresAt.isAfter(latest)) ? expiresAt : latest;
            }
        }

        if (latest == null) {
            return STATE_ONLY_TTL;
        }

        Duration ttl = Duration.between(Instant.now(), latest);
        return ttl.isNegative() ? Duration.ofSeconds(1) : ttl;
    }

    private Duration remainingTtl(OAuth2Token token) {
        return token.getExpiresAt() != null ? Duration.between(Instant.now(), token.getExpiresAt()) : STATE_ONLY_TTL;
    }

    private Map<String, String> toHash(OAuth2Authorization authorization) {

        Map<String, String> hash = new HashMap<>();
        hash.put("id", authorization.getId());
        hash.put("registeredClientId", authorization.getRegisteredClientId());
        hash.put("principalName", authorization.getPrincipalName());
        hash.put("authorizationGrantType", authorization.getAuthorizationGrantType().getValue());
        hash.put("authorizedScopes", StringUtils.collectionToCommaDelimitedString(authorization.getAuthorizedScopes()));
        hash.put("attributes", writeMap(authorization.getAttributes()));
        putIfPresent(hash, "state", authorization.getAttribute(OAuth2ParameterNames.STATE));

        OAuth2Authorization.Token<OAuth2AuthorizationCode> authorizationCode = authorization.getToken(OAuth2AuthorizationCode.class);
        if (authorizationCode != null) {
            putToken(hash, "authorizationCode", authorizationCode.getToken().getTokenValue(), authorizationCode.getToken().getIssuedAt(), authorizationCode.getToken().getExpiresAt(), authorizationCode.getMetadata());
        }
        if (authorization.getAccessToken() != null) {
            OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
            putToken(hash, "accessToken", accessToken.getToken().getTokenValue(), accessToken.getToken().getIssuedAt(), accessToken.getToken().getExpiresAt(), accessToken.getMetadata());
            hash.put("accessTokenScopes", StringUtils.collectionToCommaDelimitedString(accessToken.getToken().getScopes()));
        }
        if (authorization.getRefreshToken() != null) {
            OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getRefreshToken();
            putToken(hash, "refreshToken", refreshToken.getToken().getTokenValue(), refreshToken.getToken().getIssuedAt(), refreshToken.getToken().getExpiresAt(), refreshToken.getMetadata());
        }
        OAuth2Authorization.Token<OidcIdToken> oidcIdToken = authorization.getToken(OidcIdToken.class);
        if (oidcIdToken != null) {
            putToken(hash, "oidcIdToken", oidcIdToken.getToken().getTokenValue(), oidcIdToken.getToken().getIssuedAt(), oidcIdToken.getToken().getExpiresAt(), oidcIdToken.getMetadata());
        }

        return hash;
    }

    private void putToken(Map<String, String> hash, String name, String value, Instant issuedAt, Instant expiresAt, Map<String, Object> metadata) {
        hash.put(name + "Value", value);
        putIfPresent(hash, name + "IssuedAt", issuedAt != null ? issuedAt.toString() : null);
        putIfPresent(hash, name + "ExpiresAt", expiresAt != null ? expiresAt.toString() : null);
        hash.put(name + "Metadata", writeMap(metadata));
    }

    private void putIfPresent(Map<String, String> hash, String key, String value) {
        if (value != null) {
            hash.put(key, value);
        }
    }

    private OAuth2Authorization toObject(Map<Object, Object> hash) {

        String registeredClientId = get(hash, "registeredClientId");
        RegisteredClient registeredClient = registeredClientRepository.findById(registeredClientId);
        if (registeredClient == null) {
            throw new DataRetrievalFailureException("The RegisteredClient with id '%s' was not found in the RegisteredClientRepository.".formatted(registeredClientId));
        }

        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .id(get(hash, "id"))
                .principalName(get(hash, "principalName"))
                .authorizationGrantType(new AuthorizationGrantType(get(hash, "authorizationGrantType")))
                .authorizedScopes(StringUtils.commaDelimitedListToSet(get(hash, "authorizedScopes")))
                .attributes(attributes -> attributes.putAll(parseMap(get(hash, "attributes"))));

        if (get(hash, "state") != null) {
            builder.attribute(OAuth2ParameterNames.STATE, get(hash, "state"));
        }

        if (get(hash, "authorizationCodeValue") != null) {
            OAuth2AuthorizationCode authorizationCode = new OAuth2AuthorizationCode(
                    get(hash, "authorizationCodeValue"), parseInstant(get(hash, "authorizationCodeIssuedAt")), parseInstant(get(hash, "authorizationCodeExpiresAt")));
            builder.token(authorizationCode, metadata -> metadata.putAll(parseMap(get(hash, "authorizationCodeMetadata"))));
        }
        if (get(hash, "accessTokenValue") != null) {
            OAuth2AccessToken accessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    get(hash, "accessTokenValue"), parseInstant(get(hash, "accessTokenIssuedAt")), parseInstant(get(hash, "accessTokenExpiresAt")),
                    StringUtils.commaDelimitedListToSet(get(hash, "accessTokenScopes")));
            builder.token(accessToken, metadata -> metadata.putAll(parseMap(get(hash, "accessTokenMetadata"))));
        }
        if (get(hash, "refreshTokenValue") != null) {
            OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                    get(hash, "refreshTokenValue"), parseInstant(get(hash, "refreshTokenIssuedAt")), parseInstant(get(hash, "refreshTokenExpiresAt")));
            builder.token(refreshToken, metadata -> metadata.putAll(parseMap(get(hash, "refreshTokenMetadata"))));
        }
        if (get(hash, "oidcIdTokenValue") != null) {
            Map<String, Object> metadataMap = parseMap(get(hash, "oidcIdTokenMetadata"));
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = (Map<String, Object>) metadataMap.get(OAuth2Authorization.Token.CLAIMS_METADATA_NAME);
            OidcIdToken oidcIdToken = new OidcIdToken(
                    get(hash, "oidcIdTokenValue"), parseInstant(get(hash, "oidcIdTokenIssuedAt")), parseInstant(get(hash, "oidcIdTokenExpiresAt")), claims);
            builder.token(oidcIdToken, metadata -> metadata.putAll(metadataMap));
        }

        return builder.build();
    }

    private String get(Map<Object, Object> hash, String key) {
        Object value = hash.get(key);
        return value != null ? value.toString() : null;
    }

    private Instant parseInstant(String value) {
        return value != null ? Instant.parse(value) : null;
    }

    private String writeMap(Map<String, Object> data) {
        try {
            return this.objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private Map<String, Object> parseMap(String data) {
        if (data == null) {
            return Map.of();
        }
        try {
            return this.objectMapper.readValue(data, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
