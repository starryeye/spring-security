package dev.starryeye.custom_mac_and_rsa_validation.security.config;

import dev.starryeye.custom_mac_and_rsa_validation.security.filter.jwt.provider.JwtAuthenticationProvider;
import dev.starryeye.custom_mac_and_rsa_validation.security.filter.username_password.CustomUsernamePasswordAuthenticationFilter;
import dev.starryeye.custom_mac_and_rsa_validation.security.filter.jwt.JwtVerifierFilter;
import dev.starryeye.custom_mac_and_rsa_validation.signature.JwtVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
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

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CustomUsernamePasswordAuthenticationFilter customUsernamePasswordAuthenticationFilter,
            JwtVerifierFilter jwtVerifierFilter
    ) throws Exception {
        return http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .requestMatchers("/").permitAll()
                                .anyRequest().authenticated()
                )
                .sessionManagement(httpSecuritySessionManagementConfigurer ->
                        httpSecuritySessionManagementConfigurer
                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // 세션 생성 및 사용하지 않도록 설정
                )
                .addFilterBefore(customUsernamePasswordAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtVerifierFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CustomUsernamePasswordAuthenticationFilter jwtAuthenticationFilter(AuthenticationManager defaultAuthenticationManager) {
        CustomUsernamePasswordAuthenticationFilter customUsernamePasswordAuthenticationFilter = new CustomUsernamePasswordAuthenticationFilter();
        customUsernamePasswordAuthenticationFilter.setAuthenticationManager(defaultAuthenticationManager);
        return customUsernamePasswordAuthenticationFilter;
    }

    @Bean
    public JwtVerifierFilter jwtVerifierFilter(AuthenticationManager jwtAuthenticationManager) {
        return new JwtVerifierFilter(jwtAuthenticationManager);
    }

    @Bean
    public AuthenticationManager defaultAuthenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public AuthenticationManager jwtAuthenticationManager(JwtVerifier jwtVerifier) {

        JwtAuthenticationProvider jwtAuthenticationProvider = new JwtAuthenticationProvider(jwtVerifier);

        return new ProviderManager(
                List.of(jwtAuthenticationProvider),
                null
        );
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {

        UserDetails user = User.withUsername("user")
                .password(passwordEncoder.encode("1111"))
                .authorities("ROLE_USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
