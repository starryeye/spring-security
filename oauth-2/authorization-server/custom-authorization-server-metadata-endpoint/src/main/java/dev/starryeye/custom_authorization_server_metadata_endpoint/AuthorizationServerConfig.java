package dev.starryeye.custom_authorization_server_metadata_endpoint;

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
     * OAuth2AuthorizationServerConfigurer::authorizationServerMetadataEndpoint 를 이용하여
     * authorization server metadata endpoint 를 커스텀해본다.
     *      GET "/.well-known/oauth-authorization-server"
     *
     * 주요 클래스
     * OAuth2AuthorizationServerMetadataEndpointConfigurer
     * OAuth2AuthorizationServerMetadataEndpointFilter
     *      AuthorizationServerSettings 를 참조하여 OAuth2AuthorizationServerMetadata 를 생성 후 응답
     * 	    	OAuth2AuthorizationServerMetadata 생성 시, 개발자가 OAuth2AuthorizationServerConfigurer::authorizationServerMetadataEndpoint() 를 통해 설정한 값으로 덮어쓰기도 함.
     *
     * 주의 사항..
     * OAuth2AuthorizationServerMetadataEndpointFilter 에서..
     * OAuth2AuthorizationServerMetadata 생성할 때, RegisteredClientRepository 를 참조하지는 않기 때문에..
     * 현재 제공되는 기능과 1:1 매칭이 안되는듯..
     *
     * 참고
     * GET "/oauth2/jwks" 는..
     * public key 를 제공하는 api 로..
     * NimbusJwkSetEndpointFilter 에서 처리한다.
     *
     * 참고.
     * NimbusJwkSetEndpointFilter..
     *      JWKSource<SecurityContext> 가 bean 으로 등록되어 있어야 작동한다.
     */

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = OAuth2AuthorizationServerConfigurer.authorizationServer();

        /**
         * OAuth2AuthorizationServerConfigurer::authorizationServerMetadataEndpoint()..
         * authorization server 메타데이터를 볼 수 있는 요청("/.well-known/oauth-authorization-server") endpoint 에 대한 설정을
         * 커스텀하게 할 수 있도록 제공하는 api 이다.
         */
//        authorizationServerConfigurer.authorizationServerMetadataEndpoint(oAuth2AuthorizationServerMetadataEndpointConfigurer ->
//                oAuth2AuthorizationServerMetadataEndpointConfigurer
//                        .authorizationServerMetadataCustomizer(oAuth2AuthorizationServerMetadataBuilder ->
//                                oAuth2AuthorizationServerMetadataBuilder
//                                        .authorizationEndpoint("")
//                                        .claim("", new Object())
//                                        .clientRegistrationEndpoint("")
//                                        .codeChallengeMethod("")
//                                        .deviceAuthorizationEndpoint("")
//                                        .grantType("")
//                                        .issuer("")
//                                        .jwkSetUrl("")
//                                        .responseType("")
//                                        .scope("")
//                                        .tlsClientCertificateBoundAccessTokens(false)
//                                        .tokenEndpoint("")
//                                        .tokenEndpointAuthenticationMethod("")
//                                        .tokenIntrospectionEndpoint("")
//                                        .tokenRevocationEndpoint("")
//                                        .tokenRevocationEndpointAuthenticationMethod("")
//                        )
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
