package dev.starryeye.custom_authorize_granted_authority;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.authorizeHttpRequests(authorization ->
                authorization
                        .requestMatchers("/").hasRole("ANONYMOUS") // permitAll 을 하기 보다는 이렇게 관리하는게.. 더 좋을 듯..
                        .requestMatchers("/user").hasRole("USER")
                        .requestMatchers("/admin").hasRole("ADMIN")
                        .requestMatchers("/db").hasRole("DB")
                        .anyRequest().authenticated()
        )
                .anonymous(httpSecurityAnonymousConfigurer ->
                        // anonymous 의 경우 GrantedAuthorityDefaults 에 의해 자동화 되지 않으므로 권한 문자열을 직접 설정 해줘야함
                        httpSecurityAnonymousConfigurer.authorities("MY_ROLE_ANONYMOUS")
                )
                .formLogin(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public GrantedAuthorityDefaults grantedAuthorityDefaults() {

        /**
         * GrantedAuthority..
         * 1. Spring Security 는 Authentication 에 GrantedAuthority (권한 객체) 목록을 저장한다.
         *  - 인증 주체에 권한들을 부여
         *  - Authentication 객체를 통해 가지고 있는 GrantedAuthority 들을 참조할 수 있다.
         *
         * 2. GrantedAuthority 객체는 AuthenticationManager 에 의해 Authentication 객체에 삽입된다.
         * 3. 요청 경로의 권한과 인증 주체의 권한을 비교 검증 로직은..
         *      AuthoritiesAuthorizationManager::isAuthorized 에서 진행한다.
         *
         *
         * Spring Security 는 기본적으로 "ROLE_" 접두어가 붙은 권한 문자열로 관리된다.
         * -> GrantedAuthorityDefaults 빈을 아래와 같이 생성자로 생성하여 등록하면 커스텀한 권한 문자열 접두어를 사용할 수 있다.
         * -> SecurityFilterChain 설정에서 requestMatcher 와 hasRole 에 해당 설정이 자동적으로 설정되는 것이고..
         *      UserDetailsService 에서 roles() 로 설정한 것은 자동으로 설정되지 않으므로 authorities() 를 사용해야 정상 설정된다. (roles 메서드 내부확인하면 ROLE_ 를 붙인다.)
         */

        return new GrantedAuthorityDefaults("MY_ROLE_");
    }

    @Bean
    public UserDetailsService userDetailsService() {
//        UserDetails user = User.withUsername("user").password("{noop}1111").roles("USER").build(); // 이렇게 하면 ROLE_USER 가 되어버린다.
        UserDetails user = User.withUsername("user").password("{noop}1111").authorities("MY_ROLE_USER").build();
        UserDetails db = User.withUsername("db").password("{noop}1111").authorities("MY_ROLE_DB").build();
        UserDetails admin = User.withUsername("admin").password("{noop}1111").authorities("MY_ROLE_ADMIN", "MY_ROLE_SECURE").build();
        return new InMemoryUserDetailsManager(user, db, admin);
    }

    @Bean
    public RoleHierarchy roleHierarchy() {

        /**
         * 계층적 권한 설정에서는 withRolePrefix 로 권한 문자열 접두어를 설정해줄 수 있다.
         */

        return RoleHierarchyImpl.withRolePrefix("MY_ROLE_")
                .role("ADMIN").implies("DB", "USER", "ANONYMOUS")
                .role("DB").implies("USER", "ANONYMOUS")
                .role("USER").implies("ANONYMOUS")
                .build();
    }
}
