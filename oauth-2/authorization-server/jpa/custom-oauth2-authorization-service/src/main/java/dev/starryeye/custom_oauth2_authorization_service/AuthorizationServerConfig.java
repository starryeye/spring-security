package dev.starryeye.custom_oauth2_authorization_service;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.starryeye.custom_oauth2_authorization_service.jpa.JpaOAuth2AuthorizationService;
import dev.starryeye.custom_oauth2_authorization_service.jpa.OAuth2AuthorizationEntityRepository;
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
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
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
import java.util.UUID;

@Configuration
public class AuthorizationServerConfig {

    /**
     * 이 프로젝트의 주제는 OAuth2AuthorizationService 하나이다.
     *      OAuth2AuthorizationService 를 빈으로 등록하면 기본값 InMemoryOAuth2AuthorizationService 대신 사용된다.
     *          (OAuth2ConfigurerUtils::getAuthorizationService 의 getOptionalBean.. hello-jpa-authorization-server 주석 참고)
     *      여기서는 JPA 구현체를 로깅 데코레이터로 감싸 등록하여, 영속화와 함께 호출 시퀀스도 관찰한다.
     *
     * RegisteredClientRepository 는 이 프로젝트의 주제가 아니므로..
     *      기존 프로젝트들처럼 InMemory + 고정 client 로 등록한다. (client 등록 정보의 영속화는 custom-registered-client-repository 프로젝트 참고)
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
     * InMemoryOAuth2AuthorizationService 대신 JPA 구현체를 빈으로 등록한다.
     *      로깅 데코레이터(LoggingOAuth2AuthorizationService)로 감싸서 프레임워크의 호출 시퀀스를 관찰한다.
     */
    @Bean
    public OAuth2AuthorizationService oAuth2AuthorizationService(
            OAuth2AuthorizationEntityRepository oAuth2AuthorizationEntityRepository,
            RegisteredClientRepository registeredClientRepository
    ) {
        return new LoggingOAuth2AuthorizationService(
                new JpaOAuth2AuthorizationService(oAuth2AuthorizationEntityRepository, registeredClientRepository)
        );
    }

    /**
     * 주제 격리를 위해 기존 프로젝트들과 동일하게 InMemory + 고정 client 로 등록한다.
     *
     * 주의. 단, withId 는 기존 프로젝트들처럼 UUID.randomUUID() 로 하면 안되고 고정값이어야 한다.
     *      OAuth2Authorization 은 RegisteredClient.id 로 client 참조를 유지하는데.. (oauth2_authorization 테이블의 registered_client_id 컬럼)
     *      랜덤 id 로 등록하면 재기동마다 id 가 바뀌어서, 재기동 전에 DB 에 저장된 OAuth2Authorization 을 복원할 때
     *      DataRetrievalFailureException (RegisteredClient not found) 이 발생한다. (JpaOAuth2AuthorizationService::toObject)
     *      -> 토큰 상태를 영속화하면 client 등록 정보도 안정적인 식별자로 함께 관리되어야 한다는 뜻이고..
     *         실제로는 두 저장소(RegisteredClientRepository, OAuth2AuthorizationService)를 함께 영속화해야 하는 이유가 된다.
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient registeredClient = RegisteredClient.withId("my-spring-client-registration-id") // 고정값 (위 주석 참고)
                .clientId("my-spring-client")
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/my-spring-client")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("custom-scope")
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
                .tokenSettings(TokenSettings.builder().reuseRefreshTokens(false).build()) // refresh token grant 시 새 refresh token 발급.. save 로 갱신되는 것 관찰용
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
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
