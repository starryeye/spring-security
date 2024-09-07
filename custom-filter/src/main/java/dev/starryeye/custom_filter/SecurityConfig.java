package dev.starryeye.custom_filter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@EnableWebSecurity
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {

        /**
         * HttpSecurity ..
         * - SecurityBuilder 를 구현한 클래스이다.
         * - spring auto-configuration 으로 생성된다.
         * - SecurityConfigurer 를 생성하고 초기화 작업(내부 필터 생성 등)을 수행한다. (HttpSecurity::build)
         *
         * WebSecurity ..
         * - SecurityBuilder 를 구현한 클래스이다.
         * - spring auto-configuration 으로 생성된다.
         * - HttpSecurity 로 생성한 SecurityFilterChain 을 가지고 FilterChainProxy 를 생성한다.
         *
         * DelegatingFilterProxy ..
         * - Servlet container 에서 관리되는 Servlet Filter 이다.
         * - FilterChainProxy (Spring bean) 를 호출하여 요청을 위임하여, Servlet 에서 Spring 으로 필터 역할을 연동시켜주는 객체이다.
         */

        // HttpSecurity 를 주입 받았기 때문에 기본적으로 인가 설정이 적용되어있는 상태이다.
        httpSecurity.authorizeHttpRequests(
                        auth -> auth.anyRequest().authenticated() // 인가(authorize) 설정 가능
                )
                .formLogin(Customizer.withDefaults()); // 인증(authenticate) 설정 가능
        return httpSecurity.build(); // 설정(내부에 configurers) 을 바탕으로 filter 를 생성하는 등.. 초기화 작업을 수행, SecurityFilterChain 생성
    }

    @Bean
    public InMemoryUserDetailsManager inMemoryUserDetailsManager() {
        /**
         * 인증(authenticate) 을 위한 기본 계정을 설정한다.
         *
         * application.yml 에 spring.security.user.name/password/roles 로도 가능하다.
         *
         * 기본 계정을 설정하지 않으면, 기본적으로 name 은 user, password 는 application 실행 로그에 출력된다.
         */

        UserDetails user1 = User.withUsername("user1")
                .password("{noop}1111")
                .authorities("USER")
                .build();
        UserDetails user2 = User.withUsername("user2")
                .password("{noop}1112")
                .authorities("USER")
                .build();
        UserDetails user3 = User.withUsername("user3")
                .password("{noop}1113")
                .authorities("USER")
                .build();

        return new InMemoryUserDetailsManager(List.of(user1, user2, user3));
    }
}
