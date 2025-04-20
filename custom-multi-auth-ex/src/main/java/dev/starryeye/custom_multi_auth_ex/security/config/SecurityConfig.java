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
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
public class SecurityConfig {

    /**
     * README.md 참고하면 좋음
     */

    @Bean
    @Order(1)
    public SecurityFilterChain apiChain(HttpSecurity http, AuthenticationManager jwtAuthenticationManager) throws Exception {

        return http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .anyRequest().hasAuthority("ROLE_DEVELOPER")
                )
                // 주의사항, OncePerRequestFilter를 상속받은 filter 는 빈으로 등록하면 servlet filter 에 추가되므로 SecurityFilterChain 에서 new 해주는게 좋음..
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

    /**
     * AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
     * return authenticationManagerBuilder.build();
     *
     * 위와 같이 parentDaoAuthenticationManager 를 등록하면..
     * JwtAuthenticationManager 기준..
     * spring boot 자동 구성에 의해 DaoAuthenticationProvider 로 아래와 같은 구조로 만들어준다.
     * 하지만, 이중 parent 구성으로 보기가 좀 그렇다..
     * 그래서 현재 적용된 코드처럼 하여 이중 parent 구성을 회피함.
     *
     * JwtAuthenticationManager(ProviderManager)
     * - provider
     *      - JwtAuthenticationProvider
     * - parent(AuthenticationManager(ProviderManager)) <- parentDaoAuthenticationManager 이다.
     *      - provider
     *          - null
     *      - parent(AuthenticationManager(ProviderManager)) <- 이게 첫번째 parent 로 가야 깔끔
     *          - provider
     *              - DaoAuthenticationProvider
     *          - parent
     *              - null
     */
    @Bean
    public AuthenticationManager parentDaoAuthenticationManager(
            HttpSecurity http,
            AuthenticationConfiguration authenticationConfiguration,
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) throws Exception {

        DaoAuthenticationProvider authenticationManager = new DaoAuthenticationProvider();
        authenticationManager.setUserDetailsService(userDetailsService);
        authenticationManager.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(
                List.of(authenticationManager),
                null
        );
//        return authenticationConfiguration.getAuthenticationManager(); // 위 코드와 동일
        /**
         * 참고, (타입은 생략)
         *      am1 = authenticationConfiguration.getAuthenticationManager();
         *      authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
         *      am2 = authenticationManagerBuilder.build();
         *      am1 인스턴스 와 am2 의 parent 인스턴스는 동일하다.
         */
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
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails user = User.withUsername("user")
                .password(passwordEncoder.encode("1111"))
                .roles("USER")
                .build();
        UserDetails forParentDao = User.withUsername("parent")
                .password(passwordEncoder.encode("1111"))
                .roles("USER", "ADMIN", "DEVELOPER")
                .build();

        return new InMemoryUserDetailsManager(List.of(user, forParentDao));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
