package dev.starryeye.server_b;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        /**
         * Spring Security 의 CORS 기능의 관련 필터는
         * org.springframework.web.filter 의 CorsFilter 이다.
         *
         * CorsFilter 는 Spring Security 의 인증 관련 필터들 보다 먼저 수행된다.
         * - 사전 요청이면 다음 필터로 넘기지 않고 적절한 응답 셋팅 후에 바로 client 로 응답해버린다.
         * - simple request, preflight request 방법 모두에 대해.. isValid 를 수행해보는데..
         *      웹 브라우저가 수행할 일을 서버에서 수행해버리는 듯..
         *          요청의 orgin 헤더 값 과 해당 B 서버의 CORS 셋팅을 비교해서 일치되지 않으면 다음 필터로 넘기지 않는듯..
         *
         * 참고
         * 웹브라우저가 아닌 Intellij client 에서 요청을 할 경우..
         * Origin 헤더는 자동으로 생성되어 요청되지 않고.. (요청에 포함되지 않음)
         * Spring Security CorsFilter 는 Origin 이 없으면 그냥 Cors 필터에서 아무것도 안하고 그냥 다음 필터로 넘긴다.
         * 만약, Origin 헤더를 수동으로 설정하여 요청을 하면 CORS 가 정상 동작한다.
         * -> server-b/http/api.http 참고..
         *
         * 참고
         * CORS 는 JavaScript 에 의한 요청만 해당한다고 한다.. Html 태그로 요청 하는 것은 해당사항 없다고 한다..
         *
         * 참고
         * Simple Request 는 아래 3가지 조건을 지켜야 한다. 하나라도 지켜지지 않으면 Preflight Request 로 동작한다.
         * 1. GET, POST, HEAD
         * 2. Accept, Accept-Language, Content-Language, Content-Type, DPR, Downlink, Save-Data, Viewport-Width, Width 들만 사용되어야함.
         * 3. Content-Type 은 application/x-www-form-urlencoded, multipart/form-data, text/plain 만 사용되어야함.
         */

        http.authorizeHttpRequests(auth ->
                auth.anyRequest().permitAll() // 모든 요청을 인증 없이 허용
        )
                .cors(httpSecurityCorsConfigurer ->
                        httpSecurityCorsConfigurer
                                .configurationSource(corsConfigurationSource())
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        // http://localhost:8080 (스키마+호스트+포트) 가 모두 동일해야 동일 출처, 셋 중 하나라도 다르게 사용한다면 ...
        // -> 웹 브라우저는.. simple request 라면 해당 응답을 사용자에게 노출하지 않고, preflight request 라면 본 요청은 수행하지 않는다.
        configuration.addAllowedOrigin("http://localhost:8080"); // server A 에 대해 허용
        configuration.addAllowedMethod("*");
        configuration.addAllowedHeader("*");
        configuration.setAllowCredentials(true); // true 로 하면, addAllowedOrigin 에 와일드카드("*") 를 사용할 수 없음.
        configuration.setMaxAge(3600L); // 웹브라우저가 preflight request 를 한번 성공되었다면 일정 시간동안 예비요청은 수행하지 않고 본 요청을 바로 수행한다.

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // 모든 다른 출처 요청에 대해 CORS 를 적용하겠다.

        return source;
    }
}
