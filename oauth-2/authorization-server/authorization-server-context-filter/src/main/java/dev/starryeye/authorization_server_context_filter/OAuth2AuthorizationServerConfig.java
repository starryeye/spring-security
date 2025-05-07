package dev.starryeye.authorization_server_context_filter;

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
@Import(OAuth2AuthorizationServerConfiguration.class)
public class OAuth2AuthorizationServerConfig {

    /**
     * SecurityFilterChain 은 @Import(OAuth2AuthorizationServerConfiguration.class) 에서 생성해준 것을 사용
     *
     * OAuth2AuthorizationServerConfigurer 설정에서 생성하는 AuthorizationServerContextFilter 에 대해 알아본다.
     *
     * AuthorizationServerContextFilter..
     *      client 가 요청할 때마다.. AuthorizationServerContext 를 생성한다.
     *      AuthorizationServerContext 안에는 AuthorizationServerSettings(아래 빈으로 등록) 라는 객체가 있고
     *      AuthorizationServerSettings 내부에는 issuer 정보를 비롯한 각 기능별 endpoint url 이 존재한다.
     *      AuthorizationServerContextHolder 에 AuthorizationServerContext 를 ThreadLocal 통해 적재한다.
     *          다음 filter 에서 AuthorizationServerContextHolder 를 통해 AuthorizationServerContext 를 참조할 수 있다.
     */

    // application.yml 설정을 이용하여 자동 구성되도록 하지 않고.. 직접 등록함
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("my-spring-client")
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/my-spring-client")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
                .build();

        return new InMemoryRegisteredClientRepository(registeredClient);
    }

    // application.yml 설정을 이용하여 자동 구성되도록 하지 않고.. 직접 등록함
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer("http://localhost:8091")
                .build();
    }

}
