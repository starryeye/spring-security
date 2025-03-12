package dev.starryeye.custom_oauth2_login_authorization_request_resolver;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

@Configuration
@RequiredArgsConstructor
public class OAuth2ClientConfig {

    private final ClientRegistrationRepository clientRegistrationRepository;

    /**
     * Authorization code grant with PKCE 를 Client 에 적용시키고 싶은데..
     *      authorization code 로 access token, id token 을 얻는 요청(2 단계 요청) 에서 client_secret 데이터를 함께 넣어줘야할때는..?
     *
     * OAuth2AuthorizationRequestResolver 를 자세히 알아야한다. (구현체 : DefaultOAuth2AuthorizationRequestResolver)
     *      Authorization code 를 authorization server 에 요청(1 단계 요청) 하기 위한 OAuth2AuthorizationRequest 를 생성한다.
     *      생성할때..
     *          ClientAuthenticationMethod.NONE.equals(clientRegistration.getClientAuthenticationMethod()) 조건을 보고 PKCE 용으로 OAuth2AuthorizationRequest 를 생성하게된다.
     *              따라서, application.yml 에 "client-authentication-method: none" 설정이 필요하다.
     *              OAuth2AuthorizationRequestCustomizers::applyPkce 로 설정 및 생성
     *                  code_challenge, code_challenge_method 값 생성
     *                      spring oauth2 client 는 기본적으로 S256 로 한다. (-> keycloak client 설정에서 advanced > Proof Key for Code Exchange Code Challenge Method 를 S256 으로 설정 필요)
     *              그런데, "client-authentication-method: none" 설정을 하면..
     *                  2 단계 요청을 할때.. client_secret 을 요청데이터에 넣지 않아 keycloak 에서 에러 리턴한다..
     *
     *              그래서, MyOAuth2AuthorizationRequestResolver 로 해결해보겠다.
     *                  "client-authentication-method: none" 이 아니라, "client-authentication-method: client_secret_basic" 으로 기본적인 authorization code grant 방식과 동일하게 셋팅하고..
     *                      registration id 를 바탕으로 분기하여 OAuth2AuthorizationRequest 를 생성하도록 한다.
     *                          pkce 용 registration id 로 요청이 오면 pkce 용 OAuth2AuthorizationRequest 를 생성하도록한다.
     *                      "client-authentication-method: client_secret_basic" 로 설정하였기 때문에 2 단계 요청에서 client_secret 을 요청데이터에 넣는다.
     *
     */

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .requestMatchers("/").permitAll()
                                .anyRequest().authenticated()
                )
                .oauth2Login(oAuth2LoginConfigurer ->
                        oAuth2LoginConfigurer
                                .authorizationEndpoint(authorizationEndpointConfigurer ->
                                        authorizationEndpointConfigurer
                                                .authorizationRequestResolver(myOAuth2AuthorizationRequestResolver())
                                )
                )
                .logout(httpSecurityLogoutConfigurer ->
                                httpSecurityLogoutConfigurer
                                        .logoutSuccessHandler(oidcLogoutSuccessHandler()) // front channel logout 기능
                                        .invalidateHttpSession(true)
                                        .clearAuthentication(true)
                                        .deleteCookies("JSESSIONID")
                )
                ;

        return http.build();
    }

    private OAuth2AuthorizationRequestResolver myOAuth2AuthorizationRequestResolver() {
        return new MyOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/oauth2/authorization");
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler() {

        OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        logoutSuccessHandler.setPostLogoutRedirectUri("http://localhost:8080/login");
        return logoutSuccessHandler;
    }
}
