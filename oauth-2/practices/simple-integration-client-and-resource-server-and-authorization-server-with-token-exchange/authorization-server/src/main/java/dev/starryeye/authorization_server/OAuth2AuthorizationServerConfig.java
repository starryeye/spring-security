package dev.starryeye.authorization_server;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenExchangeAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenExchangeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Configuration
public class OAuth2AuthorizationServerConfig {

    /**
     * resource indicator (RFC 8707) 로 "누가 어떤 자원용 토큰을 받을 수 있는가" 를 관리한다..
     *      이 서버가 아는 자원 카탈로그이자 client 별 허용 목록. 여기 없는 resource 요청은 invalid_target 으로 거부된다.
     */
    private static final Map<String, Set<String>> ALLOWED_RESOURCES_BY_CLIENT = Map.of(
            "my-spring-client", Set.of("http://localhost:8081"),  // 사용자 client 는 article 용 토큰까지만
            "my-article-client", Set.of("http://localhost:8082")  // article 은 교환으로 comment 용 토큰까지만
    );

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RegisteredClientRepository registeredClientRepository) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = OAuth2AuthorizationServerConfigurer.authorizationServer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .anyRequest().authenticated()
                )
                .with(authorizationServerConfigurer, oAuth2AuthorizationServerConfigurer ->
                        oAuth2AuthorizationServerConfigurer
                                // resource 파라미터의 발급 측 검증(invalid_target).. authorize 단계와 token(exchange) 단계 양쪽에 배선한다.
                                .authorizationEndpoint(authorizationEndpointConfigurer ->
                                        authorizationEndpointConfigurer
                                                .authenticationProviders(authenticationProviders ->
                                                        authenticationProviders.replaceAll(authenticationProvider ->
                                                                authenticationProvider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider
                                                                        ? new ResourceIndicatorValidatingAuthenticationProvider(authenticationProvider, registeredClientRepository, ALLOWED_RESOURCES_BY_CLIENT)
                                                                        : authenticationProvider
                                                        )
                                                )
                                )
                                .tokenEndpoint(tokenEndpointConfigurer ->
                                        tokenEndpointConfigurer
                                                .authenticationProviders(authenticationProviders ->
                                                        authenticationProviders.replaceAll(authenticationProvider ->
                                                                authenticationProvider instanceof OAuth2TokenExchangeAuthenticationProvider
                                                                        ? new ResourceIndicatorValidatingAuthenticationProvider(authenticationProvider, registeredClientRepository, ALLOWED_RESOURCES_BY_CLIENT)
                                                                        : authenticationProvider
                                                        )
                                                )
                                )
                                .oidc(Customizer.withDefaults())
                )
                .exceptionHandling(httpSecurityExceptionHandlingConfigurer ->
                        httpSecurityExceptionHandlingConfigurer
                                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
                )
        ;

        return http.build();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer("http://localhost:8091")
                .build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository() {

        RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("my-spring-client")
                .clientSecret("{noop}secret")
                .clientIdIssuedAt(Instant.now())
                .clientSecretExpiresAt(Instant.MAX)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/my-spring-client")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("custom-scope")
                .scope("content") // 사용자 토큰은 article 호출까지만.. comment scope 는 없다 (relay 버전과의 차이, README 참고)
                .clientSettings(
                        ClientSettings.builder()
                                .requireAuthorizationConsent(true)
                                .build()
                )
                .tokenSettings(
                        TokenSettings.builder()
                                .reuseRefreshTokens(false)
                                .accessTokenTimeToLive(Duration.ofSeconds(60L))
                                .build()
                )
                .build();

        /**
         * article 서버가 comment 서버를 호출하기 위해 client 로 나서는 신원이다.. (relay 버전에는 없던 client)
         *      TOKEN_EXCHANGE : 사용자 토큰(subject)을 comment 용 토큰으로 교환
         *      CLIENT_CREDENTIALS : 교환 시 actor_token 으로 제출할 자기 토큰 발급 (act claim 을 남기는 delegation)
         *      등록 scope 를 comment 하나로 제한.. 교환 요청 scope 의 상한이 등록 scope 라서 (grant/token-exchange 프로젝트의 scope 결정 규칙)
         *      article 이 무엇을 요청하든 comment 를 넘는 권한은 구조적으로 못 받는다.
         */
        RegisteredClient articleClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("my-article-client")
                .clientSecret("{noop}article-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("comment")
                .tokenSettings(
                        TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofSeconds(60L))
                                .build()
                )
                .build();

        return new InMemoryRegisteredClientRepository(registeredClient, articleClient);
    }

    /**
     * client 가 요청한 resource indicator (RFC 8707) 를 access token 의 aud 에 반영한다.
     *
     * 기본 동작.. JwtGenerator 는 요청 파라미터와 무관하게 "요청을 인증한 client 의 client_id" 하나를 aud 로 넣는다.
     *      즉 aud 는 토큰의 소유 client 를 나타낼 뿐, 어느 resource server 용인지는 담기지 않는다.
     *      RFC 8707 의 resource 파라미터도, RFC 8693 의 resource/audience 파라미터도 발급(JwtGenerator)에는 반영되지 않는다.
     *      -> resource server 가 "이 토큰이 나를 위한 것인가"(aud 검증)를 하려면 이 customizer 확장이 필요하다.
     *
     * 분기.. (요청 resource 는 ResourceIndicatorValidatingAuthenticationProvider 가 이미 검증한 값이다)
     *      authorization code + refresh token : authorize 요청의 resource 파라미터를 반영..
     *          파라미터는 authorization 에 저장된 OAuth2AuthorizationRequest(additionalParameters)에 남아 있어서
     *          refresh 재발급 시에도 같은 곳에서 읽힌다.
     *      token exchange : 교환 요청의 resource 파라미터(OAuth2TokenExchangeAuthenticationToken.getResources())를 반영
     *      (client_credentials 등 나머지는 기본 aud 그대로)
     *
     * 주의. refresh token grant 를 분기에 포함해야 한다..
     *      access token 이 만료되면 client 가 refresh 로 재발급받는데, 재발급 토큰도 이 customizer 를 다시 통과한다.
     *      code grant 만 분기하면 갱신된 사용자 토큰에서 aud 가 기본값(client_id)으로 되돌아가 resource server 의 aud 검증(401)에 걸린다.
     *
     * 참고. client 요청 주도(이 방식, RFC 8707) 말고 "서버 정책 주도" 유파도 정당하다..
     *      client 가 요청하지 않아도 서버가 client 별 대상을 정책으로 고정 부여하는 방식 (keycloak 의 audience mapper 가 이 방식).
     *      대상이 고정된 토폴로지에서는 client 를 신뢰할 필요가 없어 더 단순하다.
     *      RFC 8707 은 client 가 상황별로 대상을 골라야 할 때(자원이 여럿) 필요한 "요청하는 표준 방법" 이다.
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
        return context -> {

            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }

            String clientId = context.getRegisteredClient().getClientId();
            List<String> requestedResources = new ArrayList<>();

            if (context.getAuthorizationGrant() instanceof OAuth2TokenExchangeAuthenticationToken exchangeToken) {
                requestedResources.addAll(exchangeToken.getResources());
            } else if (AuthorizationGrantType.AUTHORIZATION_CODE.equals(context.getAuthorizationGrantType())
                    || AuthorizationGrantType.REFRESH_TOKEN.equals(context.getAuthorizationGrantType())) {
                OAuth2AuthorizationRequest authorizationRequest = context.getAuthorization()
                        .getAttribute(OAuth2AuthorizationRequest.class.getName());
                if (authorizationRequest != null) {
                    Object resource = authorizationRequest.getAdditionalParameters().get("resource");
                    if (resource instanceof String value) {
                        requestedResources.add(value);
                    } else if (resource instanceof String[] values) { // resource 는 반복 지정이 허용된다 (RFC 8707)
                        requestedResources.addAll(List.of(values));
                    }
                }
            }

            if (!requestedResources.isEmpty()) {
                List<String> audience = new ArrayList<>(List.of(clientId));
                audience.addAll(requestedResources);
                context.getClaims().audience(audience); // 기본 aud(client_id)를 유지하며 요청된 대상을 더한다.
            }
        };
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = generateRsa();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    private RSAKey generateRsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
