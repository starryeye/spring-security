package dev.starryeye.custom_authenticate_login_controller;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
public class SecurityConfig {

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository securityContextRepository(HttpSecurity http) {
        SecurityContextRepository securityContextRepository = http.getSharedObject(SecurityContextRepository.class);

        if (securityContextRepository == null) {
            securityContextRepository = new DelegatingSecurityContextRepository(
                    new HttpSessionSecurityContextRepository(),
                    new RequestAttributeSecurityContextRepository()
            );
        }

        return securityContextRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        /**
         * 시큐리티는 필터로 인증을 처리하는데 필터를 건너띄고 MVC controller 에서 인증 처리를 하고 싶을 땐
         * 다양한 인증 방법에 의한 인증 처리 필터는 사용하면 안된다. (ex. formLogin 등)
         *
         * 따라서, Spring Security 라이브러리를 의존하기만 해도 auto-configuration 에 의해 formLogin 이 활성화 되므로
         * SecurityFilterChain Bean 을 아래와 같이 등록해줘야한다.
         */
        http.authorizeHttpRequests(auth ->
                auth
                        .requestMatchers("/login").permitAll()
                        .anyRequest().authenticated()
        )
                .csrf(AbstractHttpConfigurer::disable); // 추후 학습할 것, csrf

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails userDetails = User.withUsername("user").password("{noop}1111").roles("USER").build();
        return new InMemoryUserDetailsManager(userDetails);
    }


}
