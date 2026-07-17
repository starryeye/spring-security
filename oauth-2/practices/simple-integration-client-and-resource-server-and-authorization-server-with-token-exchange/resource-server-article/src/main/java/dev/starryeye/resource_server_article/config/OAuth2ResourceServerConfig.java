package dev.starryeye.resource_server_article.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
public class OAuth2ResourceServerConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .anyRequest().hasAuthority("SCOPE_content")
                )
                .oauth2ResourceServer(httpSecurityOAuth2ResourceServerConfigurer ->
                        httpSecurityOAuth2ResourceServerConfigurer
                                .jwt(Customizer.withDefaults())
                )
                ;

        return http.build();
    }

    /**
     * aud 검증을 추가한 JwtDecoder 이다.. (빈으로 등록하면 자동 구성 대신 이 decoder 가 쓰인다)
     *      기본 검증은 서명/만료 중심이라 aud 를 보지 않는다.. 그래서 "다른 대상용 토큰" 도 서명만 맞으면 통과한다. (relay 가 동작했던 이유)
     *      aud 에 이 서버의 식별자(자원 URI.. http://localhost:8081)가 있어야만 수락하도록 validator 를 더한다.
     *          (client 가 authorize 요청에 실은 resource 파라미터(RFC 8707)를 authorization server 가 검증 후 aud 로 반영해준다)
     *      참고. aud 검증 실패는 scope 부족(403)과 달리 토큰 유효성(인증) 실패라 401 invalid_token 이 된다.
     */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {

        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD, audience -> audience != null && audience.contains("http://localhost:8081"));

        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), audienceValidator));

        return jwtDecoder;
    }
}
