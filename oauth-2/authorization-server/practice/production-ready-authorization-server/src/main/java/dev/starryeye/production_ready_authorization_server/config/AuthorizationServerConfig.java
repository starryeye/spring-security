package dev.starryeye.production_ready_authorization_server.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.starryeye.production_ready_authorization_server.jpa.JpaOAuth2AuthorizationConsentService;
import dev.starryeye.production_ready_authorization_server.jpa.JpaRegisteredClientRepository;
import dev.starryeye.production_ready_authorization_server.jpa.OAuth2AuthorizationConsentEntityRepository;
import dev.starryeye.production_ready_authorization_server.jpa.RegisteredClientEntityRepository;
import dev.starryeye.production_ready_authorization_server.redis.RedisOAuth2AuthorizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.io.InputStream;
import java.security.KeyStore;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class AuthorizationServerConfig {

    /**
     * 학습한 조각들을 조합한 authorization server 설정이다. (조합 개요는 main class 주석 참고)
     *      각 빈의 상세 설명은 출처 프로젝트에 있으므로 여기서는 조합 시 유의점만 주석으로 남긴다.
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
                                .authorizationEndpoint(authorizationEndpointConfigurer ->
                                        authorizationEndpointConfigurer
                                                // 커스텀 consent 페이지 (custom-login-and-consent-page 프로젝트 참고)
                                                .consentPage("/oauth2/consent")
                                )
                                // 기본적으로 oidc 는 disable 되어있어서 설정해줘야함.
                                .oidc(Customizer.withDefaults())
                )
                .exceptionHandling(httpSecurityExceptionHandlingConfigurer ->
                        httpSecurityExceptionHandlingConfigurer
                                // 미인증 시 커스텀 로그인 페이지로 보낸다. (DefaultSecurityConfig 의 loginPage 설정과 짝)
                                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
                )
        ;

        return http.build();
    }

    /**
     * issuer 를 로드밸런서 주소로 고정한다.
     *      미설정 시 요청 기반으로 유도되는데(etc/forwarded-header-filter 프로젝트 참고)..
     *      운영에서는 인스턴스가 무엇이든 토큰의 iss 와 메타데이터 URL 이 항상 대표 주소여야 하므로 고정이 자연스럽다.
     *      설정값(my.issuer)으로 뺀 것은 환경마다 대표 주소가 다르기 때문이다. (기본값은 로컬 LB 주소..
     *      docker 컨테이너가 접근해야 하는 환경이라면 host.docker.internal 주소로 오버라이드하여 기동한다. openid-conformance/README.md 참고)
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings(@Value("${my.issuer}") String issuer) {
        return AuthorizationServerSettings.builder()
                .issuer(issuer)
                .build();
    }

    /**
     * client 등록 정보는 JPA/MySQL 에 저장한다. (jpa/custom-registered-client-repository 프로젝트 이식)
     *      다중 인스턴스 필수 조건.. 어떤 인스턴스가 받아도 같은 client 목록을 봐야 한다.
     *      등록은 RegisteredClientController 의 admin API 로 한다. (seed 없음)
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(RegisteredClientEntityRepository repository) {
        return new JpaRegisteredClientRepository(repository);
    }

    /**
     * 토큰 상태(OAuth2Authorization)는 Redis 에 저장한다. (jpa/advance/custom-redis-oauth2-authorization-service 프로젝트 이식)
     *      토큰 수명만큼만 살면 되는 회전 데이터라 TTL 자동 만료가 어울리고..
     *      JPA 저장소였다면 필요했을 만료 데이터 purge 배치(jpa/advance/oauth2-authorization-purge)가 통째로 불필요해진다.
     */
    @Bean
    public OAuth2AuthorizationService oAuth2AuthorizationService(
            StringRedisTemplate stringRedisTemplate,
            RegisteredClientRepository registeredClientRepository
    ) {
        return new RedisOAuth2AuthorizationService(stringRedisTemplate, registeredClientRepository);
    }

    /**
     * 동의 기록은 JPA/MySQL 에 저장한다. (jpa/custom-oauth2-authorization-consent-service 프로젝트 이식)
     *      토큰과 달리 만료 개념이 없는 내구성 데이터.. 유지되어야 재인가 때 동의를 다시 묻지 않는다.
     *      빈으로 등록해야 ConsentController 가 DI 로 기승인 scope 를 조회할 수 있다.
     */
    @Bean
    public OAuth2AuthorizationConsentService oAuth2AuthorizationConsentService(OAuth2AuthorizationConsentEntityRepository repository) {
        return new JpaOAuth2AuthorizationConsentService(repository);
    }

    /**
     * keystore(PKCS12) 파일에서 서명 키를 로드한다. (jpa/advance/custom-jwk-source 프로젝트 이식)
     *      다중 인스턴스 필수 조건.. 부팅 시 RSA 생성이면 인스턴스마다 키가 달라서
     *      8091 이 서명한 토큰을 8092 의 "/oauth2/jwks" 로 검증할 수 없게 된다.
     *      두 인스턴스가 같은 keystore 파일을 로드하므로 같은 키(kid = alias)를 노출한다.
     *      이전 키는 공개키만 노출하여 로테이션 직후의 미만료 토큰 검증을 지원한다.
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
     * 두 프로젝트의 customizer 를 하나로 합쳤다..
     *      kid 지정 : JWKSource 에 키가 2개(현재 + 이전)라 지정하지 않으면 NimbusJwtEncoder::selectJwk 에서 예외 (jpa/advance/custom-jwk-source 프로젝트 참고)
     *      claim 추가 : access token 에 authorities, id token 에 nickname.. 토큰 타입 분기 (custom-oauth2-token-customizer 프로젝트 참고)
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer(@Value("${my.jwk.current-key-alias}") String currentKeyAlias) {
        return context -> {

            context.getJwsHeader().keyId(currentKeyAlias);

            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                Set<String> authorities = context.getPrincipal().getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());
                context.getClaims().claim("authorities", authorities);
            }

            if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
                context.getClaims().claim("nickname", context.getPrincipal().getName() + "-nickname");
            }
        };
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }
}
