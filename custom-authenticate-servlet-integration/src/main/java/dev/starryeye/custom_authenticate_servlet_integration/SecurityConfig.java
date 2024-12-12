package dev.starryeye.custom_authenticate_servlet_integration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * 스프링 시큐리티는 필터 뿐만아니라 서블릿과의 통합/연동도 지원한다.
     *
     * SecurityContextHolderAwareRequestFilter
     *      FilterChainProxy 가 관리하는 시큐리티 필터중 하나이다.
     *      이 필터는 HttpServletRequest 객체를 SecurityContextHolderAwareRequestWrapper 클래스로 래핑한다.
     *      그래서, 개발자는 래핑된 객체로 서블릿에서 다양한 시큐리티 작업을 수행할 수 있다.
     *
     * Servlet3SecurityContextHolderAwareRequestWrapper
     *      SecurityContextHolderAwareRequestWrapper 를 상속한 객체이다.
     *
     * HttpServlet3RequestFactory
     *      요청이 들어오면, 요청 객체(HttpServletRequest)로 Servlet3SecurityContextHolderAwareRequestWrapper 를 실제로 생성하는 객체이다.
     *      SecurityContextHolderAwareRequestFilter 는 HttpServlet3RequestFactory 를 생성한다.
     */

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                .requestMatchers("/").hasRole("ANONYMOUS")
                                .requestMatchers("/user/**").hasRole("USER")
                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                .requestMatchers("/db/**").hasRole("DB")
                                .anyRequest().authenticated()
                )
//                .formLogin(Customizer.withDefaults()) // servlet 과 통합하여 인증 기능을 수행할 것이다.
        ;

        return http.build();
    }


    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.withUsername("user").password("{noop}1111").roles("USER").build();
        UserDetails db = User.withUsername("db").password("{noop}1111").roles("DB").build();
        UserDetails admin = User.withUsername("admin").password("{noop}1111").roles("ADMIN", "SECURE").build();
        return new InMemoryUserDetailsManager(user, db, admin);
    }

    @Bean
    public RoleHierarchy roleHierarchy() {

        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("DB", "USER", "ANONYMOUS")
                .role("DB").implies("USER", "ANONYMOUS")
                .role("USER").implies("ANONYMOUS")
                .build();
    }
}
