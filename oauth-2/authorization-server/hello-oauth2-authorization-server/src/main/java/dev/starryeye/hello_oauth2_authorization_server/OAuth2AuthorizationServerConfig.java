package dev.starryeye.hello_oauth2_authorization_server;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
//@Import(OAuth2AuthorizationServerConfiguration.class)
public class OAuth2AuthorizationServerConfig {

    /**
     * oauth2-authorization-server 의존성만 추가하고 아무것도 안하면..
     *      SecurityAutoConfiguration 에서 SpringBootWebSecurityConfiguration 를 거쳐서..
     *      SecurityFilterChainConfiguration::defaultSecurityFilterChain 가 등록되어 그냥 기본 spring security 의 SecurityFilterChain 이 등록된다.
     *
     * authorization-server 가 적용된 SecurityFilterChain 만드는 방법 3가지
     *
     * 1.
     * OAuth2AuthorizationServerAutoConfiguration 에서
     *      OAuth2AuthorizationServerWebSecurityConfiguration, OAuth2AuthorizationServerConfiguration 를 import 하는데.. (두 클래스는 public 이 아니라서 개발자 참조 불가)
     *          application.yml 에 개발자가 설정을 해줘야 동작한다.
     *              OAuth2AuthorizationServerWebSecurityConfiguration::authorizationServerSecurityFilterChain 에서 SecurityFilterChain 이 등록된다.
     *      3개의 클래스 모두 spring-boot-autoconfigure 의존성 내에 있는 설정 클래스로 authorization-server 의존성과는 상관없다.
     *
     * 2.
     * authorization-server 의존성 내의 클래스..
     * OAuth2AuthorizationServerConfiguration (위 spring-boot-autoconfigure 내의 클래스 이름만 동일하고 전혀 다른 클래스임.. 주의)
     *      OAuth2AuthorizationServerConfiguration::authorizationServerSecurityFilterChain 에 존재하는 SecurityFilterChain 빈 등록은 개발자가 직접 등록할때 사용되는 코드이다.
     *          @Import(OAuth2AuthorizationServerConfiguration.class) 를 사용하여 빈을 등록하도록 제공되는 코드
     *
     * 3.
     * 개발자가 SecurityFilterChain 을 직접 만들어 등록한다.
     *      @Bean 으로 등록
     */

//    @Bean
//    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//
//        http
//                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
//                        authorizationManagerRequestMatcherRegistry
//                                .anyRequest().authenticated()
//                )
//                ;
//
//        return http.build();
//    }
}
