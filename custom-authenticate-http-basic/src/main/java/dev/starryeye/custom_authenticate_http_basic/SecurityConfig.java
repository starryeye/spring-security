package dev.starryeye.custom_authenticate_http_basic;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
         * HTTP basic 인증 방식 적용 (RFC 7235)
         *
         * HttpBasicConfigurer 설정 클래스를 통해 설정가능하다.
         * Configurer 에 의해 생성되는 Filter 는 BasicAuthenticationFilter 이다.
         *
         * BasicAuthenticationFilter..
         *      BasicAuthenticationConverter 를 통하여, 요청 데이터 헤더(Authorization) 에 Base64 로 username, password 를 인코딩 된 값을 추출한다.
         *      인증이 성공하면 SecurityContext 에 Authentication(UsernamePasswordAuthenticationToken) 이 저장된다.
         *      인증이 실패하면 BasicAuthenticationEntryPoint(기본값) 에 의해 절차 수행됨 (WWW-Authenticate 헤더 전송)
         *      이전에 인증을 성공했다면(Authorization 헤더 or 쿠키 세션?를 포함)..
         *          BasicAuthenticationFilter::authenticationIsRequired 에 의해(SecurityContext 에 접근) 해당 필터는 스킵
         */

        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
//                .httpBasic(Customizer.withDefaults());
                .httpBasic(httpSecurityHttpBasicConfigurer ->
                        httpSecurityHttpBasicConfigurer.authenticationEntryPoint(new CustomAuthenticationEntryPoint()) // 기본 값 : BasicAuthenticationEntryPoint
                                .realmName("realm") // 기본 값 : realm
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
