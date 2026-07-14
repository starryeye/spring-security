package dev.starryeye.custom_login_and_consent_page;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                                // consent 페이지("/oauth2/consent")는 로그인 후 도달하므로 authenticated 에 포함되면 된다.
                                .anyRequest().authenticated()
                )
                .formLogin(httpSecurityFormLoginConfigurer ->
                        httpSecurityFormLoginConfigurer
                                /**
                                 * 커스텀 로그인 페이지 설정..
                                 * loginPage() 를 설정하면 DefaultLoginPageGeneratingFilter 가 등록되지 않고..
                                 * 미인증 시 이 URI 로 redirect 된다. (GET "/login" 은 LoginController 가 렌더링)
                                 * 로그인 처리 POST 는 기본값 그대로 "/login" 을 사용한다. (UsernamePasswordAuthenticationFilter)
                                 * permitAll() 로 로그인 페이지 접근과 로그인 처리 요청을 허용한다.
                                 */
                                .loginPage("/login")
                                .permitAll()
                )
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
