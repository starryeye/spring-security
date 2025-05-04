package dev.starryeye.custom_opaque_token_introspector;

import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.SpringOpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class OAuth2ResourceServerConfig {

    /**
     * oauth2ResourceServer() 설정 api 에서
     * jwt 설정이 아닌 opaqueToken 설정을 사용하면
     * Authorization 헤더 값으로 들어온 Bearer 타입 토큰을 자체 검증(JWK 이용) 하는 것이 아닌
     * authorization server 에 introspect endpoint 요청을 하여 토큰 검증을 위임한다.
     */

    /**
     * 기본적인 검증 흐름.
     * BearerTokenAuthenticationFilter (jwt 설정과 동일)
     *      미 인증 객체 생성 : BearerTokenAuthenticationToken (jwt 설정과 동일)
     *      AuthenticationManager(ProviderManager) 를 거쳐서 OpaqueTokenAuthenticationProvider 에 인증 처리 위임
     *      인증 처리 후, SecurityContext 에 인증 객체 저장
     * OpaqueTokenAuthenticationProvider (jwt 설정으로 치면, JwtAuthenticationProvider)
     *      OpaqueTokenIntrospector 에 토큰 검증 수행 위임 (jwt 설정으로 치면, JwtDecoder)
     *          검증 후 OAuth2IntrospectionAuthenticatedPrincipal 리턴됨. (jwt 설정으로 치면, Jwt)
     *      인증 객체 생성 및 리턴 : BearerTokenAuthentication (jwt 설정으로 치면, JwtAuthenticationToken)
     *          principal : OAuth2IntrospectionAuthenticatedPrincipal (jwt 설정으로 치면, Jwt)
     *
     */

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .anyRequest().authenticated()
                )
                .oauth2ResourceServer(httpSecurityOAuth2ResourceServerConfigurer ->
                        httpSecurityOAuth2ResourceServerConfigurer
                                .opaqueToken(Customizer.withDefaults())
                )
                ;

        return http.build();
    }

    /**
     * OpaqueTokenIntrospector..
     *      OAuth2ResourceServerOpaqueTokenConfiguration::opaqueTokenIntrospector() 에서..
     *          OpaqueTokenIntrospector 빈을 자동 구성 등록하고 있다.
     *      jwt 설정으로 치면, JwtDecoder 의 역할과 동일하다. (토큰 검증)
     *      아래와 같이 직접 등록하면 등록한 빈이 사용된다. (혹은 oauth2ResourceServer().opaqueToken() 의 introspector 로 직접 설정가능)
     *
     * 참고.
     * resource server 에서 client id/password 를 설정해야하는데.. (application.yml 참고)
     *      access token 을 발급한 client 에서 사용하는 client id/password 와 달라도 된다.
     *
     * 참고.
     * NimbusOpaqueTokenIntrospector 구현체(spring 의존성에서는 deprecated 됨)를 사용하고 싶다면..
     *      implementation 'com.nimbusds:oauth2-oidc-sdk:11.23.1' 로 시도해보자..
     */
    @Bean
    public OpaqueTokenIntrospector springOpaqueTokenIntrospector(OAuth2ResourceServerProperties properties) {
        OAuth2ResourceServerProperties.Opaquetoken opaquetoken = properties.getOpaquetoken();
        return new SpringOpaqueTokenIntrospector(
                opaquetoken.getIntrospectionUri(),
                opaquetoken.getClientId(),
                opaquetoken.getClientSecret()
        );
    }
}
