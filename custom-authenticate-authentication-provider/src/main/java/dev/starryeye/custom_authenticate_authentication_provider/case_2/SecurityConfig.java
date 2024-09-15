package dev.starryeye.custom_authenticate_authentication_provider.case_2;

import dev.starryeye.custom_authenticate_authentication_provider.CustomAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * AuthenticationProvider 빈을 하나만 등록하면..
     * parent 에 자동으로 등록되던 DaoAuthenticationProvider 를 대체하게 된다.
     * -> 자식 : AnonymousAuthenticationProvider
     *    parent : CustomAuthenticationProvider
     *
     * 그래서.. DaoAuthenticationProvider 를 대체하지 않고 그대로 살리고 싶다면..
     * 아래와 같이 설정해줘야한다.
     */

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManagerBuilder builder, AuthenticationConfiguration configuration) throws Exception {

        /**
         * 자식 : CustomAuthenticationProvider, AnonymousAuthenticationProvider
         * parent : DaoAuthenticationProvider
         */

        // HttpSecurity 의 getSharedObject 를 통해 참조하면 자식 AuthenticationManagerBuilder 와 ProviderManager 를 참조할 수 있다.
        AuthenticationManagerBuilder managerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        managerBuilder.authenticationProvider(customAuthenticationProvider()); // 자식에 CustomAuthenticationProvider 추가

        // 스프링 빈 DI 를 활용하면 부모 AuthenticationManagerBuilder 와 ProviderManager 에 참조할 수 있다.
        ProviderManager providerManager = (ProviderManager)configuration.getAuthenticationManager(); // 부모 parent
        // AuthenticationProvider 타입으로 스프링 빈 CustomAuthenticationProvider 이 하나 등록되어있어서 부모 providerManager 에 Dao 대신 Custom 이 대체 되어있다.
        providerManager.getProviders().remove(0); // 대체된 Custom 삭제
        builder.authenticationProvider(new DaoAuthenticationProvider()); // 부모 빌더에 있는 부모 parent 에 Dao 를 추가한다

        http
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults()) // 폼 인증 방식
        ;
        return http.build();
    }

    @Bean
    public AuthenticationProvider customAuthenticationProvider(){
        return new CustomAuthenticationProvider();
    }

    @Bean
    public UserDetailsService userDetailsService(){
        UserDetails user = User.withUsername("user").password("{noop}1111").roles("USER").build();
        return new InMemoryUserDetailsManager(user);
    }
}
