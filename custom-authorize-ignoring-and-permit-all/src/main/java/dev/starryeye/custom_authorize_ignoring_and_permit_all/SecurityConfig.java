package dev.starryeye.custom_authorize_ignoring_and_permit_all;

import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {

        /**
         * 특정 Path 의 자원(정적 자원) 접근에 대해.. Security filter 수행을 안하도록 한다.(ignoring)
         * atCommonLocations() 내부에서 참조하는 StaticResourceLocation 라는 enum class 에 무시할 Path 가 존재한다.
         *
         * WebSecurityCustomizer 를 만들면..
         * SecurityFilterChain 이 만들어지게된다..
         * 따라서, FilterChainProxy 는 두개의 SecurityFilterChain 을 가지게되고
         * ignoring 옵션을 설정한 SecurityFilterChain 은 .. 내부에 필터가 하나도 없어서 Security filter 수행을 안하게 되는 것이다.
         */
        return webSecurity -> webSecurity.ignoring()
                .requestMatchers(PathRequest.toStaticResources().atCommonLocations());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        /**
         * 위 webSecurityCustomizer 빈을 만들어 ignoring 처리를 하기 보다
         * 아래 주석 처럼 permitAll 로 처리하는 것을 더 권장한다.
         * -> spring 6.x 부터는 인증 세션을 지연 로딩 하기 때문에 이전 버전의 성능문제가 해결되었다.
         * -> 또한, ignoring 의 경우 filter 를 하나도 안타지만.. 아래 permitAll 의 경우 filter 를 태워서 보안적으로도 우수하다.
         */

        http
                .authorizeHttpRequests(authorization ->
                        authorization

//                                .requestMatchers("/images/**", "/css/**", "/js/**").permitAll()
                                .anyRequest().authenticated()
                )
                .formLogin(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.withUsername("user").password("{noop}1111").roles("USER").build();
        UserDetails db = User.withUsername("db").password("{noop}1111").roles("DB").build();
        UserDetails admin = User.withUsername("admin").password("{noop}1111").roles("ADMIN", "SECURE").build();
        return new InMemoryUserDetailsManager(user, db, admin);
    }
}
