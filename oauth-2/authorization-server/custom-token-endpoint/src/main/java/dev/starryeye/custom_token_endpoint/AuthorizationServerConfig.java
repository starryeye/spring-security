package dev.starryeye.custom_token_endpoint;

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
import java.util.UUID;

@Configuration
public class AuthorizationServerConfig {

    /**
     * OAuth2AuthorizationServerConfigurer::tokenEndpoint 를 이용하여
     * access token 발급 단계를 커스텀 해본다..
     *      POST "/oauth2/token"
     *      authorization_code
     *      refresh_token
     *      client_credential
     *      관련 flow 정리는 main class 에 정리해놓음..(todo)
     *
     * 주요 클래스
     * OAuth2TokenEndpointConfigurer
     * OAuth2TokenEndpointFilter
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
     * POST "/oauth2/token" 요청이 오면 무작정 토큰 발행부터 하지 않고..
     * 먼저 client 인증 절차를 수행한다.
     *      client 인증 필터 : OAuth2ClientAuthenticationFilter
     *      client 인증 여부 체크 필터 : AuthorizationFilter
     * OAuth2ClientAuthenticationFilter 를 거쳐서 인증을 수행하고
     * AuthorizationFilter 필터를 통과해야 토큰 관련 처리를 하는 Filter 목록(OAuth2TokenEndpointFilter 포함)의 순서가 온다.
     */

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = OAuth2AuthorizationServerConfigurer.authorizationServer();

        /**
         * OAuth2AuthorizationServerConfigurer::tokenEndpoint()..
         * 아래 주석은 client 가 authorization server 에서 authorization code 를 얻기 위한 요청("/oauth2/authorize") endpoint 에 대한 설정을
         * 커스텀하게 할 수 있도록 제공하는 api 이다.
         */
//        authorizationServerConfigurer.tokenEndpoint(oAuth2TokenEndpointConfigurer ->
//                oAuth2TokenEndpointConfigurer
//                        .accessTokenRequestConverter(null)
//                        .authenticationProvider(null)
//                        .accessTokenResponseHandler(null)
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
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build()) // authorization code 요청시 resource owner 에게 consent 동의 화면 보여줄 것인지 설정
                .tokenSettings(TokenSettings.builder().reuseRefreshTokens(false).build()) // refresh token grant 시, refresh token 을 재사용할 것인지 설정 (기본 값 true)
                .build();

        /**
         * ClientSettings.builder(), TokenSettings.builder() 에도 다양한 설정이 존재한다.
         *      위 설정 외에도.. time to live 등..
         */

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
