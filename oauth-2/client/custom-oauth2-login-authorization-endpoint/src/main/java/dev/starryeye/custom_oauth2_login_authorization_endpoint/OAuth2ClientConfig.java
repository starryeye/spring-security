package dev.starryeye.custom_oauth2_login_authorization_endpoint;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class OAuth2ClientConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .anyRequest().authenticated()
                )
                .oauth2Login(oAuth2LoginConfigurer ->
                        oAuth2LoginConfigurer
                                .loginPage("/login") // 기본값
                                .authorizationEndpoint(authorizationEndpointConfigurer ->
                                        authorizationEndpointConfigurer
                                                .baseUri("/oauth2/authorize") // 기본값은 "/oauth2/authorization" 이다.
                                )
                );

        return http.build();
    }
}
