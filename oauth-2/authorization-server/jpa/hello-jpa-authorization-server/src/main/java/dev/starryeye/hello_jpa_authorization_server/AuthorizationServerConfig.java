package dev.starryeye.hello_jpa_authorization_server;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.starryeye.hello_jpa_authorization_server.jpa.JpaRegisteredClientRepository;
import dev.starryeye.hello_jpa_authorization_server.jpa.RegisteredClientEntityRepository;
import org.springframework.boot.CommandLineRunner;
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
import java.util.UUID;

@Configuration
public class AuthorizationServerConfig {

    /**
     * hello-oauth2-authorization-server-2 와 동일한 구성에서..
     * RegisteredClientRepository 빈만 InMemoryRegisteredClientRepository 대신 JPA 구현체로 교체했다.
     *
     * OAuth2Authorization, OAuth2AuthorizationConsent 저장소는 빈으로 등록하지 않았으므로..
     *      OAuth2ConfigurerUtils 에서 InMemory 구현체가 기본값으로 사용된다. (main class 주석 참고)
     *      -> code, token, consent 는 여전히 메모리에 있어서 재기동하면 사라진다. client 등록 정보만 영속화된 상태.
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
     * InMemoryRegisteredClientRepository 대신 JPA 구현체를 빈으로 등록한다.
     *      OAuth2ConfigurerUtils::getRegisteredClientRepository 가 getBean 으로 이 빈을 가져가서 사용한다.
     *      즉, 인터페이스 구현체를 빈으로 등록하는 것 외에 프레임워크에 알려줄 것이 없다.
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(RegisteredClientEntityRepository registeredClientEntityRepository) {
        return new JpaRegisteredClientRepository(registeredClientEntityRepository);
    }

    /**
     * InMemoryRegisteredClientRepository 를 쓸 때는 생성자에 RegisteredClient 를 담아 등록했지만..
     * DB 저장소에서는 없을 때 한번만 저장해주면 재기동해도 유지된다. (admin 기능으로 client 를 등록하는 상황에 해당)
     */
    @Bean
    public CommandLineRunner registeredClientInitializer(RegisteredClientRepository registeredClientRepository) {
        return args -> {
            if (registeredClientRepository.findByClientId("my-spring-client") != null) {
                return;
            }

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

            registeredClientRepository.save(registeredClient);
        };
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
