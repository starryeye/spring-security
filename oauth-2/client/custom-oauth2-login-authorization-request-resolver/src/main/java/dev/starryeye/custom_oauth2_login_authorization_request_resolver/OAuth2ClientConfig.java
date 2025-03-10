package dev.starryeye.custom_oauth2_login_authorization_request_resolver;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

@Configuration
@RequiredArgsConstructor
public class OAuth2ClientConfig {

    private final ClientRegistrationRepository clientRegistrationRepository;

    /**
     * Authorization code grant with PKCE 를 Client 에 적용시키고 싶다면..
     *
     * OAuth2AuthorizationRequestResolver 를 자세히 알아야한다. (구현체 : DefaultOAuth2AuthorizationRequestResolver)
     *      Authorization code 를 authorization server 에 요청 하기 위해 만든 OAuth2AuthorizationRequest 를 생성한다.
     *      만들때..
     *          ClientAuthenticationMethod.NONE.equals(clientRegistration.getClientAuthenticationMethod()) 조건을 보고 PKCE 용으로 OAuth2AuthorizationRequest 만들게 된다.
     *              따라서, application.yml 에 "client-authentication-method: none" 설정이 필요하다.
     *              OAuth2AuthorizationRequestCustomizers::applyPkce 로 설정 및 생성
     *                  code_challenge, code_challenge_method 값 생성
     *                      spring oauth2 client 는 기본적으로 S256 로 한다. (-> keycloak client 설정에서 advanced > Proof Key for Code Exchange Code Challenge Method 를 S256 으로 설정 필요)
     *              그런데, "client-authentication-method: none" 설정을 하면..
     *                  authorization code 로 access token, id token 을 얻는 요청을 할때..
     *                      client_secret 을 요청데이터에 넣지 않아 keycloak 에서 에러 리턴한다..
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
                .oauth2Login(Customizer.withDefaults())
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

    private LogoutSuccessHandler oidcLogoutSuccessHandler() {

        OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        logoutSuccessHandler.setPostLogoutRedirectUri("http://localhost:8080/login");
        return logoutSuccessHandler;
    }
}
