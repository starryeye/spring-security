package dev.starryeye.hello_jpa_authorization_server;

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
public class DefaultSecurityConfig {

    /**
     * OAuth 2.0 Authorization server endpoint 와 관계없는 요청들에 대한 처리를 담당하는 Security 설정
     */

    @Bean
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                // client 등록/조회 API.. 학습 편의상 개방, 실제로는 관리자 인증/인가가 필요하다.
                                .requestMatchers("/registered-client", "/registered-client/*").permitAll()
                                .anyRequest().authenticated()
                )
                .csrf(csrfConfigurer ->
                        csrfConfigurer
                                .ignoringRequestMatchers("/registered-client") // POST 등록 API 를 브라우저 세션 없이 호출하기 위함
                )
                .formLogin(Customizer.withDefaults())
        ;

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {

        UserDetails user = User.withUsername("user")
                .password("{noop}1111")
                .authorities("ROLE_USER")
                .build();

        return new InMemoryUserDetailsManager(user);
    }
}
