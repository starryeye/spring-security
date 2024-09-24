package dev.starryeye.custom_authenticate_exception_handling;

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
         * 예외를 처리하는 필터는 ExceptionTranslationFilter 이다.
         * ExceptionTranslationFilter..
         *      인증 예외가 발생하면..
         *          - SecurityContext 에서 현재 인증이 문제있다고 판단하여 인증 객체(Authentication)를 초기화한다.
         *          - AuthenticationEntryPoint 를 호출한다. (보통 인증을 시도할 수 있는 로그인 페이지로 redirect 시킨다.)
         *          - 현재 요청(인증 예외를 발생하게한 요청) 정보를 세션에 저장 (다음 인증 성공 시 인증 이전 페이지로 redirect)
         *              RequestCache, SavedRequest 관련..
         *      인가 예외가 발생하면..
         *          - 현재 인증 상태가 익명 사용자(인증하지 않은 상태)라면 인증 예외가 발생한 것처럼 수행(위 3 가지 동작)
         *          - 현재 인증 상태가 사용자(인증한 상태)이지만 권한 문제라면 AccessDeniedHandler 를 호출한다. (보통 403 리턴)
         *
         * exceptionHandling 설정은..
         * ExceptionTranslationFilter 동작에 관련한 설정이다.
         * authenticationEntryPoint 메서드로 AuthenticationEntryPoint 객체를 직접 등록하면..
         *      - 현재 spring security application 에서 적용 중인 인증 방법(인증 프로세스) 에서 제공하는 AuthenticationEntryPoint 를 무시하고 해당 커스텀 객체가 호출된다.
         *      - Spring Security 가 기본으로 제공하는 "/login" 페이지 자체가 생성 되지 않는다. (formLogin 설정과 상관 없이 생성 안됨)
         * accessDeniedHandler 메서드로 AccessDeniedHandler 객체를 직접 등록하면..
         *      - 현재 spring security application 에서 적용 중인 인증 방법(인증 프로세스) 에서 제공하는 AccessDeniedHandler 를 무시하고 해당 커스텀 객체가 호출된다.
         *
         *
         * authenticationEntryPoint 를 설정하지 않으면..
         *      인증 프로세스 마다 제공되는 클래스로 동작한다.
         *          UsernamePasswordAuthenticationFilter -> LoginUrlAuthenticationEntryPoint
         *          BasicAuthenticationFilter -> BasicAuthenticationEntryPoint
         *          인증 프로세스 설정이 없으면 -> Http403ForbiddenEntryPoint
         *
         * accessDeniedHandler 를 설정하지 않으면..
         *      AccessDeniedHandlerImpl
         *
         * 테스트 설명
         * 1. 현재 설정 상태 그대로 하여 루트 경로 접근 시..
         *      인증이 되지 않은 상태라.. 익명 사용자이며 권한 밖의 페이지이므로 인가 예외가 발생한다.
         *      그리고, 익명 사용자 + 인가 예외 이므로 인증 예외의 3 가지 동작을 수행하여 authenticationEntryPoint 로 설정한 객체가 호출되는 것을 볼 수 있다.
         *      또한, authenticationEntryPoint 를 사용하였으므로 formLogin 설정 여부와 상관없이 기본 제공되는 "/login" 페이지 생성이 안되고 LoginController 로 매핑됨
         * 2. authenticationEntryPoint 설정은 주석처리하고.. formLogin 의 login 페이지를 활용하여 로그인 인증을 수행후 "/admin" 접근 시..
         *      익명 사용자가 아님 + 인가 예외 이므로 accessDeniedHandler 로 설정한 객체가 호출되는 것을 볼 수 있다.
         */

        http.authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers("/login").permitAll()
                                .requestMatchers("/denied").hasRole("ADMIN")
                                .anyRequest().authenticated()
                )
                .formLogin(Customizer.withDefaults())
                .exceptionHandling(httpSecurityExceptionHandlingConfigurer ->
                        httpSecurityExceptionHandlingConfigurer
                                .authenticationEntryPoint((request, response, authException) -> { // ExceptionTranslationFilter 가 호출 할 AuthenticationEntryPoint 객체이다.
                                    System.out.println(authException.getMessage());
                                    response.sendRedirect("/login"); // 인증을 하라고 안내하는 로그인 페이지로 redirect
                                })
                                .accessDeniedHandler((request, response, accessDeniedException) -> { // ExceptionTranslationFilter 가 호출 할 AccessDeniedHandler 객체이다.
                                    System.out.println(accessDeniedException.getMessage());
                                    response.sendRedirect("/denied"); // 권한 문제로 차단되었다는 페이지로 redirect
                                })
                );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {

        UserDetails userDetails = User.withUsername("user").password("{noop}1111").roles("USER").build();
        return new InMemoryUserDetailsManager(userDetails);
    }
}
