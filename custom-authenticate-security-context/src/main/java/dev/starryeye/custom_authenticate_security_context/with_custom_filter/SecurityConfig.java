package dev.starryeye.custom_authenticate_security_context.with_custom_filter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    /**
     * SecurityContextConfigurer::configure
     * - SecurityContextHolderFilter 를 생성한다.
     * - 초기화 과정에서 생성되어 HttpSecurity 에 공유되고 있는 SecurityContextRepository 를 SecurityContextHolderFilter 에 설정한다.
     *
     * FormLoginConfigurer::configure
     * - UsernamePasswordAuthenticationFilter 를 생성한다.
     * - SecurityContextConfigurer 로 부터 설정된 SecurityContextRepository 를 받아서 UsernamePasswordAuthenticationFilter 에도 설정한다.
     *      UsernamePasswordAuthenticationFilter 는 인증 성공 시 SecurityContextRepository 를 이용하여 HttpSession 에 SecurityContext 를 저장한다.
     *          추후 다음 요청 시, SecurityContextHolderFilter 에서 SecurityContext 를 조회하여 인증을 유지하기 위함이다.
     *
     */

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        /**
         * 커스텀 AuthenticationFilter 를 만들기 위해서는 AuthenticationManager 가 필요하고 설정해줘야한다.
         */
        AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        AuthenticationManager authenticationManager = authenticationManagerBuilder.build();

        http.authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers("/api/login").permitAll()
                                .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                .securityContext(httpSecuritySecurityContextConfigurer ->
                        // false 로 두면, SecurityContextHolderFilter 가 아닌 SecurityContextPersistenceFilter(deprecated) 로 동작하게 된다.
                        httpSecuritySecurityContextConfigurer.requireExplicitSave(true) // 기본 값 true
                )
                .authenticationManager(authenticationManager) // 다른 Filter 들이 사용할 AuthenticationManager
                .addFilterBefore(customAuthenticationFilter(http, authenticationManager), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private CustomAuthenticationFilter customAuthenticationFilter(HttpSecurity http, AuthenticationManager authenticationManager) {

        /**
         * 커스텀 AuthenticationFilter 생성
         * 다른 Filter 들이 사용할 AuthenticationManager 를 공통적으로 사용하도록 설정해줌.
         */
        CustomAuthenticationFilter customAuthenticationFilter = new CustomAuthenticationFilter(http);
        customAuthenticationFilter.setAuthenticationManager(authenticationManager);

        return customAuthenticationFilter;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.withUsername("user").password("{noop}1111").roles("USER").build();
        return new InMemoryUserDetailsManager(user);
    }
}
