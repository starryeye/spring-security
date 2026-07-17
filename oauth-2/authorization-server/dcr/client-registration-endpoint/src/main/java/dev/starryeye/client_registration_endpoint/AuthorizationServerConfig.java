package dev.starryeye.client_registration_endpoint;

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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
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
     * 이 프로젝트의 주제는 client registration endpoint 활성화 두 줄이다. (main class 주석 참고)
     *      1. oidc 설정의 clientRegistrationEndpoint (기본 비활성)
     *      2. 같은 체인의 oauth2ResourceServer(jwt).. 등록 요청의 Bearer(initial access token) 인증용
     *      저장소는 주제 격리를 위해 InMemory 그대로 둔다. (InMemoryRegisteredClientRepository 도 save 를 지원하므로 동적 등록이 동작한다)
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
                                .oidc(oidcConfigurer ->
                                        oidcConfigurer // 기본적으로 oidc 는 disable 되어있어서 설정해줘야함.
                                                /**
                                                 * dynamic client registration 엔드포인트 활성화.. (기본 비활성)
                                                 * POST "/connect/register" (등록), GET "/connect/register?client_id=" (조회) 가 열린다.
                                                 * 등록은 scope "client.create", 조회는 scope "client.read" 의 access token 을 요구한다.
                                                 */
                                                .clientRegistrationEndpoint(Customizer.withDefaults())
                                )
                )
                /**
                 * 등록/조회 요청이 Bearer 로 싣는 access token 을 이 서버가 직접 검증해야 한다..
                 * authorization server 가 자기가 발행한 JWT 의 resource server 역할을 겸하는 설정이다.
                 * (이 설정이 없으면 "/connect/register" 요청이 Bearer 를 인증하지 못해 로그인 페이지로 밀려난다)
                 */
                .oauth2ResourceServer(oAuth2ResourceServerConfigurer ->
                        oAuth2ResourceServerConfigurer
                                .jwt(Customizer.withDefaults())
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
     * 시드로 등록하는 것은 "등록할 자격" 을 증명할 registrar client 하나뿐이다..
     *      scope client.create 로 client_credentials grant 를 수행해 initial access token 을 얻는 용도.
     *      일반 client 들은 전부 이 프로젝트의 주제인 동적 등록으로 만들어진다.
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient registrarClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("my-registrar-client")
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("client.create") // 등록 엔드포인트가 요구하는 scope
                .build();

        return new InMemoryRegisteredClientRepository(registrarClient);
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
