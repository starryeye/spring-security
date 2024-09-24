package dev.starryeye.custom_authenticate_session_registry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.authorizeHttpRequests(auth ->
                auth.anyRequest().authenticated()
        )
                .formLogin(Customizer.withDefaults())
                .sessionManagement(httpSecuritySessionManagementConfigurer ->
                        httpSecuritySessionManagementConfigurer
                                .maximumSessions(2) // 동시 세션 허용 수 2
                                .maxSessionsPreventsLogin(false) // 사용자 인증 강제 만료 정책 적용
                );

        return http.build();
    }

    @Bean
    public SessionRegistry sessionRegistry() {

        /**
         * SessionRegistry..
         * - 세션 관리와 관련된 정보를 추적하고 관리한다.
         * - 주로 동시 로그인 제한과 같은 세션 관리를 구현할 때나 현재 생성되어있는 세션들의 모니터링 시에 사용된다.
         * - SessionRegistry 는 auto-configuration 에 의해 자동으로 생성되지 않음
         *
         * 기능
         * - 애플리케이션에서 활성화된 모든 세션을 추적한다.
         * - 각 세션에 어떤 사용자가 연결되어 있는지를 추적한다.
         * - 여러 세션이 동일한 사용자에 의해 사용될 수 있기 때문에, 동일한 사용자에 대한 여러 세션을 관리하는 역할도 수행한다.
         *
         * 참고.
         * SessionRegistry 와 SecurityContextRepository 의 관계
         * SecurityContextRepository 가 인증 정보를 저장하는 곳(예: HttpSession)을
         * SessionRegistry 가 관리할 수 있는 세션으로 포함시킬 수 있다.
         * 예를 들어, HttpSessionSecurityContextRepository 를 사용할 때 세션이 생성되면,
         * SessionRegistry 에서 이 세션을 관리할 수 있다.
         */

        return new SessionRegistryImpl();
    }

    @Bean
    public UserDetailsService userDetailsService() {

        UserDetails userDetails = User.withUsername("user").password("{noop}1111").roles("USER").build();
        return new InMemoryUserDetailsManager(userDetails);
    }
}
