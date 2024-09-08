package dev.starryeye.custom_authorize_form_login;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@EnableWebSecurity
@Configuration
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {

        /**
         * HttpSecurity 에 아래와 같은 formLogin 인증 설정을 하고 SecurityFilterChain 을 build 하면,
         * 빌드 과정에서 HttpSecurity 내부 configurers 안에 FormLoginConfigurer 가 만들어지고
         * FormLoginConfigurer 는 UsernamePasswordAuthenticationFilter 가 만들어진다. (AbstractAuthenticationProcessingFilter 를 상속)
         *
         * UsernamePasswordAuthenticationFilter 는 인증 요청이 오면 인증 처리를 수행하는 Filter 이다.
         */

        httpSecurity
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .formLogin(httpSecurityFormLoginConfigurer -> httpSecurityFormLoginConfigurer
//                        .loginPage("/loginPage") // 로그인 페이지 Path 지정, 기본값 = /login
                        .loginProcessingUrl("/loginProc") // 인증 Path 지정, 기본값 = /login, form 태그 action 관련
                        .defaultSuccessUrl("/", true) // 인증 성공시 항상 이동할 Path 지정, 기본값 = false
                        .failureUrl("/failed") // 인증 실패할 경우 이동할 Path 지정, 기본값 = /login?error
                        .usernameParameter("userId") // 인증 수행 시, http body 에서 어떤 키로 username 을 찾을 지 지정, form 태그 input 관련
                        .passwordParameter("passwd") // 인증 수행 시, http body 에서 어떤 키로 password 를 찾을 지 지정, form 태그 input 관련
//                        .successHandler((request, response, authentication) -> {
//                            System.out.println("Authentication successful!, authentication = " + authentication);
//                            response.sendRedirect("/home"); // defaultSuccessUrl 을 true 로 준 Path 보다 우선한다.
//                        })
//                        .failureHandler((request, response, exception) -> {
//                            System.out.println("Authentication failed!, exception = " + exception.getMessage());
//                            response.sendRedirect("/login"); // failureUrl 보다 우선한다.
//                        })
                        .permitAll() // loginPage(), loginProcessingUrl(), failureUrl() 은 모든 사용자들에게 열어주도록하는 설정
                );

        return httpSecurity.build();
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
