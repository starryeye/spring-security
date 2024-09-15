package dev.starryeye.custom_authenticate_authentication_provider.case_3;

import dev.starryeye.custom_authenticate_authentication_provider.CustomAuthenticationProvider;
import dev.starryeye.custom_authenticate_authentication_provider.CustomAuthenticationProvider2;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

public class SecurityConfig {

    /**
     * AuthenticationProvider 타입의 스프링 빈을 2 개 이상 등록하면 대체하는 것 없이 자식에 등록된다.
     *
     * 자식 : CustomAuthenticationProvider, CustomAuthenticationProvider2, AnonymousAuthenticationProvider
     * parent : DaoAuthenticationProvider
     */

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        AuthenticationManagerBuilder managerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        managerBuilder.authenticationProvider(customAuthenticationProvider());
        managerBuilder.authenticationProvider(customAuthenticationProvider2());

        http
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
        ;
        return http.build();
    }

    @Bean
    public AuthenticationProvider customAuthenticationProvider(){
        return new CustomAuthenticationProvider();
    }

    @Bean
    public AuthenticationProvider customAuthenticationProvider2(){
        return new CustomAuthenticationProvider2();
    }

    @Bean
    public UserDetailsService userDetailsService(){
        UserDetails user = User.withUsername("user").password("{noop}1111").roles("USER").build();
        return  new InMemoryUserDetailsManager(user);
    }
}
