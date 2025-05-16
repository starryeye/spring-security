package dev.starryeye.custom_client_authentication;

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
     * OAuth2AuthorizationServerConfigurer::clientAuthentication 를 이용하여 client 인증 처리를 커스텀 할 수 있다.
     *
     * client 인증..
     * authorization server 에 client 가 access token 에 관한 요청(발급, 검증, 해지)하면,
     * 가장 먼저 수행하는 것이 client 인증이다.
     *      제공되는 client 인증 방식
     *          client_secret_basic, client_secret_post, private_key_jwt, client_secret_jwt, none 방식
     *      관련 path
     *          POST "/oauth2/token"
     *          POST "/oauth2/introspect"
     *          POST "/oauth2/revoke"
     *          POST "/oauth2/device_authorization"
     *
     * 관련 객체 및 flow
     * OAuth2ClientAuthenticationConfigurer
     * OAuth2ClientAuthenticationFilter
     *      DelegatingAuthenticationConverter
     *          ClientSecretBasicAuthenticationConverter
     *              OAuth2ClientAuthenticationToken (미 인증 객체)
     *          ClientSecretPostAuthenticationConverter
     *          JwtClientAssertionAuthenticationConverter
     *          PublicClientAuthenticationConverter
     *          X509ClientCertificateAuthenticationConverter
     *      ProviderManager(AuthenticationManager)
     *          ClientSecretAuthenticationProvider
     *              OAuth2ClientAuthenticationToken (인증 객체)
     *          JwtClientAssertionAuthenticationProvider
     *          PublicClientAuthenticationProvider
     *          X509ClientCertificateAuthenticationProvider
     *      authenticationSuccessHandler
     *      authenticationFailureHandler
     *
     * 참고
     * 실제 access token 관련 요청 필터는 SecurityFilterChain 에서 뒷 순서에 속한다.
     * 필터 중.. AuthorizationFilter 가 token 관련 요청 필터들 앞에 위치하며 client 인증을 받았는지를 체크하는듯
     */

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = OAuth2AuthorizationServerConfigurer.authorizationServer();

        /**
         * OAuth2AuthorizationServerConfigurer::clientAuthentication()..
         * client 에 대하여 authorization server 가 client_secret_basic, client_secret_post, private_key_jwt, client_secret_jwt, none 방식으로
         * 인증을 하는 과정에 대해 커스텀하게 할 수 있도록 제공하는 api 이다.
         */
//        authorizationServerConfigurer.clientAuthentication(oAuth2ClientAuthenticationConfigurer ->
//                oAuth2ClientAuthenticationConfigurer
//                        .authenticationConverter(null)
//                        .authenticationProvider(null)
//                        .authenticationSuccessHandler(null)
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
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
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
