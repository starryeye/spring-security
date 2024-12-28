package dev.starryeye.auth_service.security.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] STATIC_RESOURCES_PATH_PATTERNS = {"/css/**", "/images/**", "/js/**", "/favicon/**", "/*/icon-*"};

    private final AuthenticationProvider myAuthenticationProvider;
    private final AuthenticationDetailsSource<HttpServletRequest, WebAuthenticationDetails> myAuthenticationDetailsSource;
    private final AuthenticationSuccessHandler myAuthenticationSuccessHandler;
    private final AuthenticationFailureHandler myAuthenticationFailureHandler;
    private final AccessDeniedHandler myAccessDeniedHandler;

    @Bean
    public SecurityFilterChain formSecurityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authorizeRequestMatcherRegistry ->
                        authorizeRequestMatcherRegistry
                                .requestMatchers(STATIC_RESOURCES_PATH_PATTERNS).permitAll()

                                .requestMatchers("/").permitAll()
                                .requestMatchers("/users/signup").permitAll()
                                .requestMatchers("/login*").permitAll() // 주의.. "/login" 과 "/login?error=aaa" 는 다르게 볼 때가 있다.

                                .requestMatchers("/user").hasRole("USER")
                                .requestMatchers("/manager").hasRole("MANAGER")
                                .requestMatchers("/admin").hasRole("ADMIN")

                                .anyRequest().authenticated()
                )
                .formLogin(formLoginConfigurer ->
                        formLoginConfigurer
                                .loginPage("/login").permitAll()
                                .authenticationDetailsSource(myAuthenticationDetailsSource)
                                .successHandler(myAuthenticationSuccessHandler)
                                .failureHandler(myAuthenticationFailureHandler)
                )
                .authenticationProvider(myAuthenticationProvider)
                .exceptionHandling(exceptionHandlingConfigurer ->
                        exceptionHandlingConfigurer
                                .accessDeniedHandler(myAccessDeniedHandler)
                )
        ;

        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain restSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(authorizeRequestMatcherRegistry ->
                        authorizeRequestMatcherRegistry
                                .requestMatchers(STATIC_RESOURCES_PATH_PATTERNS).permitAll()

                                .anyRequest().permitAll()
                )
                .csrf(AbstractHttpConfigurer::disable)
        ;

        return http.build();
    }
}
