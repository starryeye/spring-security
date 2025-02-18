package dev.starryeye.custom_oauth2_login_oauth2_login_configurer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class OAuth2ClientConfig {

    /**
     * OAuth2LoginConfigurer 에 의한 oauth2Login() api 초기화를 알아본다.
     *
     * SecurityFilterChain 빈을 직접 등록하지 않으면, OAuth2WebSecurityConfiguration 의 내부 클래스인
     *      OAuth2SecurityFilterChainConfiguration::oauth2SecurityFilterChain 에 의해 기본 빈이 등록된다.
     *
     * OAuth2LoginConfigurer 는..
     *      HttpSecurity 의 oauth2Login api 가 다루는 SecurityConfigurer 이다.
     *      authorizationEndPointConfig 를 설정한다. (Authorization code 발급 관련)
     *      redirectionEndPointConfig 를 설정한다. (Authorization code redirect 처리 관련)
     *      tokenEndPointConfig 를 설정한다. (access token 발급 관련)
     *      userinfoEndPointConfig 를 설정한다. (userinfo 요청 관련)
     *
     * OAuth2LoginConfigurer::init 과정에서..
     *      OAuth2LoginAuthenticationFilter(아래 설명) 가 생성된다.
     *          access token 발급 관련 필터
     * OAuth2LoginConfigurer::configurer 과정에서..
     *      OAuth2AuthorizationRequestRedirectFilter 가 생성된다.
     *          authorization code 발급 관련 필터
     *
     *
     *
     *
     * OAuth2LoginAuthenticationFilter 는..
     *      /login/oauth2/code/{registration id} 로 요청오면 처리하는 필터이다.
     *      authorization code grant 방식에서 authorization code 로 access token 을 발급받는 단계에서 사용된다.
     *      OAuth2LoginAuthenticationProvider(아래 설명) 에 authorization server 와 통신하는 역할을 위임한다.
     *      clientRegistrationRepository 를 가진다. (인가서버 요청에 필요한 정보 담김)
     *      authorizedClientRepository 를 가진다. (인가서버에 요청한 응답 결과가 담김)
     *      authorizationRequestRepository 를 가진다. (인가서버 요청에 사용할 매개변수 담김)
     *
     * OAuth2LoginAuthenticationProvider 는..
     *      authorization server 와 통신하는 역할을 담당한다.
     *      userService 를 가진다. (access token 으로 userinfo 요청 담당)
     */

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.authorizeHttpRequests(authorizeRequests ->
                authorizeRequests.anyRequest().authenticated()
        )
                .oauth2Login(Customizer.withDefaults());

        return http.build();
    }
}
