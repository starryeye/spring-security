package dev.starryeye.custom_social_login_client_with_form_login.config;

import dev.starryeye.custom_social_login_client_with_form_login.service.security.CustomOAuth2UserService;
import dev.starryeye.custom_social_login_client_with_form_login.service.security.CustomOidcUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

@Configuration
@RequiredArgsConstructor
public class OAuth2ClientConfig {

    private final ClientRegistrationRepository clientRegistrationRepository;

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        /**
         * 전체 flow, 주요 클래스 및 메서드
         * 초기화
         *      OAuth2LoginConfigurer : init() / configure()
         * authorization code 를 발급 받기위한 요청 처리
         *      OAuth2AuthorizationRequestRedirectFilter
         *          /oauth2/authorization/{registration id} 요청 처리
         *          OAuth2AuthorizationRequest
         *              요청하기 위한 데이터 객체
         * access token 을 발급 받기위한 요청 처리 + 인증 처리
         *      OAuth2LoginAuthenticationFilter
         *          /login/oauth2/code/{registration id} 요청 처리 (authorization server 에서 사용자 웹브라우져 통해 redirect 요청}
         *          OAuth2LoginAuthenticationProvider
         *              OAuth2AuthorizationCodeAuthenticationProvider
         *                  access token 요청
         *              CustomOAuth2UserService (커스텀) 를 이용해 사용자 정보 GET (OAuth2User 생성)
         *          OidcAuthorizationCodeAuthenticationProvider
         *              id token, access token 요청
         *              JwtDecoder 를 통한 id token 검증
         *              CustomOidcUserService (커스텀) 를 이용해 사용자 정보 GET (OidcUser 생성)
         *          인증 객체 생성 및 저장
         *
         *
         * 참고..
         * naver..
         *      1. "/token" 요청에 대한 응답에 scope 필드를 응답하지 않아서 client 서버에서 권한 매핑이 정상 동작하지 않음..
         *          OAuth2AccessTokenResponse 에 scope 필드 값 존재하지 않음.
         *          https://developers.naver.com/docs/login/devguide/devguide.md#%EB%84%A4%EC%9D%B4%EB%B2%84%20%EB%A1%9C%EA%B7%B8%EC%9D%B8-%EA%B0%9C%EB%B0%9C%EA%B0%80%EC%9D%B4%EB%93%9C
         *          google, keycloak 의 경우 "/token" 요청에 대한 응답에 scope 필드가 존재하여 정상 권한 매핑 동작함. (ROLE_SCOPE_email, ROLE_SCOPE_profile 매핑됨)
         *              -> scope 로 매핑된 권한으로 client 에서 차단 처리할 필요는 없고 그냥 access token 으로 resources server 에 요청을 해버리고 access 에 대한 자격을 위임하는 편이 나을 수 도..
         *      2. OpenID Connect 기술 지원이 안됨. openid scope 로 요청해도 id token 발급 안됨.
         *
         */

        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .requestMatchers("/images/**", "/css/**", "/js/**").permitAll() // 정적파일접근 허용
                                .requestMatchers("/").permitAll()
                                .requestMatchers("/api/oauth2-oidc-user").hasAnyAuthority("ROLE_OAUTH2_USER", "ROLE_OIDC_USER")
                                .requestMatchers("/api/scope-profile").hasAuthority("ROLE_SCOPE_profile")
                                .requestMatchers("/api/scope-openid").hasAuthority("ROLE_SCOPE_openid")
                                .anyRequest().authenticated()
                )
                .formLogin(httpSecurityFormLoginConfigurer ->
                        // formLogin 의 loginPage 는 폼 로그인, oauth2Login 의 loginPage 는 oauth2 로그인 페이지가 기본이다.
                        // 여기서는 커스텀 login page(폼 로그인 + social 로그인) 으로 만들어본다.
                        httpSecurityFormLoginConfigurer
                                .loginPage("/login")
                                .loginProcessingUrl("/login-process")
                                .defaultSuccessUrl("/")
                                .permitAll()
                )
                .oauth2Login(oAuth2LoginConfigurer ->
                        oAuth2LoginConfigurer
                                .userInfoEndpoint(userInfoEndpointConfig ->
                                        userInfoEndpointConfig
                                                .userService(customOAuth2UserService)
                                                .oidcUserService(customOidcUserService)
                                )
                )
                .logout( // front channel logout setting
                httpSecurityLogoutConfigurer ->
                        httpSecurityLogoutConfigurer
                                .logoutSuccessHandler(oidcLogoutSuccessHandler())
                                .invalidateHttpSession(true)
                                .clearAuthentication(true)
                                .deleteCookies("JSESSIONID")
                )
                .exceptionHandling(httpSecurityExceptionHandlingConfigurer ->
                        httpSecurityExceptionHandlingConfigurer
                                // 인증 실패 시, 로그인 페이지로 보낸다.
                                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")
                                )
                )
        ;

        return http.build();
    }

    @Bean
    public GrantedAuthoritiesMapper grantedAuthoritiesMapper() {
        return new CustomGrantedAuthoritiesMapper();
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler() {
        OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        logoutSuccessHandler.setPostLogoutRedirectUri("http://localhost:8080/");
        return logoutSuccessHandler;
    }

}
