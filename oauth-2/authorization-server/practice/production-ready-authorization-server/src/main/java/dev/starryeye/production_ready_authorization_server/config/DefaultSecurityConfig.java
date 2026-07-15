package dev.starryeye.production_ready_authorization_server.config;

import dev.starryeye.production_ready_authorization_server.jpa.JpaUserDetailsService;
import dev.starryeye.production_ready_authorization_server.jpa.UserEntityRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
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
                                /**
                                 * admin API 보호..
                                 * client/사용자 등록은 ROLE_ADMIN 만 호출할 수 있다. (관리자 계정은 AdminAccountInitializer 가 부팅 시 생성)
                                 * 일반 사용자(ROLE_USER)가 basic 인증으로 호출하면 403 이 된다.
                                 */
                                .requestMatchers("/registered-clients/**", "/users/**").hasRole("ADMIN")
                                // 로드밸런싱 관찰용
                                .requestMatchers("/whoami").permitAll()
                                // consent 페이지("/oauth2/consent")는 로그인 후 도달하므로 authenticated 에 포함되면 된다.
                                .anyRequest().authenticated()
                )
                .csrf(csrfConfigurer ->
                        csrfConfigurer
                                .ignoringRequestMatchers("/registered-clients", "/users") // 등록 API 를 브라우저 세션 없이 호출하기 위함
                )
                /**
                 * admin API 를 curl 등에서 세션 없이 호출할 수 있도록 http basic 을 함께 허용한다..
                 * 인증 자체는 form login 과 동일하게 DaoAuthenticationProvider (JPA UserDetailsService + password 검증) 를 탄다.
                 * 미인증 진입점은 두 방식이 공존하므로 DelegatingAuthenticationEntryPoint 가 Accept 헤더로 협상한다.. (관찰 결과)
                 *      브라우저(text/html) -> 로그인 페이지 redirect(302), curl 처럼 Accept 에 html 이 없으면 -> 401 (http basic 진입점)
                 */
                .httpBasic(Customizer.withDefaults())
                .formLogin(httpSecurityFormLoginConfigurer ->
                        httpSecurityFormLoginConfigurer
                                // 커스텀 로그인 페이지 (custom-login-and-consent-page 프로젝트 참고).. 로그인 처리 POST 는 기본값 그대로
                                .loginPage("/login")
                                .permitAll()
                )
        ;

        return http.build();
    }

    /**
     * 사용자 저장소를 JPA/MySQL 로 영속화한다.. (keycloak 대비 갭이던 사용자 관리 층의 최소 구현)
     *      InMemory 였다면 재기동 시 사라지고 인스턴스마다 따로 존재하는 상태라 운영에서는 외부 저장소가 필수다.
     *      UserDetailsService 빈을 등록하면 DaoAuthenticationProvider 가 이 구현으로 사용자를 조회한다.
     *      사용자 등록은 UserController 의 admin API 로 한다. (user / 1111 도 seed 가 아니라 API 로 등록)
     */
    @Bean
    public UserDetailsService userDetailsService(UserEntityRepository repository) {
        return new JpaUserDetailsService(repository);
    }
}
