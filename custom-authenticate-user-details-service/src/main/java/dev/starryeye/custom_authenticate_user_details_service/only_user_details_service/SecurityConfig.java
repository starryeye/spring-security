package dev.starryeye.custom_authenticate_user_details_service.only_user_details_service;

import dev.starryeye.custom_authenticate_user_details_service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

//@Configuration
public class SecurityConfig {

    /**
     * UserDetailsService 를 빈으로 등록하면 auto-configuration 에 의해
     * CustomUserDetailsService 는 최종적으로 DaoAuthenticationProvider 가 사용하게 된다.
     */

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.authorizeHttpRequests(auth ->
                        auth.anyRequest().authenticated()
                )
                .formLogin(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public UserDetailsService customUserDetailsService() {
        // UserDetailsService 타입의 클래스를 직접 만들어 이전 프로젝트들에서 사용하던 아래 userDetailsService() 를 대체한다.
        return new CustomUserDetailsService();
    }

//    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.withUsername("user").password("{noop}1111").roles("USER").build();
        return new InMemoryUserDetailsManager(user);
    }
}
