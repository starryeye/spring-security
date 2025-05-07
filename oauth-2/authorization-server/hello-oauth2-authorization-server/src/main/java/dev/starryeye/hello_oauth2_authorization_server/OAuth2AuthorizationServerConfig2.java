package dev.starryeye.hello_oauth2_authorization_server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.util.UUID;

@Configuration
//@Import(OAuth2AuthorizationServerConfiguration.class)
public class OAuth2AuthorizationServerConfig2 {

    /**
     * HelloOauth2AuthorizationServerApplication 주석에서 설명한 두번째 방법..
     *
     * authorization-server 의존성에서 제공하는 OAuth2AuthorizationServerConfiguration::authorizationServerSecurityFilterChain 를 이용하여..
     * SecurityFilterChain 을 등록한다. (주의, authorization-server 의존성의 OAuth2AuthorizationServerConfiguration 이다.)
     *
     * https://docs.spring.io/spring-authorization-server/reference/getting-started.html
     * https://docs.spring.io/spring-authorization-server/reference/configuration-model.html
     *
     * SecurityFilterChain 은 OAuth2AuthorizationServerConfiguration 에서 등록해주는 것을 사용하고 나머지 필요한 설정은 직접 등록해준다. (application.yml 사용하지 않음)
     * -> RegisteredClientRepository, AuthorizationServerSettings
     *
     * 참고.
     * 1번 방법과 같이 직접 등록하지 않고 application.yml 를 이용하여도 된다.
     *
     * 참고.
     * application.yml 을 이용하지 않은 상태에서..
     * RegisteredClientRepository 는 빈 등록이 되지 않으면 실행자체가 안되지만..
     * AuthorizationServerSettings 는 빈 등록이 되지 않아도 실행은 되는데 issuer 정보가 빠진 상태로 초기화 되므로 주의할 것.
     */
//
//    @Bean
//    public RegisteredClientRepository registeredClientRepository() {
//        RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
//                .clientId("my-spring-client")
//                .clientSecret("{noop}secret")
//                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
//                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
//                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
//                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
//                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/my-spring-client")
//                .scope(OidcScopes.OPENID)
//                .scope(OidcScopes.PROFILE)
//                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
//                .build();
//
//        return new InMemoryRegisteredClientRepository(registeredClient);
//    }
//
//    @Bean
//    public AuthorizationServerSettings authorizationServerSettings() {
//        return AuthorizationServerSettings.builder()
//                .issuer("http://localhost:8091") // 아래 주석은 기본 값이다.
////                .authorizationEndpoint("/oauth2/v1/authorize")
////                .deviceAuthorizationEndpoint("/oauth2/v1/device_authorization")
////                .deviceVerificationEndpoint("/oauth2/v1/device_verification")
////                .tokenEndpoint("/oauth2/v1/token")
////                .tokenIntrospectionEndpoint("/oauth2/v1/introspect")
////                .tokenRevocationEndpoint("/oauth2/v1/revoke")
////                .jwkSetEndpoint("/oauth2/v1/jwks")
////                .oidcLogoutEndpoint("/connect/v1/logout")
////                .oidcUserInfoEndpoint("/connect/v1/userinfo")
////                .oidcClientRegistrationEndpoint("/connect/v1/register")
//                .build();
//    }
}
