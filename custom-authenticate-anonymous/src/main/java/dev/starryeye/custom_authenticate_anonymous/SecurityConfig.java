package dev.starryeye.custom_authenticate_anonymous;

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
         * Spring Security 에서 익명사용자란 ..
         * 인증받지 않은 사용자이다.
         */

        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/anonymous").hasRole("GUEST") // GUSET 권한만이 anonymous 자원에 접근가능하도록 설정
                        .requestMatchers("/anonymous/context", "/authentication").permitAll() // 해당 자원에는 모든 사용자가 접근 가능하도록 설정
                        .anyRequest().authenticated()
                )
                .formLogin(Customizer.withDefaults()) // 폼 인증 설정
                .anonymous(httpSecurityAnonymousConfigurer ->
                        httpSecurityAnonymousConfigurer
                                .principal("guest") // 익명 사용자 이름 설정
                                .authorities("ROLE_GUEST") // 익명 사용자 권한 설정
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
