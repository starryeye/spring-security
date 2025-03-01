package dev.starryeye.custom_oauth2_login_redirection_endpoint;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class OAuth2ClientConfig {

    /**
     * OAuth2LoginAuthenticationFilter..
     *      OAuth2.0 Client 모듈에서 authorization server(인가서버) 로 부터 access token 을 얻기 위한
     *          두 번째 단계인 access token(토큰) 얻기를 담당하는 filter 이다.
     *      기본 값으로 "/login/oauth2/code/{registration id}" 로 요청이 오면 해당 필터가 처리한다.
     *          아래와 같이 redirectionEndpoint() 를 이용하면, path 를 변경할 수 있다. (주의, application.yml 에서 registration > redirect-uri 설정 값도 함께 변경해줘야 한다.)
     *          OAuth2AuthorizationRequestRedirectFilter 에서 authorization code 를 얻기위해 redirect url 을 함께 보내는데 그 url 이다.
     *              결국, authorization server 가 resource owner 를 통해 authorization code 를 redirect 요청을 받는 필터이다.
     *      첫 단계에서 authorization code 를 얻기위한 요청의 쿼리파라미터에 state 값을 추가해서 authorization server 로 요청하면..
     *          authorization server 가 state 값을 다시 그대로 redirect 해주는데 OAuth2LoginAuthenticationFilter(하위 설명) 에서 값이 동일한지 검증한다.(csrf)
     *      access token 을 얻으면 해당 토큰으로 OAuth2LoginAuthenticationToken 객체를 만든다.
     *      OAuth2LoginAuthenticationToken 을 AuthenticationManager 로 전달하며 최종 인증처리를 AuthenticationManager 에게 위임한다.
     *      OAuth2AuthorizedClientRepository 를 사용하여 OAuth2AuthorizedClient 를 저장한다.
     *      최종 인증 객체인 OAuth2AuthenticationToken 을 생성하게 되고 SecurityContext 에 저장한다.
     *
     * OAuth2LoginAuthenticationFilter 의 주요 클래스
     *      OAuth2LoginAuthenticationProvider.. (OAuth2LoginAuthenticationFilter 에서 AuthenticationManager 에 의해 호출되는듯..)
     *          authorization code 를 access token 으로 교환하는 처리와 최종 인증 처리를 담당한다..
     *              scope 에 "openid" 값이 포함되어 있으면, OidcAuthorizationCodeAuthenticationProvider(하위 설명) 를 이용하고
     *              아니면, OAuth2AuthorizationCodeAuthenticationProvider(하위 설명) 를 이용한다.
     *
     * OidcAuthorizationCodeAuthenticationProvider..
     *      OpenID Connect 프로토콜을 사용한다. id token 으로 인증처리를 한다.
     *      OidcUserService 에게 인증 처리를 위임한다.
     *          OidcUserService 는 DefaultOidcUser(사용자 정보 객체)를 만들어 낸다.
     *      authorization server 와의 실제 통신은 DefaultAuthorizationCodeTokenResponseClient 를 이용한다.
     * OAuth2AuthorizationCodeAuthenticationProvider..
     *      access token 을 얻는다.
     *      DefaultOAuth2UserService 에게 인증 처리를 위임한다.
     *          DefaultOAuth2UserService 는 access token 으로 userinfo 를 얻어서 인증 처리를 한다.
     *          DefaultOAuth2UserService 는 DefaultOAuth2User(사용자 정보 객체)를 만들어 낸다.
     *      authorization server 와의 실제 통신은 DefaultAuthorizationCodeTokenResponseClient 를 이용한다.
     *
     * 참고
     * access token 을 전달해주는 authorization server 의 응답 데이터에 scope 가 포함되어있는데
     * 해당 값이 권한 객체로 매핑된다.
     */

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .anyRequest().authenticated()
                )
                .oauth2Login(oAuth2LoginConfigurer ->
                        oAuth2LoginConfigurer
                                .redirectionEndpoint(redirectionEndpointConfig ->
                                        redirectionEndpointConfig
                                                .baseUri("/login/oauth2/code/custom/*") // 기본 값은 "/login/oauth2/code/*" 이다.
                                )
                );

        return http.build();
    }
}
