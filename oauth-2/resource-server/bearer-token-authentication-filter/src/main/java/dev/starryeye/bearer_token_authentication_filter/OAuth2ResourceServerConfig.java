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
