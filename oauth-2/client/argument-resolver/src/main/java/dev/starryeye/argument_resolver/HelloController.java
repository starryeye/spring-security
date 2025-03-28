package dev.starryeye.argument_resolver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class HelloController {

    @GetMapping("/")
    public String hello() {
        return "Hello OAuth 2.0 Client!";
    }

    /**
     * oauth2Login api 를 사용하여 client 에서 사용자 인증을 진행하면..
     *      Authentication..
     *          OAuth2AuthenticationToken 타입이다.
     *          principal..
     *              OAuth2User 타입의 상속인 DefaultOidcUser 타입이다. (OIDC)
     */

    @GetMapping("/authentication")
    public Authentication authentication(Authentication authentication) {

        // 인증 객체 참조 방법 1
        OAuth2AuthenticationToken oAuth2AuthenticationToken1 = (OAuth2AuthenticationToken) authentication;

        // 인증 객체 참조 방법 2
        OAuth2AuthenticationToken oAuth2AuthenticationToken2 = (OAuth2AuthenticationToken) SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();

        log.info("oAuth2AuthenticationToken1: {}", oAuth2AuthenticationToken1);
        log.info("oAuth2AuthenticationToken2: {}", oAuth2AuthenticationToken2);

        return oAuth2AuthenticationToken1;
    }

    @GetMapping("/principal")
    public OAuth2User principal(@AuthenticationPrincipal OAuth2User principal) { // 주의. Principal 타입이면 null 이 바인딩됨..

        // principal 참조 방법 1
        DefaultOidcUser principal1 = (DefaultOidcUser) principal;

        // principal 참조 방법 2
        OAuth2AuthenticationToken oAuth2AuthenticationToken = (OAuth2AuthenticationToken) SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
        DefaultOidcUser principal2 = (DefaultOidcUser) oAuth2AuthenticationToken.getPrincipal();

        log.info("principal1: {}", principal1);
        log.info("principal2: {}", principal2);

        return principal1;
    }

    @GetMapping("/authorized-client")
    public OAuth2AuthorizedClient authorizedClient(@RegisteredOAuth2AuthorizedClient("my-keycloak") OAuth2AuthorizedClient authorizedClient) {

        /**
         * oauth2Login 으로 인가 및 인증처리를 한 후, 호출해보자. ("/authentication")
         */

        /**
         * "/authorized-client" path 는 permitAll 로 설정되어있어서 filter 에서 아무런 처리를 하지 않음.
         * 그러나..
         * @RegistredOAuth2AuthorizedClient..
         *      OAuth2AuthorizedClientArgumentResolver 에 의해 OAuth2AuthorizedClient 를 바인딩 받을 수 있다.
         *      registrationId 를 어노테이션 요소에 설정하면 해당 authorization server 를 통해 client 인가를 받을 수 있다. (스스로 인가 받기 위해서는 커스텀이 필요하다. "/authorized-client-password" 참고..)
         *          그래서.. 이 controller 를 호출할땐 oauth2Login 으로 인증, 인가 처리를 하고 호출하면 인가 처리할때 저장한 OAuth2AuthorizedClient 가 바인딩된다.
         *          내부적으로 OAuth2AuthorizedClientManager 로 인가 받는다. (여기선 개발자가 직접 등록한 OAuth2AuthorizedClientManager 를 사용한다.)
         *              지원하는 authorizedClientProvider..
         *                  AuthorizationCodeOAuth2AuthorizedClientProvider : 사실상 지원안하는듯..?
         *                  RefreshTokenOAuth2AuthorizedClientProvider
         *                  ClientCredentialsOAuth2AuthorizedClientProvider
         *                  PasswordOAuth2AuthorizedClientProvider
         *      OAuth2AuthorizedClientService, OAuth2AuthorizedClientRepository 를 DI 받아서 OAuth2AuthorizedClient 참조하는 방식보다 더 편리하다.
         *
         *
         * OAuth2AuthorizedClientArgumentResolver 에서는 client 에 대한 인가만 하기 때문에.. 인증이 되지 않는다.
         *      "/authorized-client" 호출 후, "/is-authenticated" 호출하면 anonymous 이다..
         */

        ClientRegistration clientRegistration = authorizedClient.getClientRegistration();
        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
        OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();

        log.info("clientRegistration: {}", clientRegistration);
        log.info("accessToken: {}", accessToken.getTokenValue());
        log.info("refreshToken: {}", refreshToken.getTokenValue());

        return authorizedClient;
    }

}
