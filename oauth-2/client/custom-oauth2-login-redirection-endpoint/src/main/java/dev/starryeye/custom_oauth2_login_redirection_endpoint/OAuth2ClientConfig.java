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
