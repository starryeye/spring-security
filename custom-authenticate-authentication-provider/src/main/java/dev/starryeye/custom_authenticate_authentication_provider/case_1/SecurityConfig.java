package dev.starryeye.custom_authenticate_authentication_provider.case_1;

import dev.starryeye.custom_authenticate_authentication_provider.CustomAuthenticationProvider;
import dev.starryeye.custom_authenticate_authentication_provider.CustomAuthenticationProvider2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

//@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        /**
         * AuthenticationProvider 를 직접 생성하고 직접 등록할 수 있다.
         *
         * 최종 provider 등록 상태
         * 자식 : CustomAuthenticationProvider, CustomAuthenticationProvider2, AnonymousAuthenticationProvider
         * parent : DaoAuthenticationProvider
         *
         * 참고
         * AuthenticationManager 프로젝트에서는 AuthenticationManagerBuilder 를 참조하여
         * AuthenticationManager 를 생성하고 HttpSecurity 에 등록까지 해줬었는데.. 여기서는 왜 생성 등록 작업이 없지?
         * -> AuthenticationManager 프로젝트 에서는 커스텀 필터를 생성했기 때문에 명시적으로 Manager 를 등록해줘야할 필요가 있었지만..
         *      해당 프로젝트에서는 커스텀 필터를 이용하지 않기 때문에 해당 프로젝트에서 사용할.. manager 는
         *      auto-configuration 에 의해 filter 와 manager 가 자동으로 생성 설정된다.
         *
         * 그럼 어느 필터에 적용될 줄 알고 빌더에 설정만 해놓는 건가요?
         * -> spring security 에서 사용되는 다양한 AuthenticationFilter 들이 인증을 위해 호출하는 AuthenticationManager 는
         *      런타임에 싱글톤으로 단 하나의 인스턴스이다.
         */

        AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        // 아래 주석과 동일한 방법이다.
        authenticationManagerBuilder.authenticationProvider(new CustomAuthenticationProvider());
        authenticationManagerBuilder.authenticationProvider(new CustomAuthenticationProvider2());

        http
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults()) // 폼 인증
//                .authenticationProvider(new CustomAuthenticationProvider())
//                .authenticationProvider(new CustomAuthenticationProvider2())
        ;
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(){
        UserDetails user = User.withUsername("user").password("{noop}1111").roles("USER").build();
        return  new InMemoryUserDetailsManager(user);
    }
}
