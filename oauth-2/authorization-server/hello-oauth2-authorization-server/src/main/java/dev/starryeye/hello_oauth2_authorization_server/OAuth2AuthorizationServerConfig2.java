package dev.starryeye.hello_oauth2_authorization_server;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;

@Configuration
@Import(OAuth2AuthorizationServerConfiguration.class)
public class OAuth2AuthorizationServerConfig2 {

    /**
     * HelloOauth2AuthorizationServerApplication 주석에서 설명한 두번째 방법..
     *
     * authorization-server 의존성에서 제공하는 OAuth2AuthorizationServerConfiguration::authorizationServerSecurityFilterChain 를 이용하여..
     * SecurityFilterChain 을 등록한다. (주의, authorization-server 의존성의 OAuth2AuthorizationServerConfiguration 이다.)
     *
     */
}
