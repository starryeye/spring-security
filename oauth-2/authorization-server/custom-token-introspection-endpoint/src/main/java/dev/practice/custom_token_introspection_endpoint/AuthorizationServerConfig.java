package dev.practice.custom_token_introspection_endpoint;

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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Configuration
public class AuthorizationServerConfig {

    /**
     * OAuth2AuthorizationServerConfigurer::tokenIntrospectionEndpoint 를 이용하여
     * access token 검증 단계를 커스텀 해본다..
     *      POST "/oauth2/introspect"
     *      관련 flow 정리는 main class 에 정리해놓음..(todo)
     *
     * 주요 클래스
     * OAuth2TokenIntrospectionEndpointConfigurer
     * OAuth2TokenIntrospectionEndpointFilter
     * 		DelegatingAuthenticationConverter
     * 			OAuth2AuthorizationCodeAuthenticationConverter
     * 				OAuth2AuthorizationCodeAuthenticationToken
     * 			OAuth2RefreshTokenAuthenticationConverter
     * 				OAuth2RefreshTokenAuthenticationToken
     * 			OAuth2ClientCredentialsAuthenticationConverter
     * 				OAuth2ClientCredentialsAuthenticationToken
     * 			OAuth2DeviceCodeAuthenticationConverter
     * 		        OAuth2DeviceCodeAuthenticationToken
     * 		    OAuth2TokenExchangeAuthenticationConverter
     * 		        OAuth2TokenExchangeAuthenticationToken
     * 		ProviderManager(AuthenticationManager)
     * 			OAuth2AuthorizationCodeAuthenticationProvider
     * 		        DelegatingOAuth2TokenGenerator
     * 					JwtGenerator // JWT 토큰 생성기
     * 				        JwtEncoder // 전자서명
     * 					OAuth2AccessTokenGenerator // 단순 String 토큰 생성기
     * 					OAuth2RefreshTokenGenerator
     * 			OAuth2RefreshTokenAuthenticationProvider
     * 			OAuth2ClientCredentialsAuthenticationProvider
     * 			OAuth2AccessTokenAuthenticationToken(인증 객체)
     * 		OAuth2AccessTokenResponseAuthenticationSuccessHandler
     * 		OAuth2ErrorAuthenticationFailureHandler
     *
     * 참고..
     * introspect.http 파일 설명도 볼것.
     */

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = OAuth2AuthorizationServerConfigurer.authorizationServer();

        /**
         * OAuth2AuthorizationServerConfigurer::tokenIntrospectionEndpoint()..
         * 아래 주석은 client 가 authorization server 에서 access token 을 검증받기 위한 요청("/oauth2/introspect") endpoint 에 대한 설정을
         * 커스텀하게 할 수 있도록 제공하는 api 이다.
         */
//        authorizationServerConfigurer.tokenIntrospectionEndpoint(oAuth2TokenIntrospectionEndpointConfigurer ->
//                oAuth2TokenIntrospectionEndpointConfigurer
//                        .introspectionRequestConverter(null)
//                        .authenticationProvider(null)
//                        .introspectionResponseHandler(null)
//                        .errorResponseHandler(null)
//        );

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

        // confidential client 용 (PKCE, authorization code, client credential, refresh token 지원)
        RegisteredClient registeredClient1 = RegisteredClient.withId(UUID.randomUUID().toString())
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
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build()) // authorization code 요청시 resource owner 에게 consent 동의 화면 보여줄 것인지 설정
                .tokenSettings(TokenSettings.builder().reuseRefreshTokens(false).build()) // refresh token grant 시, refresh token 을 재사용할 것인지 설정 (기본 값 true)
                .build();

        // public client 용 (PKCE 지원)
        RegisteredClient registeredClient2 = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("my-public-client")
                .clientIdIssuedAt(Instant.now())
                .clientSecretExpiresAt(Instant.MAX)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE) // public client 용으로 client id + PKCE 로 client 인증을 위함 (client secret 사용하지않음)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN) // 주의, OAuth2RefreshTokenGenerator 에서 refresh token 을 생성할 때, public client 라면 refresh token 을 생성하지 않는 것으로 되어 있다.
                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/my-public-client")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("custom-scope")
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(true) // authorization code 요청시 resource owner 에게 consent 동의 화면 보여줄 것인지 설정
                        .requireProofKey(true) // PKCE 필수화, 요청에 code_challenge, code_challenge_method 를 반드시 포함시켜야함.
                        .build()
                )
                .tokenSettings(TokenSettings.builder().reuseRefreshTokens(false).build()) // refresh token grant 시, refresh token 을 재사용할 것인지 설정 (기본 값 true)
                .build();

        // resource server 용
        RegisteredClient registeredClient3 = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("my-spring-resource-server")
                .clientSecret("{noop}secret-resource-server")
                .clientIdIssuedAt(Instant.now())
                .clientSecretExpiresAt(Instant.MAX)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/my-spring-resource-server")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("custom-scope")
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build()) // authorization code 요청시 resource owner 에게 consent 동의 화면 보여줄 것인지 설정
                .build();

        /**
         * ClientSettings.builder(), TokenSettings.builder() 에도 다양한 설정이 존재한다.
         *      위 설정 외에도.. time to live 등..
         */

        return new InMemoryRegisteredClientRepository(List.of(registeredClient1, registeredClient2, registeredClient3));
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
