package dev.starryeye.custom_registered_client_repository;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.starryeye.custom_registered_client_repository.jpa.JpaRegisteredClientRepository;
import dev.starryeye.custom_registered_client_repository.jpa.RegisteredClientEntityRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
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
     * hello-jpa-authorization-server 와 동일한 구성이다.
     * client 는 등록 API (RegisteredClientController) 로 3가지 유형(CONFIDENTIAL, PUBLIC, SERVICE)을 등록하며..
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

    /**
     * InMemoryRegisteredClientRepository 대신 JPA 구현체를 빈으로 등록한다. (hello-jpa-authorization-server 참고)
     *
     * 참고.
     * clientSecretExpiresAt(Instant.MAX) 는 MySQL datetime 범위를 벗어나 저장 불가.. 만료 없음은 설정하지 않음(null)으로 표현 (main class 주석 참고)
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(RegisteredClientEntityRepository registeredClientEntityRepository) {
        return new JpaRegisteredClientRepository(registeredClientEntityRepository);
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
