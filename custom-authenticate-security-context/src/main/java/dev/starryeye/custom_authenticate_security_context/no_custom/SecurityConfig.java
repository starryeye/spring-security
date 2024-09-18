package dev.starryeye.custom_authenticate_security_context.no_custom;

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

    /**
     * SecurityContextConfigurer::configure
     * - SecurityContextHolderFilter 를 생성한다.
     * - 초기화 과정에서 생성되어 HttpSecurity 에 공유되고 있는 SecurityContextRepository 를 SecurityContextHolderFilter 에 설정한다.
     *
     * FormLoginConfigurer::configure
     * - UsernamePasswordAuthenticationFilter 를 생성한다.
     * - SecurityContextConfigurer 로 부터 설정된 SecurityContextRepository 를 받아서 UsernamePasswordAuthenticationFilter 에도 설정한다.
     *      UsernamePasswordAuthenticationFilter 는 인증 성공 시 SecurityContextRepository 를 이용하여 HttpSession 에 SecurityContext 를 저장한다.
     *          추후 다음 요청 시, SecurityContextHolderFilter 에서 SecurityContext 를 조회하여 인증을 유지하기 위함이다.
     *
     */

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .formLogin(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.withUsername("user").password("{noop}1111").roles("USER").build();
        return new InMemoryUserDetailsManager(user);
    }
}
