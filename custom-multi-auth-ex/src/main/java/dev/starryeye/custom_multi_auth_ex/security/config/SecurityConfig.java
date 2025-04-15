package dev.starryeye.custom_multi_auth_ex.security.config;

import dev.starryeye.custom_multi_auth_ex.security.filter.ApiKeyAuthenticationFilter;
import dev.starryeye.custom_multi_auth_ex.security.filter.JwtAuthenticationFilter;
import dev.starryeye.custom_multi_auth_ex.security.provider.ApiKeyAuthenticationProvider;
import dev.starryeye.custom_multi_auth_ex.security.provider.JwtAuthenticationProvider;
import dev.starryeye.custom_multi_auth_ex.security.service.ApiKeyService;
import dev.starryeye.custom_multi_auth_ex.security.service.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain apiChain(HttpSecurity http, AuthenticationManager jwtManager) throws Exception {

        return http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .anyRequest().hasAuthority("ROLE_DEVELOPER")
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtManager), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain adminChain(HttpSecurity http, AuthenticationManager apiKeyManager) throws Exception {

        /**
         * ApiKey 로 인증을 수행하는 것은..
         *  stateless 인증, 세션 필요 없이 Api Key 로만 인증 처리하기 때문에
         * ApiKeyAuthenticationFilter 을 세션 기반 필터인 SecurityContextHolderFilter 전에 처리하도록 함.
         */
        return http
                .securityMatcher("/admin/**")
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .anyRequest().hasAnyAuthority("ROLE_ADMIN")
                )
                .addFilterBefore(new ApiKeyAuthenticationFilter(apiKeyManager), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public SecurityFilterChain loginChain(HttpSecurity http, AuthenticationManager parentDaoAuthenticationManager) throws Exception {

        return http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .anyRequest().authenticated()
                )
                .authenticationManager(parentDaoAuthenticationManager)
                .formLogin(Customizer.withDefaults())
                .build();
    }

    // parent AuthenticationManager
    @Bean
    public AuthenticationManager parentDaoAuthenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        return authenticationManagerBuilder.build();
    }

    @Bean
    public AuthenticationManager jwtManager(JwtService jwtService, AuthenticationManager parentDaoAuthenticationManager) {
        JwtAuthenticationProvider jwtAuthenticationProvider = new JwtAuthenticationProvider(jwtService);
        return new ProviderManager(
                List.of(jwtAuthenticationProvider),
                parentDaoAuthenticationManager
        );
    }

    @Bean
    public AuthenticationManager apiKeyManager(ApiKeyService apiKeyService, AuthenticationManager parentDaoAuthenticationManager) {
        ApiKeyAuthenticationProvider apiKeyAuthenticationProvider = new ApiKeyAuthenticationProvider(apiKeyService);
        return new ProviderManager(
                List.of(apiKeyAuthenticationProvider),
                parentDaoAuthenticationManager
        );
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails userDetails = User.withUsername("user")
                .password("{noop}1111")
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(userDetails);
    }
}
