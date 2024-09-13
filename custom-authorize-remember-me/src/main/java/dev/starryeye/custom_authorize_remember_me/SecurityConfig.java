package dev.starryeye.custom_authorize_remember_me;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        /**
         * 아래 설정에 의해, 기억하기 체크박스를 체크하고 인증에 성공하면 클라이언트로 전달되는 쿠키는 총 2개이다.
         * JSESSIONID : 폼인증에 의함
         * remember : 기억하기 기능에 의함
         */

        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .formLogin(Customizer.withDefaults()) // form 인증
                .rememberMe(httpSecurityRememberMeConfigurer -> // 기억하기(로그인 유지) 기능
                        httpSecurityRememberMeConfigurer
//                                .alwaysRemember(true) // true : 기억하기 체크박스를 체크하지 않아도 기억하기 기능이 활성화 된다.
                                .tokenValiditySeconds(3600)
                                .userDetailsService(inMemoryUserDetailsManager())
                                .rememberMeParameter("remember") // 체크박스 이름, 기본 값 : remember-me
                                .rememberMeCookieName("remember") // 쿠키 이름, 기본 값 : remember-me
                                .key("security") // 기억하기 인증을 위해 생성된 토큰을 식별하는 키 설정
                );

        return http.build();
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
