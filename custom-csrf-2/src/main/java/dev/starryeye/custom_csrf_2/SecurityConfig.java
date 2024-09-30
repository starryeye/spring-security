package dev.starryeye.custom_csrf_2;

import dev.starryeye.custom_csrf_2.csrf.CustomCsrfFilter;
import dev.starryeye.custom_csrf_2.csrf.CustomCsrfTokenRequestHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        /**
         * JavaScript 기반 웹 애플리케이션을 지원해야할 때의 설정
         *
         * CsrfTokenRepository 를 CookieCsrfTokenRepository 를 사용해야한다.
         * - Client 로 csrf 토큰 발행 시, XSRF-TOKEN 명을 가진 쿠키로 발행해준다. (값은 csrf 원본 토큰이다. 암호화 하지 않음)
         * - 따라서, Client 는 서버로 요청 시 X-XSRF-TOKEN 헤더 또는 _csrf 요청 파라미터에 csrf 토큰을 담아 요청해야한다. + 응답받은 쿠키도 함께 포함
         *
         * JavaScript 에서 쿠키를 읽을 수 있도록 withHttpOnlyFalse 설정을 해준다.
         *
         * 참고
         * script.html
         * -> 'X-XSRF-TOKEN': getCookie('XSRF-TOKEN') // CSRF 토큰 헤더에 추가
         * -> credentials: 'include' // 쿠키를 포함시키기 위해 필요
         */

        CsrfTokenRequestHandler customCsrfTokenRequestHandler = new CustomCsrfTokenRequestHandler();

        http.authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers("/script", "script/csrfToken").permitAll()
                                .anyRequest().authenticated()
                )
                .csrf(httpSecurityCsrfConfigurer ->
                        httpSecurityCsrfConfigurer
                                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()) // 쿠키 방식으로 CsrfToken 을 관리하도록 함
                                .csrfTokenRequestHandler(customCsrfTokenRequestHandler) // 커스텀 CsrfTokenRequestHandler
                )
                .formLogin(Customizer.withDefaults())
                .addFilterBefore(new CustomCsrfFilter(), BasicAuthenticationFilter.class); // 커스텀 Csrf 필터

        return http.build();
    }
//
//    @Bean
//    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//
//        /**
//         * 타임리프와 form 태그를 이용하여 csrf 토큰을 처리할 때 설정
//         *
//         * CsrfTokenRepository 를 HttpSessionCsrfTokenRepository 를 사용하게 된다.
//         */
//
//        http.authorizeHttpRequests(auth ->
//                auth
//                        .requestMatchers("/form", "form/csrfToken").permitAll()
//                        .anyRequest().authenticated()
//        )
//                .formLogin(Customizer.withDefaults());
//
//        return http.build();
//    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.withUsername("user").password("{noop}1111").roles("USER").build();
        return new InMemoryUserDetailsManager(user);
    }
}
