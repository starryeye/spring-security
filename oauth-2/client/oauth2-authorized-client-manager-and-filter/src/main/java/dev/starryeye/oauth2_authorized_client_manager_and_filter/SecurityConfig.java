package dev.starryeye.oauth2_authorized_client_manager_and_filter;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final DefaultOAuth2AuthorizedClientManager oAuth2AuthorizedClientManager;
    private final OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .requestMatchers("/password-credentials-grant-login").permitAll()
                                .anyRequest().authenticated()
                )
                .addFilterBefore(myOAuth2LoginAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
        ;

        return http.build();
    }

    @Bean
    public MyOAuth2LoginAuthenticationFilter myOAuth2LoginAuthenticationFilter() {

        MyOAuth2LoginAuthenticationFilter myOAuth2LoginAuthenticationFilter = new MyOAuth2LoginAuthenticationFilter(oAuth2AuthorizedClientManager, oAuth2AuthorizedClientRepository);

        myOAuth2LoginAuthenticationFilter.setAuthenticationSuccessHandler((request, response, authentication) -> response.sendRedirect("/"));

        return myOAuth2LoginAuthenticationFilter;
    }
}
