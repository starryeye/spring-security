package dev.starryeye.custom_oauth2_login_login_page;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class OAuth2ClientConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        /**
         * 현재 설정에 따르면 모든 요청에 대해 인증이 필요하다고 설정이 되어있다.
         * -> 인증은 oauth2Login 에 의해 OAuth 2.0 으로 인증이 이루어진다.
         * 1. 어떤 path 로 요청을 하면 인증이 필요하다.
         * 2. 인증을 위해서 기본 client 로그인 페이지(아래 설명)로 redirect.. 되어야 할 것 같지만..
         *      실제로는 바로 /oauth2/authorization/{registration id} 요청을 한 효과가 나타난다.
         *          이유 : 접근 권한이 없는 경우, LoginUrlAuthenticationEntryPoint(기본) 에 설정된 Url 로 이동된다. ("/oauth2/authorization/{registration id}")
         *
         * oauth2Login 의 기본 설정에 따르면,
         * DefaultLoginPageGeneratingFilter 에 의해 기본 client 로그인 페이지가 생성된다. ("/login")
         * 1. 해당 로그인 페이지에 접속하면..
         * 2. registration 설정의 client-name 문자열(현재 예제에서 My keycloak)의 링크가 보인다.
         * 3. 해당 링크를 누르면, /oauth2/authorization/{registration id} 로 client 에게 요청된다.
         * 4. 해당 요청이 들어오면, 요청을 처리하는 필터(OAuth2AuthorizationRequestRedirectFilter)가
         *      authorization server(현재 예제에서 keycloak) 로 인가 요청(현재 예제에서 authorization code grant)을 시작한다.
         * 5. authorization server 는 사용자에게 승인을 획득하기 위해 authorization server 의 로그인 페이지를 보여준다.
         *
         * 참고.
         *      로그인을 한 상태에서 "/login" 페이지에 접근하면 접근이 되나..
         *          링크를 눌러 /oauth2/authorization/{registration id} 로 접근을 하면..
         *              http://localhost:8090/realms/custom-realm/protocol/openid-connect/auth~~ 로 redirect
         *              http://localhost:8080/login/oauth2/code/my-keycloak~~ 로 redirect 를 거치고
         *              최종적으로 "http://localhost:8080/ 로 redirect 된다.. (todo, 왜 "/" 로 redirect 되는지 보기 requestCache 쪽 처리인가?)
         *
         */

        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .anyRequest().authenticated()
                )
                .oauth2Login(Customizer.withDefaults());

        /**
         * 아래와 같이 loginPage api 를 이용하면..
         * 기본적으로 DefaultLoginPageGeneratingFilter 가 생성해주는 로그인 페이지가 아니라
         * 개발자가 직접 커스텀한 페이지가 보여진다.
         */
//        http
//                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
//                        authorizationManagerRequestMatcherRegistry
//                                .requestMatchers("/custom-login-page").permitAll()
//                                .anyRequest().authenticated()
//                )
//                .oauth2Login(httpSecurityOAuth2LoginConfigurer ->
//                        httpSecurityOAuth2LoginConfigurer
//                                .loginPage("/custom-login-page")
//                );

        return http.build();
    }
}
