package dev.starryeye.custom_authenticate_session_management;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        /**
         * 인증 서버는 동일한 계정에 대해 동시에 몇개의 세션을 허용할 것인지 제어 할 수 있다.
         * Spring Security 에서는 두가지 전략을 제공한다.
         * 1. 사용자 인증 강제 만료
         *      인증은 모두 허용하며, 가장 최근에 성공한 인증들에 대해 "동시 세션 허용 수" 만큼 허용함.
         *      먼저 성공한 인증들의 세션은 만료됨
         * 2. 사용자 인증 시도 차단
         *      인증 시도 자체를 차단, 가장 먼저 성공한 인증들만 "동시 세션 허용 수" 만큼 허용함.
         *      이후 인증들은 실패함
         *
         *
         * 테스트
         * 하나는 기본 chrome, 다른하나는 시크릿모드로 ㄱㄱ
         */

        http.authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers("invalidSessionUrl", "expiredUrl").permitAll()
                                .anyRequest().authenticated()
                )
                .formLogin(Customizer.withDefaults())
                .sessionManagement(httpSecuritySessionManagementConfigurer ->
                        httpSecuritySessionManagementConfigurer
                                // invalidSessionUrl, expiredUrl 두개는 maxSessionsPreventsLogin 가 false 일 때만 의미가 있으며..
                                // 용도는 비슷하나 두개의 설정을 하고 안하고의 경우의 수 4가지 결과가 다르다..
                                .invalidSessionUrl("/invalidSessionUrl")
                                .maximumSessions(1) // 동시 세션 허용 수, 기본 값 : 무제한
                                .maxSessionsPreventsLogin(false) // true: 사용자 인증 시도 차단, false : 사용자 인증 강제 만료, 기본 값 : false
                                .expiredUrl("/expiredUrl")
                )
        ;

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails userDetails = User.withUsername("user").password("{noop}1111").roles("USER").build();
        return new InMemoryUserDetailsManager(userDetails);
    }
}
