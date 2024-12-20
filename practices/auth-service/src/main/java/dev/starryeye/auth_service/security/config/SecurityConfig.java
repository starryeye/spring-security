package dev.starryeye.auth_service.security.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationProvider myAuthenticationProvider;

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
                        formLoginConfigurer.loginPage("/login").permitAll()
                )
                .authenticationProvider(myAuthenticationProvider)
        ;

        return http.build();
    }
}
