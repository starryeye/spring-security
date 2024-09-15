package dev.starryeye.custom_authenticate_logout;

import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        /**
         * Spring security 에서 formLogin 설정을 하면 기본적으로 login 페이지와 logout 페이지를 제공한다.
         *
         * 로그아웃 페이지는 GET /logout 으로 접근 (기본)
         * 로그아웃 실행은 POST /logout 으로 접근 (기본)
         *
         * 현재 logoutUrl() or logoutRequestMatcher() 로 설정하면 로그아웃 할수 없음
         * -> GET /logout 페이지에서 제공하는 버튼의 요청 url 이 POST /logout 이기 때문이다.
         *      -> POST /logoutProcess 에서 GET /logoutProcess 로 바꾸고 로그아웃 페이지를 이용하지 말고 직접 호출하면 된다.
         *      -> 혹은 직접 controller 를 구현
         */

        http.authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers("/logoutSuccess").permitAll() // 이걸 설정하지 않으면, 로그아웃 순간 .anyRequest().authenticated() 때문에 /logoutSuccess 페이지 허용이 안되어 로그인 페이지로 가버림
                                .anyRequest().authenticated()
                )
                .formLogin(Customizer.withDefaults()) // 폼 인증 설정
                .logout(httpSecurityLogoutConfigurer ->
                        httpSecurityLogoutConfigurer
                                .logoutUrl("/logoutProcess") // 로그아웃 실행 URL 설정, 기본 값은 POST /logout 이다.
                                .logoutRequestMatcher(new AntPathRequestMatcher("/logoutProcess", HttpMethod.POST.name())) // 로그아웃 실행 URL 설정, logoutUrl() 보다 우선적이다. Http Method 를 지정하지 않으면 모든 Method 가 허용된다.
                                .logoutSuccessUrl("/logoutSuccess") // 로그아웃 성공 시 redirect 경로, 기본 값 : /login?logout
                                .logoutSuccessHandler((request, response, authentication) -> {
                                    response.sendRedirect("/logoutSuccess"); // 로그아웃 성공 핸들러, logoutSuccessUrl() 보다 우선순위 높다.
                                })
                                .deleteCookies("JSESSIONID", "remember-me") // 로그아웃 성공 시 제거될 쿠키 이름
                                .invalidateHttpSession(true) // 세션을 만료시킨다. 기본값 true
                                .clearAuthentication(true) // 로그아웃 시 SecurityContextLogoutHandler 가 인증객체(Authentication) 을 삭제한다. 기본 값 true
                                .addLogoutHandler((request, response, authentication) -> {
                                    System.out.println("add logout handler"); // 로그아웃 성공 핸들러 뒤에 추가할 핸들러 설정
                                    HttpSession session = request.getSession();
                                    session.invalidate(); // 세션 만료시킨다.
                                    SecurityContextHolder.getContextHolderStrategy().getContext().setAuthentication(null); // SecurityContextLogoutHandler 에 존재하는 현재 인증 객체(Authentication) 삭제
                                    SecurityContextHolder.getContextHolderStrategy().clearContext(); // SecurityContext 클리어
                                })
                                .permitAll() // logoutUrl(), logoutRequestMatcher 의 url 에 대해 모든 사용자 접근 허용
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
