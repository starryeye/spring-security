package dev.starryeye.custom_authorize_access;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.DefaultHttpSecurityExpressionHandler;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ApplicationContext applicationContext) throws Exception {

        /**
         * requestMatcher api 이후 기본적으로 제공되는 hasRole 과 같은 권한 규칙을..
         * access 를 통해 좀더 복잡하게 커스텀하는 방법을 알아본다.
         */

        // 표현식을 처리할 핸들러 생성
        DefaultHttpSecurityExpressionHandler expressionHandler = new DefaultHttpSecurityExpressionHandler();
        expressionHandler.setApplicationContext(applicationContext);

        // 생성한 표현식 핸들러를 사용할 표현식 매니저 생성
        WebExpressionAuthorizationManager expressionAuthorizationManager = new WebExpressionAuthorizationManager(
                "@customWebSecurity.check(authentication, request)" // customWebSecurity 이름을 가진 빈 인스턴스를 이용하여 권한 검사를 한다.
        );
        expressionAuthorizationManager.setExpressionHandler(expressionHandler);

        http.authorizeHttpRequests(authorization ->
                        authorization
                                // 커스텀 표현식 핸들러를 사용하는 표현식 매니저를 적용
                                .requestMatchers("/resource/**").access(expressionAuthorizationManager)

                                // "name" path variable 값이 인증 객체(Authentication) 의 name 과 동일해야 해당 경로에 접근이 된다.
                                .requestMatchers("/users/{name}").access(
                                        new WebExpressionAuthorizationManager("#name == authentication.name")
                                )

                                // 아래는 requestMatchers("/admin/db").hasAnyRole("DB", "ADMIN") 과 동일한 의미이다.
                                // requestMatchers("/admin/db").access(anyOf(hasAuthority("ROLE_DB"), hasRole("ADMIN"))) 이것도 동일하다.
                                .requestMatchers("/admin/db").access(new WebExpressionAuthorizationManager("hasAuthority('ROLE_DB') or hasRole('ADMIN')"))

                                .anyRequest().authenticated()
                )
                .formLogin(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.withUsername("user").password("{noop}1111").roles("USER").build();
        UserDetails manager = User.withUsername("db").password("{noop}1111").roles("DB").build();
        UserDetails admin = User.withUsername("admin").password("{noop}1111").roles("ADMIN", "SECURE").build();
        return new InMemoryUserDetailsManager(user, manager, admin);
    }
}
