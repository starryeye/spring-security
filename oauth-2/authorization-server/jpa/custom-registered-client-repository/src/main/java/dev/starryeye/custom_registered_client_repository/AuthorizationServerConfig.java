package dev.starryeye.custom_registered_client_repository;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.starryeye.custom_registered_client_repository.jpa.JpaRegisteredClientRepository;
import dev.starryeye.custom_registered_client_repository.jpa.RegisteredClientEntityRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
import java.util.UUID;

@Configuration
public class AuthorizationServerConfig {

    /**
     * hello-jpa-authorization-server 와 동일한 구성에서 seed client 를 3개(confidential, public+PKCE, resource server 용)로 늘리고..
     * 전체 필드 매핑(ClientSettings, TokenSettings, Instant 필드 포함)이 동작하는지 확인한다.
     *
     * client secret 은 {noop} 대신 bcrypt 로 인코딩하여 저장한다. (main class 주석 참고)
     */

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = OAuth2AuthorizationServerConfigurer.authorizationServer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher()) // authorization server 에서 제공하는 기본 EndPoint 에 대한 SecurityFilterChain
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .anyRequest().authenticated()
                )
                .with(authorizationServerConfigurer, oAuth2AuthorizationServerConfigurer ->
                        oAuth2AuthorizationServerConfigurer
                                // 기본적으로 oidc 는 disable 되어있어서 설정해줘야함.
                                .oidc(Customizer.withDefaults())
                )
                .exceptionHandling(httpSecurityExceptionHandlingConfigurer ->
                        httpSecurityExceptionHandlingConfigurer
                                // 기본 authentication EntryPoint 설정
                                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
                )
        ;

        return http.build();
    }

    // application.yml 설정을 이용하여 자동 구성되도록 하지 않고.. 직접 등록함
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer("http://localhost:8091")
                .build();
    }

    // InMemoryRegisteredClientRepository 대신 JPA 구현체를 빈으로 등록한다. (hello-jpa-authorization-server 참고)
    @Bean
    public RegisteredClientRepository registeredClientRepository(RegisteredClientEntityRepository registeredClientEntityRepository) {
        return new JpaRegisteredClientRepository(registeredClientEntityRepository);
    }

    @Bean
    public CommandLineRunner registeredClientInitializer(RegisteredClientRepository registeredClientRepository) {
        return args -> {
            if (registeredClientRepository.findByClientId("my-spring-client") != null) {
                return;
            }

            /**
             * client secret 인코딩용..
             * ClientSecretAuthenticationProvider 의 기본 PasswordEncoder 와 동일한 DelegatingPasswordEncoder 이다.
             *      encode 결과는 "{bcrypt}$2a$10$..." 형태로 저장된다. ("/registered-clients/raw" 로 확인)
             */
            PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

            // confidential client 용 (authorization code, client credential, refresh token 지원)
            RegisteredClient registeredClient1 = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("my-spring-client")
                    .clientSecret(passwordEncoder.encode("secret"))
                    .clientIdIssuedAt(Instant.now())
                    // clientSecretExpiresAt(Instant.MAX) 는 MySQL datetime 범위를 벗어나 저장 불가.. 만료 없음은 설정하지 않음(null)으로 표현 (main class 주석 참고)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .redirectUri("http://127.0.0.1:8080/login/oauth2/code/my-spring-client")
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .scope("custom-scope")
                    .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build()) // authorization code 요청시 resource owner 에게 consent 동의 화면 보여줄 것인지 설정
                    .tokenSettings(
                            TokenSettings.builder()
                                    .reuseRefreshTokens(false) // refresh token grant 시, refresh token 을 재사용할 것인지 설정 (기본 값 true)
                                    .accessTokenTimeToLive(Duration.ofSeconds(120)) // 기본값 300초, JSON 왕복 후에도 반영되는지 token 응답의 expires_in 으로 확인용
                                    .build()
                    )
                    .build();

            // public client 용 (PKCE 지원)
            RegisteredClient registeredClient2 = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("my-public-client")
                    .clientIdIssuedAt(Instant.now())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE) // public client 용으로 client id + PKCE 로 client 인증을 위함 (client secret 사용하지않음)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("http://127.0.0.1:8080/login/oauth2/code/my-public-client")
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .clientSettings(ClientSettings.builder()
                            .requireAuthorizationConsent(true)
                            .requireProofKey(true) // PKCE 필수화, 요청에 code_challenge, code_challenge_method 를 반드시 포함시켜야함.
                            .build()
                    )
                    .build();

            // resource server 용 (client credentials 전용)
            RegisteredClient registeredClient3 = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("my-spring-resource-server")
                    .clientSecret(passwordEncoder.encode("secret-resource-server"))
                    .clientIdIssuedAt(Instant.now())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .scope("custom-scope")
                    .build();

            registeredClientRepository.save(registeredClient1);
            registeredClientRepository.save(registeredClient2);
            registeredClientRepository.save(registeredClient3);
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
