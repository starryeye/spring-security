package dev.starryeye.auth_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authorizeRequestMatcherRegistry ->
                        authorizeRequestMatcherRegistry
                                .requestMatchers("/css/**", "/images/**", "/js/**", "/favicon/**", "/*/icon-*").permitAll()
                                .requestMatchers("/").permitAll()
                                .requestMatchers("/users/signup").permitAll()
                                .anyRequest().authenticated()
                )
                .formLogin(formLoginConfigurer ->
                        formLoginConfigurer.loginPage("/login").permitAll()
                )
        ;

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {

        UserDetails user = User.withUsername("user").password("{noop}1111").roles("USER").build();

        return new InMemoryUserDetailsManager(user);
    }
}
