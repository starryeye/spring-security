package dev.starryeye.oauth2_resource_server_configurer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class OAuth2ResourceServerConfig {

    /**
     * OAuth2ResourceServerAutoConfiguration 에 의해 spring boot 자동 구성 이 동작한다.
     *
     * OAuth2ResourceServerAutoConfiguration ..
     *      Oauth2ResourceServerConfiguration 을 import 한다.
     *
     * OAuth2ResourceServerConfiguration ..
     *      OAuth2ResourceServerJwtConfiguration 을 import 한다.
     *          JWT 자체를 검증하는 용도인 JwtDecoder 빈 생성 관련
     *          내부 클래스인 OAuth2SecurityFilterChainConfiguration 에서 기본 SecurityFilterChain 을 생성함. (아래와 같이 직접 SecurityFilterChain 을 등록하면 동작하지 않음.)
     *      OAuth2ResourceServerOpaqueTokenConfiguration 을 import 한다.
     *          토큰 활성화 여부(introspect) 기능 관련
     */


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        /**
         * 아래 코드는 기본 생성되는 SecurityFilterChain 과 동일한 설정이다.
         *
         * OAuth2ResourceServerConfigurer 에서는.. 아래 두개의 configurer 로 설정가능하다.
         *      OAuth2ResourceServerConfigurer<H>.JwtConfigurer (현재 설정)
         *      OAuth2ResourceServerConfigurer<H>.OpaqueTokenConfigurer (추후 알아본다.)
         *
         * OAuth2ResourceServerConfigurer 설정하면.. init/configure 에 의해..
         *      JwtConfigurer 설정됨.
         *      AuthenticationEntryPoint 가 BearerTokenAuthenticationEntryPoint 로 설정됨.
         *      filter 가 BearerTokenAuthenticationFilter 로 생성됨.
         *
         * JwtConfigurer 설정하면.. init/configure 에 의해..
         *      filter 에서 사용할 AuthenticationManager 설정함.
         *      JWT 토큰을 인증 객체로 변환할 JwtAuthenticationConverter 설정함.
         *
         * BearerTokenAuthenticationFilter ..
         *      AnonymousAuthenticationProvider, JwtAuthenticationProvider 를 사용
         *
         * JwtAuthenticationProvider..
         *      NimbusJwtDecoder 를 사용
         *      JwtAuthenticationConverter 를 사용
         */
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
