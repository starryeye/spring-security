package dev.starryeye.custom_authenticate_authentication_manager.case_1;

import dev.starryeye.custom_authenticate_authentication_manager.CustomAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

//@Configuration
public class SecurityConfig {

    /**
     * HttpSecurity, AuthenticationManagerBuilder 를 통해 AuthenticationManager 를 생성할 수 있다.
     */

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        /**
         * 참고,
         * AuthenticationManager 를 생성하는 단계에서는 AuthenticationProvider 가 등록되지 않고..
         * HttpSecurity 를 build 할때, 내부 configurer 에 의해 생성되는듯 (기본은 Anonymous 와 dao 가 생성된다. -> case_2 에서 설정한 것과 동일하게 생성됨, CustomAuthenticationProvider 는 제외)
         *
         * 실제 인증을 수행하는 provider 는 dao 가 될 것이다.
         */

        // HttpSecurity 로 부터 AuthenticationManagerBuilder 를 참조 (AuthenticationManagerBuilder 는 자동 구성에 의한 스프링 빈이라 주입 받을 수도 있음)
        AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        // AuthenticationManager 생성
        AuthenticationManager authenticationManager = authenticationManagerBuilder.build();

        http.authorizeHttpRequests(auth ->
                auth
                        .requestMatchers("/", "/api/login").permitAll()
                        .anyRequest().authenticated()
        )
                .authenticationManager(authenticationManager) // 생성한 authenticationManager 를 설정
                .addFilterBefore(customFilter(http, authenticationManager), UsernamePasswordAuthenticationFilter.class); // UsernamePasswordAuthenticationFilter 이전에 커스텀 Filter 가 동작하도록 한다.

        return http.build();
    }

    private CustomAuthenticationFilter customFilter(HttpSecurity http, AuthenticationManager authenticationManager) {
        CustomAuthenticationFilter customAuthenticationFilter = new CustomAuthenticationFilter(http);
        customAuthenticationFilter.setAuthenticationManager(authenticationManager);
        return customAuthenticationFilter;
    }

    @Bean
    public InMemoryUserDetailsManager inMemoryUserDetailsManager() {
        UserDetails userDetails = User.withUsername("user")
                .password("{noop}1111")
                .authorities("USER")
                .build();

        return new InMemoryUserDetailsManager(userDetails);
    }
}
