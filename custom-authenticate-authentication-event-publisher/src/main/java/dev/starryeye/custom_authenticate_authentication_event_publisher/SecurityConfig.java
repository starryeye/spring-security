package dev.starryeye.custom_authenticate_authentication_event_publisher;

import dev.starryeye.custom_authenticate_authentication_event_publisher.event.CustomAuthenticationFailureEvent;
import dev.starryeye.custom_authenticate_authentication_event_publisher.event.CustomDefaultAuthenticationFailureEvent;
import dev.starryeye.custom_authenticate_authentication_event_publisher.exception.CustomAuthenticationException;
import dev.starryeye.custom_authenticate_authentication_event_publisher.provider.CustomAuthenticationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collections;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CustomAuthenticationProvider customAuthenticationProvider // 커스텀 AuthenticationProvider
    ) throws Exception {

        http.authorizeHttpRequests(authorization ->
                        authorization
                                .requestMatchers("/").hasRole("ANONYMOUS")
                                .requestMatchers("/user").hasRole("USER")
                                .requestMatchers("/admin").hasRole("ADMIN")
                                .requestMatchers("/db").hasRole("DB")
                                .anyRequest().authenticated()
                )
                .formLogin(Customizer.withDefaults())
                .authenticationProvider(customAuthenticationProvider) // AuthenticationManager 가 커스텀 AuthenticationProvider 를 사용하도록함
        ;

        return http.build();
    }

    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        /**
         * 커스텀 AuthenticationEventPublisher 를 만들어본다.
         *
         * AuthenticationEventPublisher 는 ..
         * - 생성하지 않아도 빈으로 Auto-configuration 을 통해 빈으로 등록된다.
         * - 실패 이벤트를 발행하기 위해 AuthenticaionEventPublisher::publishAuthenticationFailure API 를 제공하는데
         *      파라미터로 예외를 넘기면된다. AuthenticaionEventPublisher 해당 예외와 매핑된 실패 이벤트를 ApplicationEventPublisher 를 통해 실제 발행한다.
         * - 예외와 실패 이벤트 매핑은 DefaultAuthenticationEventPublisher 생성자 참조
         */
        DefaultAuthenticationEventPublisher defaultAuthenticationEventPublisher = new DefaultAuthenticationEventPublisher(applicationEventPublisher);

        // 커스텀 예외(CustomAuthenticationException) 을 특정 이벤트(CustomAuthenticationFailureEvent) 로 매핑시키는 설정
        Map<Class<? extends AuthenticationException>, Class<? extends AbstractAuthenticationFailureEvent>> mapping =
                Collections.singletonMap(CustomAuthenticationException.class, CustomAuthenticationFailureEvent.class);
        defaultAuthenticationEventPublisher.setAdditionalExceptionMappings(mapping);

        // AuthenticaionEventPublisher::publishAuthenticationFailure API 는 AuthenticationEvent 타입의 파라미터를 취급한다.
        // AuthenticationEvent 이지만 어느 이벤트로도 매핑되지 않은 경우 기본 이벤트로 발행을 해야한다.
        // 기본 이벤트를 설정한다.
        defaultAuthenticationEventPublisher.setDefaultAuthenticationFailureEvent(CustomDefaultAuthenticationFailureEvent.class);

        return defaultAuthenticationEventPublisher;
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
