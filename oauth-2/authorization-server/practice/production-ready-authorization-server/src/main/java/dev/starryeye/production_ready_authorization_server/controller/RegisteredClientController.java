package dev.starryeye.production_ready_authorization_server.controller;

import dev.starryeye.production_ready_authorization_server.config.ResourceIndicatorValidatingAuthenticationProvider;
import dev.starryeye.production_ready_authorization_server.jpa.RegisteredClientEntity;
import dev.starryeye.production_ready_authorization_server.jpa.RegisteredClientEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.ArrayList;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RegisteredClientController {

    /**
     * client 사전 등록(pre-registration)용 admin API 이다. (jpa/custom-registered-client-repository 프로젝트 이식, ROLE_ADMIN 전용.. DefaultSecurityConfig 참고)
     *      clientType 프리셋(CONFIDENTIAL, PUBLIC, SERVICE)으로 등록하며 secret 은 처음부터 bcrypt 로 저장한다.
     *
     * 다중 인스턴스 관점..
     *      등록 요청을 어느 인스턴스가 처리하든 MySQL 에 저장되므로 다른 인스턴스도 즉시 같은 client 를 본다.
     */

    private final RegisteredClientEntityRepository entityRepository;
    private final RegisteredClientRepository registeredClientRepository;

    // ClientSecretAuthenticationProvider 의 기본 PasswordEncoder 와 동일한 DelegatingPasswordEncoder (encode 시 "{bcrypt}.." 로 저장됨)
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @PostMapping("/registered-clients")
    public RegisterClientResponse register(@RequestBody RegisterClientRequest request) {

        String clientId = UUID.randomUUID().toString();
        String rawClientSecret = request.clientType() == ClientType.PUBLIC ? null : UUID.randomUUID().toString();

        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientIdIssuedAt(Instant.now())
                .clientName(request.clientName()) // 커스텀 consent 화면에 표시된다. (ConsentController 참고)
                .scopes(scopes -> scopes.addAll(request.scopes()));

        TokenSettings.Builder tokenSettingsBuilder = TokenSettings.builder();
        if (request.accessTokenTimeToLiveSeconds() != null) {
            tokenSettingsBuilder.accessTokenTimeToLive(Duration.ofSeconds(request.accessTokenTimeToLiveSeconds()));
        }

        ClientSettings.Builder clientSettingsBuilder = ClientSettings.builder();

        switch (request.clientType()) {
            case CONFIDENTIAL -> builder
                    .clientSecret(passwordEncoder.encode(rawClientSecret))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .redirectUri(requiredRedirectUri(request))
                    .clientSettings(clientSettingsBuilder.requireAuthorizationConsent(request.requireAuthorizationConsentOrDefault()).build())
                    .tokenSettings(tokenSettingsBuilder.reuseRefreshTokens(false).build());
            case PUBLIC -> builder
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(requiredRedirectUri(request))
                    .clientSettings(clientSettingsBuilder
                            .requireAuthorizationConsent(request.requireAuthorizationConsentOrDefault())
                            .requireProofKey(true) // PKCE 필수화
                            .build()
                    )
                    .tokenSettings(tokenSettingsBuilder.build());
            case SERVICE -> builder
                    .clientSecret(passwordEncoder.encode(rawClientSecret))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .clientSettings(clientSettingsBuilder.build())
                    .tokenSettings(tokenSettingsBuilder.build());
            case EXCHANGE -> builder
                    .clientSecret(passwordEncoder.encode(rawClientSecret))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS) // 교환 시 actor_token 발급용 (delegation)
                    .clientSettings(clientSettingsBuilder.build())
                    .tokenSettings(tokenSettingsBuilder.build());
        }

        /**
         * client 별 허용 자원 목록(resource indicator, RFC 8707)을 ClientSettings 의 커스텀 설정으로 담는다..
         *      client 등록 정보의 일부이므로 별도 테이블 없이 JpaRegisteredClientRepository 의
         *      JSON 직렬화를 그대로 타고 DB(client_settings 컬럼)에 저장된다.
         *      등록 시점에 URI 형식(절대 URI, fragment 금지)을 검증해 나쁜 값이 저장되는 것을 막는다.
         */
        if (request.allowedResources() != null && !request.allowedResources().isEmpty()) {
            builder.clientSettings(ClientSettings.withSettings(clientSettingsBuilder.build().getSettings())
                    .setting(ResourceIndicatorValidatingAuthenticationProvider.ALLOWED_RESOURCES_SETTING, validatedResources(request.allowedResources()))
                    .build());
        }

        registeredClientRepository.save(builder.build());

        return new RegisterClientResponse(clientId, rawClientSecret, request.clientName(), request.clientType());
    }

    // RegisteredClient 로 변환된 결과 조회 (comma 문자열/JSON 이 원래 타입으로 복원된 모습)
    @GetMapping("/registered-clients")
    public List<RegisteredClient> getRegisteredClients() {
        return entityRepository.findAll().stream()
                .map(entity -> registeredClientRepository.findById(entity.getId()))
                .toList();
    }

    // entity 원문 조회 (client_settings, token_settings 의 @class 포함 JSON 문자열과 "{bcrypt}.." 로 저장된 client_secret 확인용)
    @GetMapping("/registered-clients/raw")
    public List<RegisteredClientEntity> getRegisteredClientEntities() {
        return entityRepository.findAll();
    }

    private String requiredRedirectUri(RegisterClientRequest request) {
        if (!StringUtils.hasText(request.redirectUri())) {
            throw new IllegalArgumentException("redirectUri is required for client type: " + request.clientType());
        }
        return request.redirectUri();
    }

    /**
     * 주의. 반환 타입은 역직렬화 allowlist 에 있는 ArrayList 여야 한다..
     *      List.copyOf/List.of 의 ImmutableCollections 타입으로 저장하면 @class 타입 정보가 그대로 JSON 에 남고,
     *      조회 시 SecurityJackson2Modules 의 allowlist 에 없는 타입이라 역직렬화가 거부된다. (전 client 조회가 깨진다)
     */
    private List<String> validatedResources(List<String> resources) {
        for (String resource : resources) {
            try {
                URI uri = URI.create(resource);
                if (!uri.isAbsolute() || uri.getFragment() != null) {
                    throw new IllegalArgumentException("allowed resource must be an absolute URI without fragment: " + resource);
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("invalid allowed resource: " + resource);
            }
        }
        return new ArrayList<>(resources);
    }
}
