package dev.starryeye.custom_authorize_authorization_manager;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        /**
         * AuthorizationManager ..
         * - 인증된 사용자의 권한 정보와 요청 자원의 보안 요구 사항을 비교하여 접근 여부 심사를 수행하는 인터페이스이다.
         * - 요청 기반, 메서드 기반 인가 처리에서 최종 자원 접근 결정을 수행한다.
         * - AuthorizationFilter 가 AuthorizationManager 를 호출하여 접근 권한 여부를 심사한다.
         *
         * 요청 기반 권한 부여 관리자(AuthorizationManager 구현체)는 RequestMatcherDelegatingAuthorizationManager 에 의해 위임된다.
         * 아래 설정 코드 기준으로..
         * anyRequest 는 AuthenticatedAuthorizationManager (인증 여부 심사) 가 동작되고..
         * /user, /db, /admin 은 AuthorityAuthorizationManager (권한 여부 심사) 가 동작되고..
         * /secure 는 커스텀 CustomAuthorizationManager 가 동작된다.
         */

        http.authorizeHttpRequests(authorization ->
                authorization
                        .requestMatchers("/").hasRole("ANONYMOUS")
                        .requestMatchers("/user").hasRole("USER")
                        .requestMatchers("/db").access(new WebExpressionAuthorizationManager("hasRole('DB')"))
                        .requestMatchers("/admin").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/secure").access(new CustomAuthorizationManager()) // hasRole("SECURE") api 와 동일한 기능을 하는 커스텀 AuthorizationManager
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

    @Bean
    public RoleHierarchy roleHierarchy() {

        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("DB", "USER", "ANONYMOUS")
                .role("DB").implies("USER", "ANONYMOUS")
                .role("USER").implies("ANONYMOUS")
                .build();
    }
}
