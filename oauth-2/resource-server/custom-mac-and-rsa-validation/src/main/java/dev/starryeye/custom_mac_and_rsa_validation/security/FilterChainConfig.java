package dev.starryeye.custom_mac_and_rsa_validation.security;

import dev.starryeye.custom_mac_and_rsa_validation.security.filter.username_password.CustomUsernamePasswordAuthenticationFilter;
import dev.starryeye.custom_mac_and_rsa_validation.security.filter.jwt_1.JwtVerifierFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class FilterChainConfig {

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
                // 토큰 발행용
                .addFilterBefore(customUsernamePasswordAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // 토큰 검증
                // jwt_1 방식 (직접 커스텀 Filter 사용, spring-resource-server 의존성 X)
//                .addFilterBefore(jwtVerifierFilter, UsernamePasswordAuthenticationFilter.class) // jwt_1 방식
                // jwt_2 방식 (oauth2ResourceServer() 설정 api 사용)
                .oauth2ResourceServer(httpSecurityOAuth2ResourceServerConfigurer ->
                        httpSecurityOAuth2ResourceServerConfigurer
                                .jwt(Customizer.withDefaults()) // JwtDecoderConfig 로 JwtDecoder 는 직접 생성
                )
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    public AuthenticationManager defaultAuthenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
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
