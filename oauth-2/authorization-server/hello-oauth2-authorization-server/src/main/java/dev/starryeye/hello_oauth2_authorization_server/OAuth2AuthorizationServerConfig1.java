package dev.starryeye.hello_oauth2_authorization_server;

import org.springframework.context.annotation.Configuration;

@Configuration
public class OAuth2AuthorizationServerConfig1 {

    /**
     * HelloOauth2AuthorizationServerApplication 주석에서 설명한 첫번째 방법..
     *
     * spring-boot-autoconfigure 에서 제공하는 방법을 이용한다.
     *      OAuth2AuthorizationServerAutoConfiguration 에서 OAuth2AuthorizationServerWebSecurityConfiguration 를 이용해서..
     *      SecurityFilterChain 이 생성된다.
     * application.yml 설정만 해주면 끝
     */
}
