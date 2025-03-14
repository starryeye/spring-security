package dev.starryeye.custom_oauth2_client_oauth2_client_configurer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class OAuth2ClientConfig {

    /**
     * OAuth2ClientConfigurer 에 의한 oauth2Client() api 초기화를 알아본다.
     *
     * 참고.
     * oauth2Login()
     *      client 인가 뿐만아니라 사용자 인증 처리까지 해주는 기능의 API 이다.
     * oauth2Client()
     *      client 인가까지만 처리해주는 기능의 API 이다.
     *
     * 참고
     *      "사용자가 authorization server 를 통해 client 에게 리소스 접근 권한을 부여" == "인가 받은 client" (client 인가)
     *
     * OAuth2ClientConfigurer..
     *      init, configurer 과정을 통해 AuthorizationCodeGrantConfigurer 의 init, configurer 과정을 진행시킨다.
     *
     * AuthorizationCodeGrantConfigurer..
     *      OAuth2AuthorizationCodeAuthenticationProvider 생성
     *          oauth2Login() 에서는 OAuth2LoginAuthenticationFilter 가 OAuth2AuthorizationCodeAuthenticationProvider 를 사용하여..
     *              authorization code 로 access token 을 얻는 요청을 하도록 하였다.
     *          여기서는 OAuth2AuthorizationCodeGrantFilter 가 OAuth2AuthorizationCodeAuthenticationProvider 를 사용하여 동일한 역할을 수행하도록 시킨다.
     *      OAuth2AuthorizationRequestRedirectFilter 생성
     *          authorization code grant 방식에서 authorization code 를 authorization server 로 요청하고 발급 받는 필터이다. (1단계)
     *          oauth2Login() api 에서도 동일한 클래스가 동일한 역할을 위해 사용된다.
     *      OAuth2AuthorizationCodeGrantFilter 생성
     *          authorization server 가 redirect 로 응답(authorization code) 주는 path 를 처리하는 filter 이다. (2단계)
     *          발급된 authorization code 로 access token 을 authorization server 로 요청하는 처리를 담당한다.
     *              주의, 사용자의 인증처리는 하지 않는다.
     *          oauth2Login() api 에서는 OAuth2LoginAuthenticationFilter 가 해당 처리를 담당한다.
     *              사용자의 인증처리까지 담당해준다.
     *          OAuth2AuthorizedClientRepository 를 사용하여 OAuth2AuthorizedClient (access token 과 principalName, clientRegistration 등이 적재) 를 저장한다.
     */

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .anyRequest().authenticated()
                )
//                .oauth2Login(Customizer.withDefaults())
                .oauth2Client(Customizer.withDefaults())
                ;

        return http.build();
    }
}
