package dev.starryeye.custom_security_matcher;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain1(HttpSecurity http) throws Exception {

        /**
         * 모든 요청에 대해 securityFilterChain1 이 수행되도록 한다. (form 로그인이 적용된 SecurityFilterChain)
         */
        http.authorizeHttpRequests(authorization ->
                        authorization.anyRequest().authenticated()
                )
                .formLogin(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    @Order(1) // 범위가 좁은 SecurityFilterChain 이 우선적으로 매칭되도록 한다.
    public SecurityFilterChain securityFilterChain2(HttpSecurity http) throws Exception {

        /**
         * securityMatchers 를 사용하여 "/api/**", "/oauth/**" 에 해당되는 요청은 SecurityFilterChain2 가 실행되도록한다.
         */
        http
                .securityMatchers(requestMatcherConfigurer ->
                        requestMatcherConfigurer.requestMatchers("/api/**", "/oauth/**")
                )
                .authorizeHttpRequests(authorization ->
                        authorization.anyRequest().permitAll()
                );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.withUsername("user").password("{noop}1111").roles("USER").build();
        UserDetails manager = User.withUsername("db").password("{noop}1111").roles("DB").build();
        UserDetails admin = User.withUsername("admin").password("{noop}1111").roles("ADMIN", "SECURE").build();
        return new InMemoryUserDetailsManager(user, manager, admin);
    }
}
