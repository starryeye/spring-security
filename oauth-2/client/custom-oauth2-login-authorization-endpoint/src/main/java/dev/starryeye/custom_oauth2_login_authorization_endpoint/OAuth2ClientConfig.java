package dev.starryeye.custom_oauth2_login_authorization_endpoint;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class OAuth2ClientConfig {

    /**
     * OAuth2AuthorizationRequestRedirectFilter..
     *      OAuth2.0 Client 모듈에서 authorization server(인가서버) 로 부터 access token 을 얻기 위한
     *          첫 번째 단계인 authorization code(코드) 얻기를 담당하는 filter 이다.
     *      기본 값으로 "/oauth2/authorization/{registration id}" 로 요청이 오면 해당 필터가 처리한다.
     *          아래와 같이 authorizationEndpoint() 를 이용하면, path 를 변경할 수 있다.
     *      DefaultOAuth2AuthorizationRequestResolver 를 이용하여 인가서버에 코드 요청을 위한 정보 클래스(OAuth2AuthorizationRequest) 를 생성한다.
     *      OAuth2AuthorizationRequestRepository 를 이용하여 인가서버에 코드를 요청하고 응답받을때까지 OAuth2AuthorizationRequest 를 유지시킨다.
     *      코드 요청시 필요한 redirect url 은 "/login/oauth2/code/{registration id}" 를 기본 값으로 쓴다.
     *      해당 필터가 동작하도록 하는 요청 url(기본 값: /oauth2/authorization/{registration id}) 로 요청되는 상황은 두가지이다.
     *          1. 로그인 페이지(기본 값 : "/login")에서 registration name 의 링크 클릭 시
     *          2. 접근 권한이 없는 경우 LoginUrlAuthenticationEntryPoint 에 의해 redirect
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
                                .authorizationEndpoint(authorizationEndpointConfigurer ->
                                        authorizationEndpointConfigurer
                                                .baseUri("/oauth2/authorize") // 기본값은 "/oauth2/authorization" 이다.
                                )
                );

        return http.build();
    }
}
