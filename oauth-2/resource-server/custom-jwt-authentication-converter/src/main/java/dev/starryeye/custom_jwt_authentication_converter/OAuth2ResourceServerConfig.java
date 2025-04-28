package dev.starryeye.custom_jwt_authentication_converter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class OAuth2ResourceServerConfig {

    /**
     * "/private/photos/**" 요청은 "ROLE_manage-account" 권한이 필요함
     * "/photos/**" 요청은 "SCOPE_photos" 권한이 필요함
     *
     * SecurityFilterChain 을 2개로 설정하여
     * 첫번째 filterChain 에는 커스텀 jwtAuthenticationConverter 를 설정하여 권한 매핑을 커스텀하게 바꿨다.
     * 두번째 filterChain 에는 기본 jwtAuthenticationConverter 을 사용하도록 하여 scope claim 을 보고 "SCOPE_" prefix 로 권한 매핑되도록함.
     */

    @Order(1)
    @Bean
    public SecurityFilterChain securityFilterChain1(HttpSecurity http) throws Exception {

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new CustomJwtGrantedAuthoritiesConverter());

        http
                .securityMatcher("/private/**")
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .requestMatchers("/private/photos/**").hasAuthority("ROLE_manage-account")
                                .anyRequest().denyAll()
                )
                .oauth2ResourceServer(httpSecurityOAuth2ResourceServerConfigurer ->
                        httpSecurityOAuth2ResourceServerConfigurer
                                .jwt(jwtConfigurer ->
                                        jwtConfigurer.jwtAuthenticationConverter(jwtAuthenticationConverter)
                                )
                )
                ;

        return http.build();
    }

    @Order(2)
    @Bean
    public SecurityFilterChain securityFilterChain2(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .requestMatchers("/photos/**").hasAuthority("SCOPE_photos")
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
