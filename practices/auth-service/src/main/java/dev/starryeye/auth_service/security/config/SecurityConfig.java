package dev.starryeye.auth_service.security.config;

import dev.starryeye.auth_service.security.ajax_api.ApiMyAuthenticationFilter;
import dev.starryeye.auth_service.security.base.MyDynamicAuthorizationManager;
import dev.starryeye.auth_service.security.base.MyDynamicAuthorizationService;
import dev.starryeye.auth_service.security.base.MyMapBasedUrlRoleMapper;
import dev.starryeye.auth_service.security.base.MyUrlRoleMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.*;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] STATIC_RESOURCES_PATH_PATTERNS = {"/css/**", "/images/**", "/js/**", "/favicon/**", "/*/icon-*"};

    private final AuthenticationProvider myAuthenticationProvider;
    private final AuthenticationDetailsSource<HttpServletRequest, WebAuthenticationDetails> myAuthenticationDetailsSource;
    private final AuthenticationSuccessHandler myAuthenticationSuccessHandler;
    private final AuthenticationFailureHandler myAuthenticationFailureHandler;
    private final AccessDeniedHandler myAccessDeniedHandler;

    private final HandlerMappingIntrospector introspector;

    private final AuthenticationProvider apiMyAuthenticationProvider;
    private final AuthenticationSuccessHandler apiMyAuthenticationSuccessHandler;
    private final AuthenticationFailureHandler apiMyAuthenticationFailureHandler;
    private final AuthenticationEntryPoint apiMyAuthenticationEntryPoint;
    private final AccessDeniedHandler apiMyAccessDeniedHandler;

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
        authenticationManagerBuilder.authenticationProvider(apiMyAuthenticationProvider);
        AuthenticationManager authenticationManager = authenticationManagerBuilder.build();

        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(authorizeRequestMatcherRegistry ->
                        authorizeRequestMatcherRegistry
                                .requestMatchers(STATIC_RESOURCES_PATH_PATTERNS).permitAll()

                                .requestMatchers("/api").permitAll()
                                .requestMatchers("/api/login").permitAll()

                                .requestMatchers("/api/user").hasRole("USER")
                                .requestMatchers("/api/manager").hasRole("MANAGER")
                                .requestMatchers("/api/admin").hasRole("ADMIN")

                                .anyRequest().authenticated()
                )
//                .csrf(AbstractHttpConfigurer::disable)
                .authenticationManager(authenticationManager)
                .addFilterBefore(restMyAuthenticationFilter(http, authenticationManager), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(httpSecurityExceptionHandlingConfigurer ->
                        httpSecurityExceptionHandlingConfigurer
                                .authenticationEntryPoint(apiMyAuthenticationEntryPoint)
                                .accessDeniedHandler(apiMyAccessDeniedHandler)
                )
        ;

        return http.build();
    }

    private ApiMyAuthenticationFilter restMyAuthenticationFilter(HttpSecurity http, AuthenticationManager authenticationManager) {

        ApiMyAuthenticationFilter apiMyAuthenticationFilter = new ApiMyAuthenticationFilter(http);
        apiMyAuthenticationFilter.setAuthenticationManager(authenticationManager);
        apiMyAuthenticationFilter.setAuthenticationSuccessHandler(apiMyAuthenticationSuccessHandler);
        apiMyAuthenticationFilter.setAuthenticationFailureHandler(apiMyAuthenticationFailureHandler);

        return apiMyAuthenticationFilter;
    }
}
