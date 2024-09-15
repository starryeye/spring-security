package dev.starryeye.custom_authenticate_authentication_manager.case_2;

import dev.starryeye.custom_authenticate_authentication_manager.CustomAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationProvider;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

//@Configuration
public class SecurityConfig {

    /**
     * AuthenticationManager 를 직접 생성하고 필터에 등록할 수 있다.
     *
     * case_1 보다 좀더 세부적인 설정을 할 수 있다. (manager 가 사용 할 provider 까지 직접 설정 가능)
     */

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/","/api/login").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(customFilter(http), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private CustomAuthenticationFilter customFilter(HttpSecurity http) {

        /**
         * 아래와 같이 설정할 경우..
         * provider 우선순위는 anonymous -> custom -> dao 가 되는데
         *
         * anonymous 는 수행자격이 안되어 패스하고 custom provider 에 의해 인증이 수행된다.
         */

        // AuthenticationManager 가 사용할 AuthenticationProvider 리스트 생성
        List<AuthenticationProvider> list1 = List.of(new DaoAuthenticationProvider());
        // AuthenticationManager 의 구현체인 ProviderManager 를 직접 생성
        ProviderManager parent = new ProviderManager(list1);
        // AuthenticationManager 가 사용할 AuthenticationProvider 리스트 생성
        List<AuthenticationProvider> list2 = List.of(new AnonymousAuthenticationProvider("key"), new CustomAuthenticationProvider());
        // AuthenticationManager 를 생성하면서, 추가적으로 사용할 AuthentiationManager 도 넣어준다.
        ProviderManager authenticationManager = new ProviderManager(list2, parent);

        CustomAuthenticationFilter customAuthenticationFilter = new CustomAuthenticationFilter(http);
        customAuthenticationFilter.setAuthenticationManager(authenticationManager);

        return customAuthenticationFilter;

    }

    @Bean
    public InMemoryUserDetailsManager inMemoryUserDetailsManager() {
        UserDetails userDetails = User.withUsername("user")
                .password("{noop}1111")
                .authorities("USER")
                .build();

        return new InMemoryUserDetailsManager(userDetails);
    }
}
