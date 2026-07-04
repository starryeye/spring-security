package dev.starryeye.custom_registered_client_repository;

import dev.starryeye.custom_registered_client_repository.jpa.RegisteredClientEntity;
import dev.starryeye.custom_registered_client_repository.jpa.RegisteredClientEntityRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RegisteredClientController {

    /**
     * client 사전 등록(pre-registration)용 admin API 이다.
     *      client 등록 기능은 프레임워크가 제공하지 않으므로 개발자가 직접 만들어야 한다. (hello-jpa-authorization-server 의 RegisteredClientController 주석 참고)
     *
     * hello 프로젝트와의 차이..
     *      1. clientType 프리셋으로 3가지 유형의 client 를 등록할 수 있다. (CONFIDENTIAL, PUBLIC, SERVICE.. ClientType 참고)
     *      2. secret 을 "{noop}" 이 아니라 처음부터 bcrypt 로 인코딩하여 저장한다.
     *          운영에서의 올바른 방식이며, 이미 인코딩 방식이 최신이므로 secret 인코딩 upgrade(save)도 일어나지 않는다.
     *      3. accessTokenTimeToLiveSeconds 로 TokenSettings 를 등록 시점에 지정해볼 수 있다. (JSON 직렬화 왕복 확인용)
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
                .clientName(request.clientName())
                .scopes(scopes -> scopes.addAll(request.scopes()));

        TokenSettings.Builder tokenSettingsBuilder = TokenSettings.builder();
        if (request.accessTokenTimeToLiveSeconds() != null) {
            tokenSettingsBuilder.accessTokenTimeToLive(Duration.ofSeconds(request.accessTokenTimeToLiveSeconds()));
        }

        switch (request.clientType()) {
            case CONFIDENTIAL -> builder
                    .clientSecret(passwordEncoder.encode(rawClientSecret))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .redirectUri(requiredRedirectUri(request))
                    .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
                    .tokenSettings(tokenSettingsBuilder.reuseRefreshTokens(false).build());
            case PUBLIC -> builder
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(requiredRedirectUri(request))
                    .clientSettings(ClientSettings.builder()
                            .requireAuthorizationConsent(true)
                            .requireProofKey(true) // PKCE 필수화
                            .build()
                    )
                    .tokenSettings(tokenSettingsBuilder.build());
            case SERVICE -> builder
                    .clientSecret(passwordEncoder.encode(rawClientSecret))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .tokenSettings(tokenSettingsBuilder.build());
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
}
