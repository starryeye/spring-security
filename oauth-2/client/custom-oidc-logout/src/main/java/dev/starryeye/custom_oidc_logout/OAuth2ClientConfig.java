package dev.starryeye.custom_oidc_logout;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

@Configuration
@RequiredArgsConstructor
public class OAuth2ClientConfig {

    private final ClientRegistrationRepository clientRegistrationRepository;

    /**
     * OAuth 2.0 client 에서 인증 후, 로그아웃을 할 때는..
     * 2가지 작업이 필요하다.
     *      1. Client 에서 사용자의 웹 브라우저에 대한 세션과 쿠키를 지운다.
     *      2. OpenID Provider (Authorization server) 에 세션 로그아웃 요청을 한다.
     */

    // oidcLogout api 를 사용하는 방법
//    @Bean
//    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//
//        http
//                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
//                        authorizationManagerRequestMatcherRegistry
//                                .anyRequest().authenticated()
//                )
//                .oauth2Login(Customizer.withDefaults())
//                .logout(AbstractHttpConfigurer::disable)
//                .oidcLogout(Customizer.withDefaults())
//                ;
//
//        return http.build();
//    }

    // logout api 를 사용하는 방법
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        /**
         * 참고.
         * Spring security 에서 logout() api 를 관장하는 filter 는..
         *      LogoutFilter 이다.
         *          기본적으로 "/logout" path 로 작동한다.
         *          LogoutFilter 에서 client 의 로그아웃 처리를 완료하고 logoutSuccessHandler() 으로 등록한..
         *              OidcClientInitiatedLogoutSuccessHandler 를 호출하는 것이다.
         */

        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .anyRequest().authenticated()
                )
                .oauth2Login(Customizer.withDefaults())
                .logout(
                        httpSecurityLogoutConfigurer ->
                                httpSecurityLogoutConfigurer
                                        .logoutSuccessHandler(oidcLogoutSuccessHandler()) // 로그아웃이 성공하면 호출할 헨들러 설정
                                        .invalidateHttpSession(true) // 로그아웃 시, 세션 삭제 처리
                                        .clearAuthentication(true) // 로그아웃 시, 인증 객체 삭제 처리
                                        .deleteCookies("JSESSIONID") // 로그아웃 시, 쿠키 삭제 처리
                )
        ;

        return http.build();
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler() {

        /**
         * logout() API 를 사용하여 client 에서 사용자의 웹브라우저에 대한 세션 쿠키를 삭제하고..
         * OidcClientInitiatedLogoutSuccessHandler 를 이용하여,
         *      OpenID Provider (Authorization server) 에 세션 로그아웃 요청을 하도록 한다.
         *          요청 주소는 http://localhost:8090/realms/custom-realm/protocol/openid-connect/logout
         *              clientRegistrationRepository 에 의해 알수 있다.
         *                  http://localhost:8090/realms/custom-realm/.well-known/openid-configuration 로 땡겨옴..
         *      OpenID provider 에게 로그아웃 요청을 보낼때 redirect 할 url 을 설정한다. (보통 client 의 login url)
         *          authorization server 에도 redirect url 을 허용하도록 설정해줘야한다.
         *              keycloak 의 경우.. Valid post logout redirect URIs 설정에 추가해줘야함
         */

        OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        logoutSuccessHandler.setPostLogoutRedirectUri("http://localhost:8080/login");
        return logoutSuccessHandler;
    }
}
