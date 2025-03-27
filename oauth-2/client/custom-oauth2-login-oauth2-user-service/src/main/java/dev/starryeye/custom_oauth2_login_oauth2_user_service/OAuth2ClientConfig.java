package dev.starryeye.custom_oauth2_login_oauth2_user_service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class OAuth2ClientConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .requestMatchers(HttpMethod.POST, "/user").permitAll() // UserController 실습을 위함
                                .requestMatchers(HttpMethod.POST, "/oidc").permitAll()
                                .anyRequest().authenticated()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .oauth2Login(Customizer.withDefaults());
//                .oauth2Login(oAuth2LoginConfigurer ->
//                        oAuth2LoginConfigurer
//                                .userInfoEndpoint(userInfoEndpointConfig ->
//                                        userInfoEndpointConfig
////                                                .userService() // OAuth 2.0, access token 으로 사용자 정보 얻는 방식 ("/userinfo") 의 역할을 담당하는 객체 직접 지정
//                                                .oidcUserService(new MyOidcUserService()) // OAuth 2.0, OIDC 로 사용자 정보 얻는 방식 의 역할을 담당하는 객체 직접 지정
//                                )
//                );

        return http.build();
    }
}
