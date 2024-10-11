package dev.starryeye.custom_authorize_request_matcher_delegating_authorization_manager;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcherEntry;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SecurityConfig {


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        /**
         * RequestMatcherDelegatingAuthorizationManager 는
         * 요청 기반 권한 부여 동작 관련하여.. AuthorizationManager 구현체들 중 적합한 객체로 위임한다.
         */

        http.authorizeHttpRequests(authorization ->
                authorization
                        .anyRequest().access(
                                // 모든 요청을 처리할 AuthorizationManager 를 지정했다.
                                // Spring Security 기본 RequestMatcherDelegatingAuthorizationManager 는 해당 AuthorizationManager 를 모든 요청에 대해 사용한다.
                                authorizationManager(null)
                        )
        )
                .formLogin(Customizer.withDefaults());

        return http.build();
    }

    private AuthorizationManager<RequestAuthorizationContext> authorizationManager(HandlerMappingIntrospector handlerMappingIntrospector) {

        /**
         * 모든 요청을 CustomRequestMatcherDelegatingAuthorizationManager 으로 처리하도록한다.
         * CustomRequestMatcherDelegatingAuthorizationManager 는 내부에 RequestMatcherDelegatingAuthorizationManager 를 가지도록하여
         *      RequestMatcherDelegatingAuthorizationManager 에 처리를 위임한다.
         *      RequestMatcherDelegatingAuthorizationManager 는 아래에서 정의한 requestMatcherEntry1, 2, 3, 4, 5 를 가진다.
         *
         * 최종적으로 Spring Security 기본 RequestMatcherDelegatingAuthorizationManager 는
         * CustomRequestMatcherDelegatingAuthorizationManager 로 처리를 위임하고 CustomRequestMatcherDelegatingAuthorizationManager 는
         * 내부 RequestMatcherDelegatingAuthorizationManager 로 처리를 위임하는 형태이다.
         *
         * RequestMatcherEntry..
         * 내부에 RequestMatcher 와 매핑된 요청에 대해 권한 심사를 하기 위한 AuthorizationManager 를 가진다.
         */

        List<RequestMatcherEntry<AuthorizationManager<RequestAuthorizationContext>>> mappings = new ArrayList<>();


        RequestMatcherEntry<AuthorizationManager<RequestAuthorizationContext>> requestMatcherEntry1 =
                new RequestMatcherEntry<>(new MvcRequestMatcher(handlerMappingIntrospector, "/"),
                        AuthorityAuthorizationManager.hasRole("ANONYMOUS"));

        RequestMatcherEntry<AuthorizationManager<RequestAuthorizationContext>> requestMatcherEntry2 =
                new RequestMatcherEntry<>(new MvcRequestMatcher(handlerMappingIntrospector, "/user"),
                        AuthorityAuthorizationManager.hasAuthority("ROLE_USER"));

        RequestMatcherEntry<AuthorizationManager<RequestAuthorizationContext>> requestMatcherEntry3 =
                new RequestMatcherEntry<>(new MvcRequestMatcher(handlerMappingIntrospector, "/db"),
                        AuthorityAuthorizationManager.hasAuthority("ROLE_DB"));

        RequestMatcherEntry<AuthorizationManager<RequestAuthorizationContext>> requestMatcherEntry4 =
                new RequestMatcherEntry<>(new MvcRequestMatcher(handlerMappingIntrospector, "/admin"),
                        AuthorityAuthorizationManager.hasRole("ADMIN"));

        RequestMatcherEntry<AuthorizationManager<RequestAuthorizationContext>> requestMatcherEntry5 =
                new RequestMatcherEntry<>(AnyRequestMatcher.INSTANCE, new AuthenticatedAuthorizationManager<>());

        mappings.add(requestMatcherEntry1);
        mappings.add(requestMatcherEntry2);
        mappings.add(requestMatcherEntry3);
        mappings.add(requestMatcherEntry4);
        mappings.add(requestMatcherEntry5);

        return new CustomRequestMatcherDelegatingAuthorizationManager(mappings);
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
        // 직접 AuthorityAuthorizationManager 를 생성했기 때문에 roleHierarchy 는 적용되지 않는다.
        // 적용하고 싶다면 AuthorityAuthorizationManager 를 생성할때 직접 roleHierarchy 를 셋팅해줘야함
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("DB", "USER", "ANONYMOUS")
                .role("DB").implies("USER", "ANONYMOUS")
                .role("USER").implies("ANONYMOUS")
                .build();
    }
}
