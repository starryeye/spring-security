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
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain apiChain(HttpSecurity http, AuthenticationManager jwtAuthenticationManager) throws Exception {

        return http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .anyRequest().hasAuthority("ROLE_DEVELOPER")
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtAuthenticationManager), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain adminChain(HttpSecurity http, AuthenticationManager apiKeyAuthenticationManager) throws Exception {

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
                .addFilterBefore(new ApiKeyAuthenticationFilter(apiKeyAuthenticationManager), UsernamePasswordAuthenticationFilter.class)
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
        /**
         * 참고 이 AuthenticationManager(ProviderManager) 는..
         * 직접 가진(child) Provider 가 0개이고
         * parent AuthenticationManager(ProviderManager) 에 DaoAuthenticationProvider 가 있다.
         *
         * 의도한대로 깔끔하게 한다면.. (현재.. ApiKeyAuthenticationFilter 에서 authenticationManager 를 참조해보면 이중으로 되어있음)
         * 아래 코드처럼 할게 아니라..
         * return new ProviderManager(
         *                 List.of(new DaoAuthenticationProvider()),
         *                 null
         *         );
         * 로 해야함.. 대신 UserDetailService 및 passwordEncoder 등을 직접 셋팅해야할듯..
         */
        AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        return authenticationManagerBuilder.build();
    }

    @Bean
    public AuthenticationManager jwtAuthenticationManager(JwtService jwtService, AuthenticationManager parentDaoAuthenticationManager) {
        JwtAuthenticationProvider jwtAuthenticationProvider = new JwtAuthenticationProvider(jwtService);
        return new ProviderManager(
                List.of(jwtAuthenticationProvider),
                parentDaoAuthenticationManager
        );
    }

    @Bean
    public AuthenticationManager apiKeyAuthenticationManager(ApiKeyService apiKeyService, AuthenticationManager parentDaoAuthenticationManager) {
        ApiKeyAuthenticationProvider apiKeyAuthenticationProvider = new ApiKeyAuthenticationProvider(apiKeyService);
        return new ProviderManager(
                List.of(apiKeyAuthenticationProvider),
                parentDaoAuthenticationManager
        );
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.withUsername("user")
                .password("{noop}1111")
                .roles("USER")
                .build();
        UserDetails forParentDao = User.withUsername("parent")
                .password("{noop}1111")
                .roles("USER", "ADMIN", "DEVELOPER")
                .build();

        return new InMemoryUserDetailsManager(List.of(user, forParentDao));
    }
}
