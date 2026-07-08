package dev.starryeye.custom_oauth2_authorization_consent_service;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.starryeye.custom_oauth2_authorization_consent_service.jpa.JpaOAuth2AuthorizationConsentService;
import dev.starryeye.custom_oauth2_authorization_consent_service.jpa.OAuth2AuthorizationConsentEntityRepository;
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
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class AuthorizationServerConfig {

    /**
     * 이 프로젝트의 주제는 OAuth2AuthorizationConsentService 하나이다.
     *      OAuth2AuthorizationConsentService 를 빈으로 등록하면 기본값 InMemoryOAuth2AuthorizationConsentService 대신 사용된다.
     *          (OAuth2ConfigurerUtils::getAuthorizationConsentService 의 getOptionalBean.. hello-jpa-authorization-server 주석 참고)
     *      여기서는 JPA 구현체를 로깅 데코레이터로 감싸 등록하여, 영속화와 함께 호출 시퀀스도 관찰한다.
     *
     * 주제가 아닌 저장소들은 기본값/InMemory 그대로 둔다.
     *      RegisteredClientRepository : InMemory + 고정 client (영속화는 custom-registered-client-repository 프로젝트 참고)
     *      OAuth2AuthorizationService : 기본값 InMemory (영속화는 custom-oauth2-authorization-service 프로젝트 참고)
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

    /**
     * InMemoryOAuth2AuthorizationConsentService 대신 JPA 구현체를 빈으로 등록한다.
     *      로깅 데코레이터(LoggingOAuth2AuthorizationConsentService)로 감싸서 프레임워크의 호출 시퀀스를 관찰한다.
     */
    @Bean
    public OAuth2AuthorizationConsentService oAuth2AuthorizationConsentService(
            OAuth2AuthorizationConsentEntityRepository oAuth2AuthorizationConsentEntityRepository
    ) {
        return new LoggingOAuth2AuthorizationConsentService(
                new JpaOAuth2AuthorizationConsentService(oAuth2AuthorizationConsentEntityRepository)
        );
    }

    /**
     * 주제 격리를 위해 기존 프로젝트들과 동일하게 InMemory + 고정 client 로 등록한다.
     *
     * 주의. withId 는 고정값이어야 한다. (custom-oauth2-authorization-service 프로젝트에서 발견한 함정과 동일 원인)
     *      동의 기록은 (registeredClientId + principalName) 복합키로 저장되는데..
     *      랜덤 id 로 등록하면 재기동마다 id 가 바뀌어 기승인 기록을 못 찾게 되고, consent 화면이 다시 뜬다.
     *      3번 프로젝트처럼 예외가 나는 것도 아니라서.. 영속화가 조용히 무력화되는 더 은밀한 함정이다.
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient registeredClient = RegisteredClient.withId("my-spring-client-registration-id") // 고정값 (위 주석 참고)
                .clientId("my-spring-client")
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/my-spring-client")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("custom-scope")
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build()) // consent 가 이 프로젝트의 주제이므로 반드시 true
                .build();

        return new InMemoryRegisteredClientRepository(registeredClient);
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
                    .keyID(java.util.UUID.randomUUID().toString())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
