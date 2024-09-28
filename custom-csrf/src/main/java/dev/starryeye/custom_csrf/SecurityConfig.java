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

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        /**
         * CSRF 는 Spring Security 에서 아무런 설정하지 않아도 기본적으로 설정된다.
         *
         * 기본으로 설정되는 기능
         * - POST, PUT, DELETE 의 요청에 한해서 HTTP 요청 Body 에 "_csrf" 토큰이 포함 되어야 요청을 허가한다. (없으면 인증을 수행할 수 있는 페이지로 보낸다..)
         */

        http.authorizeHttpRequests(auth ->
                auth
                        .requestMatchers("/api/articles", "/api/articles/new").permitAll()
                        .anyRequest().authenticated()
        )
                .formLogin(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(){
        UserDetails user = User.withUsername("user").password("{noop}1111").roles("USER").build();
        return  new InMemoryUserDetailsManager(user);
    }
}
