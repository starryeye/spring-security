package dev.starryeye.auth_service.security.config;

import dev.starryeye.auth_service.security.rest.RestMyAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.*;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] STATIC_RESOURCES_PATH_PATTERNS = {"/css/**", "/images/**", "/js/**", "/favicon/**", "/*/icon-*"};

    private final AuthenticationProvider myAuthenticationProvider;
    private final AuthenticationDetailsSource<HttpServletRequest, WebAuthenticationDetails> myAuthenticationDetailsSource;
    private final AuthenticationSuccessHandler myAuthenticationSuccessHandler;
    private final AuthenticationFailureHandler myAuthenticationFailureHandler;
    private final AccessDeniedHandler myAccessDeniedHandler;

    private final AuthenticationProvider restMyAuthenticationProvider;

    // todo, form 과 rest 를 패키지로 분리해보기

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

        /**
         * 참고
         * 해당 restSecurityFilterChain 에서 사용되고 있는 AuthenticationManager(ProviderManager) 는
         *      formSecurityFilterChain 에서 사용되는 AuthenticationManager 와 다른 객체이다.
         *      AuthenticationManager 내부에 AuthenticationProvider 는 동일한 객체가 있을 수 있다.
         */

        AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.authenticationProvider(restMyAuthenticationProvider);
        AuthenticationManager authenticationManager = authenticationManagerBuilder.build();

        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(authorizeRequestMatcherRegistry ->
                        authorizeRequestMatcherRegistry
                                .requestMatchers(STATIC_RESOURCES_PATH_PATTERNS).permitAll()

                                .anyRequest().permitAll()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .authenticationManager(authenticationManager)
                .addFilterBefore(restMyAuthenticationFilter(authenticationManager), UsernamePasswordAuthenticationFilter.class)
        ;

        return http.build();
    }

    private RestMyAuthenticationFilter restMyAuthenticationFilter(AuthenticationManager authenticationManager) {

        RestMyAuthenticationFilter restMyAuthenticationFilter = new RestMyAuthenticationFilter();
        restMyAuthenticationFilter.setAuthenticationManager(authenticationManager);

        return restMyAuthenticationFilter;
    }
}
