package dev.starryeye.auth_service.security.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationProvider myAuthenticationProvider;
    private final AuthenticationDetailsSource<HttpServletRequest, WebAuthenticationDetails> myAuthenticationDetailsSource;
    private final AuthenticationSuccessHandler myAuthenticationSuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authorizeRequestMatcherRegistry ->
                        authorizeRequestMatcherRegistry
                                .requestMatchers("/css/**", "/images/**", "/js/**", "/favicon/**", "/*/icon-*").permitAll()
                                .requestMatchers("/").permitAll()
                                .requestMatchers("/users/signup").permitAll()
                                .anyRequest().authenticated()
                )
                .formLogin(formLoginConfigurer ->
                        formLoginConfigurer
                                .loginPage("/login").permitAll()
                                .authenticationDetailsSource(myAuthenticationDetailsSource)
                                .successHandler(myAuthenticationSuccessHandler)
                )
                .authenticationProvider(myAuthenticationProvider)
        ;

        return http.build();
    }
}
