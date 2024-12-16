package dev.starryeye.custom_multiple_security_filter_chain;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * 여러개의 SecurityFilterChain 을 만들고 요청 url 패턴에 따라
     * 서로 다른 SecurityFilterChain(보안 필터)를 적용 시켜본다.
     *
     * 초기화 과정.
     * WebSecurity 는 FilterChainProxy 를 만드는데..
     *      WebSecurity::performBuild 에서..
     *          FilterChainProxy filterChainProxy = new FilterChainProxy(securityFilterChains); 를 디버깅해보면..
     *          아래에서 생성한 두개의 SecurityFilterChain 이 전달되는 것을 볼 수 있다.
     *              두개의 SecurityFilterChain 은 설정한 것에 따라 서로 다른 보안 필터들을 가지고 있는 것을 볼 수 있다.
     *
     * 요청 과정.
     * FilterChainProxy 는 요청 url 패턴에 따라 어떤 SecurityFilterChain 을 적용할지 판단한다.
     *      FilterChainProxy::doFilterInternal 에서
     *          List<Filter> filters = getFilters(firewallRequest); 를 디버깅해보면.. (FilterChainProxy::getFilters(HttpServletRequest request))
     *          어떤 SecurityFilterChain 이 선택되는지 확인되고..
     *          최종적으로 선택된 SecurityFilterChain 의 필터들이
     *          들어온 요청에 대해 검사하게 된다.
     */

    @Order(1)
    @Bean
    public SecurityFilterChain securityFilterChain1(HttpSecurity http) throws Exception {

        /**
         * @Order(1) 을 적용하여 가장 우선순위 높은 SecurityFilterChain 으로 설정하였다.
         * securityMatchers 를 사용하여 "/api/**" url 패턴에 대해 적용하는 SecurityFilterChain 으로 설정하였다.
         *      해당 url 패턴에 매칭이 안되면 다음 SecurityFilterChain 으로 넘긴다.
         *
         * 참고
         * "/api/**" url 패턴의 요청은 이 SecurityFilterChain 으로 검사되고
         *      나머지 SecurityFilterChain 으로 검사하지는 않음
         */

        http
                .securityMatchers(requestMatcherConfigurer ->
                        requestMatcherConfigurer.requestMatchers("/api/**")
                )
                .authorizeHttpRequests(requestMatcherConfigurer ->
                        requestMatcherConfigurer
                                .anyRequest().permitAll()
                );

        return http.build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain2(HttpSecurity http) throws Exception {

        /**
         * @Order 어노테이션이 없으면 가장 우선순위가 낮은 것이다.
         * "/api/**" url 패턴에 매칭되지 않은 나머지는 이 SecurityFilterChain 으로 검사된다.
         */

        http
                .authorizeHttpRequests(requestMatcherConfigurer ->
                        requestMatcherConfigurer
                                .anyRequest().authenticated()
                )
                .formLogin(Customizer.withDefaults()); // form 로그인 설정

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.withUsername("user").password("{noop}1111").roles("USER").build();
        return new InMemoryUserDetailsManager(user);
    }
}
