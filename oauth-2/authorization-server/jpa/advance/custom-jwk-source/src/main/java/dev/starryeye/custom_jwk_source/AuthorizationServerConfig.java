package dev.starryeye.custom_jwk_source;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
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
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.io.InputStream;
import java.security.KeyStore;
import java.util.List;
import java.util.UUID;

@Configuration
public class AuthorizationServerConfig {

    /**
     * 이 프로젝트의 주제는 JWKSource 하나이다. (main class 주석 참고)
     *      기존 보일러플레이트의 "부팅 시 RSA 생성" 을 keystore 파일 로드로 교체하고, 이전 키 공개키를 함께 노출(로테이션)한다.
     *      저장소들은 주제가 아니므로 기존 프로젝트들처럼 InMemory/기본값 그대로 둔다.
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
     * keystore(PKCS12) 파일에서 키를 로드하여 JWKSource 를 구성한다.
     *      기존 방식(부팅 시 RSA 생성)과 달리 재기동/다중 인스턴스에서도 같은 키가 유지된다.
     *
     *      현재 키 : 개인키 포함으로 로드.. 서명에 사용된다.
     *      이전 키 : toPublicJWK() 로 공개키만 담는다.. 이전 키로 서명된 (만료 전) 토큰의 검증용으로 JWKS 에 노출만 한다.
     *
     * 참고. RSAKey.load 는 keystore alias 를 kid(keyID) 로 사용한다.
     *      -> "/oauth2/jwks" 응답에서 kid 가 alias(key-2026-07-08 등)로 보이고, 재기동해도 변하지 않는다.
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(
            @Value("${my.jwk.key-store-location}") Resource keyStoreLocation,
            @Value("${my.jwk.key-store-password}") String keyStorePassword,
            @Value("${my.jwk.key-password}") String keyPassword,
            @Value("${my.jwk.current-key-alias}") String currentKeyAlias,
            @Value("${my.jwk.previous-key-alias}") String previousKeyAlias
    ) throws Exception {

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream inputStream = keyStoreLocation.getInputStream()) {
            keyStore.load(inputStream, keyStorePassword.toCharArray());
        }

        RSAKey currentKey = RSAKey.load(keyStore, currentKeyAlias, keyPassword.toCharArray());
        RSAKey previousKey = RSAKey.load(keyStore, previousKeyAlias, keyPassword.toCharArray()).toPublicJWK();

        return new ImmutableJWKSet<>(new JWKSet(List.of(currentKey, previousKey)));
    }

    /**
     * JWT 발행 시 JWS 헤더에 현재 키의 kid 를 지정한다.
     *      JWKSource 에 키가 2개(현재 + 이전) 있으므로..
     *      kid 로 특정하지 않으면 NimbusJwtEncoder::selectJwk 에서 매칭 키가 2개라서 JwtEncodingException 이 발생한다.
     *      OAuth2TokenCustomizer<JwtEncodingContext> 빈은 OAuth2ConfigurerUtils::getJwtCustomizer 가 가져가 JwtGenerator 에 적용된다.
     *          access token, id token 모두 JwtGenerator 로 생성되므로 둘 다 현재 키로 서명된다.
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer(@Value("${my.jwk.current-key-alias}") String currentKeyAlias) {
        return context -> context.getJwsHeader().keyId(currentKeyAlias);
    }

    // 주제 격리를 위해 기존 프로젝트들과 동일한 InMemory + 고정 client (application.yml 자동 구성 대신 직접 등록)
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
                .scope("custom-scope")
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
                .build();

        return new InMemoryRegisteredClientRepository(registeredClient);
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }
}
