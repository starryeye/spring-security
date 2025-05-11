package dev.starryeye.oauth2_authorization_service;

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
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Configuration
public class AuthorizationServerConfig {

    /**
     * OAuth2Authorization, OAuth2AuthorizationService 에 대해 알아본다.
     *
     * OAuth2Authorization..
     *      resource owner 의 역할이 있는 권한 부여 방식 (authorization code grant, resource owner password, client credential..) 에서..
     *          authorization server 가 resource owner 의 허가를 받고 client 에게 권한을 부여(인가)할 때..
     *          authorization server 측에서 access token, refresh token, id token, authorization code 등을 권한 부여 방식에 따라 저장할 객체이다.
     *      oauth2-client 의존성에서의 OAuth2AuthorizedClient 객체와 대응된다.
     *      토큰 및 코드의 타입은 OAuth2Token 이다.
     *      동일 resource owner, 동일 client 라도 authorization server 에 생성되는 OAuth2Authorization 객체는 요청마다 생성된다.
     *
     * OAuth2AuthorizationService..
     *      authorization server 에서 OAuth2Authorization 을 저장하고 조회할 수 있는 저장소 클래스이다.
     *      spring bean 으로 등록되지 않으므로 개발자가 직접 등록하면.. 등록한 빈으로 사용되며.. DI 로 참조 가능하다. (아래코드 참조)
     *      구현체
     *          InMemoryOAuth2AuthorizationService (기본값)
     *          JdbcOAuth2AuthorizationService
     *
     * 간단 흐름.
     * authorization code grant 방식에서..
     *      1. resource owner 가 authorization server 가 제공하는 로그인 페이지를 거쳐 consent 에 승인 버튼을 누르면..
     *      code 가 생성되는 단계..
     *          OAuth2AuthorizationCodeRequestAuthenticationProvider::authenticate 에서 보면..
     *              code 생성 후, OAuth2Authorization 객체를 생성하고 code 를 담은 후.. OAuth2AuthorizationService 에 생성한 객체를 담는 것을 볼 수 있음.
     *      2. client 가 authorization code 를 포함하여 token 을 요청하는 단게..
     *          CodeVerifierAuthenticator::authenticate 에서 보면..
     *              요청 데이터에 포함된 authorization code 로 OAuth2AuthorizationService 에서 이전에 생성해놓은 OAuth2Authorization 을 조회하여 얻는 것을 볼 수 있다.
     *          또한..
     *          OAuth2AuthorizationCodeAuthenticationProvider::authenticate 에서 보면..
     *              1 단계에서 생성하고 authorization code 가 담겨있는 OAuth2Authorization 객체에
     *                  access token, id token, refresh token 을 생성해서 담고 OAuth2AuthorizationService 에 저장하는 것을 볼 수 있다.
     *                  authorization code 는 1회성이므로 metadata.invalided 속성을 true 변경한다.
     *
     *
     * todo..
     *      1. 1 번에서 consent 동의 하고 OAuth2AuthorizationCodeRequestAuthenticationProvider::authenticate 에서 ..
     *          authorization code 생성하는 break point 안잡힘..
     *      2. OAuth2AuthorizationController 에서 OAuth2Authorization 을 리턴했는데 id token 값이 응답 데이터에 없음..
     *          실제 객체에는 존재함..
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

    // application.yml 설정을 이용하여 자동 구성되도록 하지 않고.. 직접 등록함
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("my-spring-client")
                .clientSecret("{noop}secret")
                .clientIdIssuedAt(Instant.now())
                .clientSecretExpiresAt(Instant.MAX)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/my-spring-client")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("custom-scope")
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
                .build();

        return new InMemoryRegisteredClientRepository(registeredClient);
    }

    @Bean
    public OAuth2AuthorizationService authorizationService() {
        return new InMemoryOAuth2AuthorizationService();
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
