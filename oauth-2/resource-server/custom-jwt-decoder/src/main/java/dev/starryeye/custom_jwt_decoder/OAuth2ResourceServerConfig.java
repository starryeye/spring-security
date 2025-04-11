package dev.starryeye.custom_jwt_decoder;

import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class OAuth2ResourceServerConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

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

    /**
     * JwtDecoder..
     * - 문자열로 된 JWT(JSON Web Token)를 Jwt 객체로 디코딩하는 역할의 객체이다.
     * - JWT 가 JWS(JSON Web Signature) 구조로 되어있을 경우, JWS 서명에 대한 검증을 한다.
     *      authorization server 에서 public key 를 받아와서 전자서명 검증 방법을 포함하여 직접 자체적으로 검증함
     * - spring boot 자동 구성에 의해 기본적으로 빈 등록이된다.
     *      OAuth2ResourceServerJwtConfiguration 에 보면 빈 등록 중
     *      직접 등록한다면, JwtAuthenticationProvider 는 등록된 JwtDecoder 빈을 사용한다.
     *          authorization server 에서 사용하는 알고리즘이 RS256 이 아니라면 직접 등록해서 사용하자.
     *
     */

    @Bean
    public JwtDecoder jwtDecoder1(OAuth2ResourceServerProperties properties) {
        // OAuth2ResourceServerProperties 는 application.yml 과 연관
        return JwtDecoders.fromIssuerLocation(properties.getJwt().getIssuerUri());
        // issuer uri 를 통해 authorization server 와 통신하여, 최종적으로 필요한 jwk-set-uri 를 얻는다.
        //      1. /issuer/.well-known/openid-configuration
        //      2. /.well-known/openid-configuration/issuer
        //      3. /.well-known/oauth-authorization-server/issuer
        //      위 3가지 경로로 요청해보면서 정보를 얻음

        // 참고 아래 방법은 3번 경로로는 요청안하는 정도의 차이..
//        return JwtDecoders.fromOidcIssuerLocation(properties.getJwt().getIssuerUri());
    }

//    @Bean
//    public JwtDecoder jwtDecoder2(OAuth2ResourceServerProperties properties) {
//        return NimbusJwtDecoder.withJwkSetUri(properties.getJwt().getJwkSetUri())
////                .cache() // public key 에 대해 캐시 설정가능
////                .jwsAlgorithm() // 알고리즘 특정가능, 기본 RS256
//                .build();
//    }
}
