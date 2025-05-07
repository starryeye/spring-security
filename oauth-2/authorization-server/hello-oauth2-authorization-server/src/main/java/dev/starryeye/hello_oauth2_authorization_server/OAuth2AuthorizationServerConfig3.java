package dev.starryeye.hello_oauth2_authorization_server;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

@Configuration
public class OAuth2AuthorizationServerConfig3 {

    /**
     * HelloOauth2AuthorizationServerApplication 주석에서 설명한 세번째 방법..
     *
     * authorization-server 설정에 필요한 필수적인 빈들을 직접 구성해본다.
     *
     * https://docs.spring.io/spring-authorization-server/reference/configuration-model.html
     * 위 링크를 참고하면..
     * 훨씬더 디테일한 설정을 해볼 수 있다.
     */

    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = OAuth2AuthorizationServerConfigurer.authorizationServer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher()) // authorization server 관련 endpoint 만 다루는 securityFilterChain
                .with(authorizationServerConfigurer, oAuth2AuthorizationServerConfigurer ->
                        oAuth2AuthorizationServerConfigurer
                                .oidc(Customizer.withDefaults())	// 기본적으로 oidc 는 disable 되어있어서 설정해줘야함.
                )
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .anyRequest().authenticated()
                )
                .csrf(httpSecurityCsrfConfigurer ->
                        httpSecurityCsrfConfigurer
                                .ignoringRequestMatchers(authorizationServerConfigurer.getEndpointsMatcher())
                );

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .anyRequest().authenticated()
                )
                .formLogin(Customizer.withDefaults());

        return http.build();
    }

    // application.yml 설정을 이용하여 자동 구성되도록 하지 않고.. 직접 등록함
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("my-spring-client")
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/my-spring-client")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
                .build();

        return new InMemoryRegisteredClientRepository(registeredClient);
    }

    // application.yml 설정을 이용하여 자동 구성되도록 하지 않고.. 직접 등록함
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer("http://localhost:8091") // 아래 주석은 기본 값이다.
//                .authorizationEndpoint("/oauth2/authorize")
//                .deviceAuthorizationEndpoint("/oauth2/device_authorization")
//                .deviceVerificationEndpoint("/oauth2/device_verification")
//                .tokenEndpoint("/oauth2/token")
//                .jwkSetEndpoint("/oauth2/jwks")
//                .tokenRevocationEndpoint("/oauth2/revoke")
//                .tokenIntrospectionEndpoint("/oauth2/introspect")
//                .oidcClientRegistrationEndpoint("/connect/register")
//                .oidcUserInfoEndpoint("/userinfo")
//                .oidcLogoutEndpoint("/connect/logout")
                .build();
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
