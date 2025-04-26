package dev.starryeye.bearer_token_authentication_filter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class OAuth2ResourceServerConfig {

    /**
     * oauth2-resource-server 의존성의 oauth2ResourceServer() 설정을 활용할 경우..
     * client 요청에는 Authorization 헤더에 Bearer JWT 이 필요하다.
     * 해당 요청에 대해 security filter 가 인증 처리를 하는데..
     *
     * 이때 인증 처리를 해주는 필터가 BearerTokenAuthenticationFilter 이다.
     *
     * BearerTokenAuthenticationFilter 에 대해 알아본다.
     *
     * 처리 흐름
     * - "Authorization" Header 와 "Bearer {access token}" 형식 검증
     * - access token 을 BearerTokenAuthenticationToken(인증 객체)에 담아서 AuthenticationManager 에 인증 처리 위임
     * - ProviderManager 는 JwtAuthenticationProvider 에 인증 처리 위임
     * - JwtAuthenticationProvider 는 access token 을 NimbusJwtDecoder 를 사용하여 검증 (보통 authorization server 에서 제공하는 JWKSet 으로 진행)
     * - 검증 완료 후, Jwt 타입을 생성 (UserDetails 느낌)
     * - JwtAuthenticationConverter 를 사용하여 최종 인증 처리 된 JwtAuthenticationToken(인증 객체)를 생성
     *      - principal 로 Jwt 객체를 사용한다.
     * - SecurityContext 에 인증객체 적재
     *
     * 참고.
     * NimbusJwtDecoder 는 spring.security.oauth2.resourceserver.jwt.jwk-set-uri 를 설정하면 auto configuration 에 의해 생성됨.
     * - OAuth2ResourceServerJwtConfiguration::jwtDecoderByJwkKeySetUri
     */

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .anyRequest().authenticated()
                )
                .oauth2ResourceServer(httpSecurityOAuth2ResourceServerConfigurer ->
                        httpSecurityOAuth2ResourceServerConfigurer
                                .jwt(Customizer.withDefaults())
                )
                ;

        return http.build();
    }
}
