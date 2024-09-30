package dev.starryeye.custom_csrf;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        /**
         * CSRF 는..
         * - Spring Security 에서 아무런 설정하지 않아도 기본적으로 설정된다.
         * - CsrfFilter 에 의해 동작된다.
         *
         * 기본으로 설정되는 기능
         * - POST, PUT, DELETE 의 요청에 한해서 (CsrfFilter 에서 GET, HEAD, TRACE, OPTION 이면 다음 필터로 넘긴다.)
         *      - HTTP 요청 Parameter 에 "_csrf" 토큰이 포함 되어야 요청을 허가한다. (없으면 인증을 수행할 수 있는 페이지로 보낸다..)
         *      - HTTP 요청 Header "X-Csrf-Token" 에 토큰을 넣어주면 요청을 허가한다. (없으면 인증을 수행할 수 있는 페이지로 보낸다..)
         *      http/api.http 참조..
         * - csrf 토큰은 session 에 저장된다.(기본)
         */

        http.authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers("/api/articles", "/api/articles/new", "/csrf").permitAll()
                                .anyRequest().authenticated()
                )
//                .csrf(httpSecurityCsrfConfigurer ->
//                        httpSecurityCsrfConfigurer
//                                .ignoringRequestMatchers("/api/articles", "/api/articles/new") // 부분 비활성화
//                                .disable() // 모든 요청에 대해 비활성화
//                )
                .formLogin(Customizer.withDefaults())
//                .csrf(httpSecurityCsrfConfigurer -> //todo, api.http (CookieCsrfTokenRepository 를 사용하면 헤더로 전달 시 X-Xsrf-Token 을 사용해야하며, 원본 토큰이 필요) 동작이 성공하지 못함.. 이유 찾기
//                        httpSecurityCsrfConfigurer
////                                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
//                                .csrfTokenRepository(new CookieCsrfTokenRepository()) // csrf Token 발행을 세션 방식이 아닌 쿠키 방식으로 변경한다.
//                )
        ;

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.withUsername("user").password("{noop}1111").roles("USER").build();
        return new InMemoryUserDetailsManager(user);
    }
}
