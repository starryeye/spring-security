package dev.starryeye.oauth2_authorized_client_manager_and_filter;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final DefaultOAuth2AuthorizedClientManager defaultOAuth2AuthorizedClientManager;
    private final OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .requestMatchers("/password-credentials-grant-login").permitAll()
                                .anyRequest().authenticated()
                )
                .addFilterBefore(
                        myOAuth2LoginAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class
                )
        ;

        return http.build();
    }

    private MyOAuth2LoginAuthenticationFilter myOAuth2LoginAuthenticationFilter() {

        MyOAuth2LoginAuthenticationFilter myOAuth2LoginAuthenticationFilter = new MyOAuth2LoginAuthenticationFilter(defaultOAuth2AuthorizedClientManager, oAuth2AuthorizedClientRepository);

        myOAuth2LoginAuthenticationFilter.setAuthenticationManager(null); // MyOAuth2LoginAuthenticationFilter 는 AuthenticationManager 를 이용해서 인증을 하지 않음
        myOAuth2LoginAuthenticationFilter.setSecurityContextRepository(new HttpSessionSecurityContextRepository()); // MyOAuth2LoginAuthenticationFilter 의 상위 클래스인 AbstractAuthenticationProcessingFilter 이 인증 객체 및 SecurityContext 를 저장할 SecurityContextRepository 를 설정해줘야함
        myOAuth2LoginAuthenticationFilter.setAuthenticationSuccessHandler((request, response, authentication) -> response.sendRedirect("/"));

        return myOAuth2LoginAuthenticationFilter;
    }
}
