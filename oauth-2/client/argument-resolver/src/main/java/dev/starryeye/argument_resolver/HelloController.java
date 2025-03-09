package dev.starryeye.argument_resolver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@Slf4j
@RestController
public class HelloController {

    @GetMapping("/")
    public String hello() {
        return "Hello OAuth 2.0 Client!";
    }

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

}
