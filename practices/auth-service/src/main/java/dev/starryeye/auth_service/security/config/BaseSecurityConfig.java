package dev.starryeye.auth_service.security.config;

import dev.starryeye.auth_service.security.base.MyDynamicAuthorizationManager;
import dev.starryeye.auth_service.security.base.MyDynamicAuthorizationService;
import dev.starryeye.auth_service.security.base.MyMapBasedUrlRoleMapper;
import dev.starryeye.auth_service.security.base.MyUrlRoleMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@Configuration
@RequiredArgsConstructor
public class BaseSecurityConfig {

    private final AuthenticationProvider myAuthenticationProvider;
    private final AuthenticationDetailsSource<HttpServletRequest, WebAuthenticationDetails> myAuthenticationDetailsSource;
    private final AuthenticationSuccessHandler myAuthenticationSuccessHandler;
    private final AuthenticationFailureHandler myAuthenticationFailureHandler;
    private final AccessDeniedHandler myAccessDeniedHandler;

    private final HandlerMappingIntrospector introspector;

    @Bean
    public SecurityFilterChain baseSecurityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authorizeRequestMatcherRegistry ->
                        authorizeRequestMatcherRegistry
                                .anyRequest().access(myDynamicAuthorizationManager())
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
    public AuthorizationManager<RequestAuthorizationContext> myDynamicAuthorizationManager() {
        return new MyDynamicAuthorizationManager(introspector, myDynamicAuthorizationService());
    }

    @Bean
    public MyDynamicAuthorizationService myDynamicAuthorizationService() {
        return new MyDynamicAuthorizationService(myUrlRoleMapper());
    }

    @Bean
    public MyUrlRoleMapper myUrlRoleMapper() {
        return new MyMapBasedUrlRoleMapper();
    }
}
