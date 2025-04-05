package dev.starryeye.custom_social_login_client_with_form_login.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class AuthenticationInfoController {

    @GetMapping("/api/oauth2-oidc-user")
    public Authentication user(Authentication authentication, @AuthenticationPrincipal OAuth2User oauth2User) {

        log.info("authentication: {}, oauth2User: {}", authentication, oauth2User);

        return authentication;
    }

    @GetMapping("/api/scope-profile")
    public Authentication profile(Authentication authentication, @AuthenticationPrincipal OAuth2User oauth2User) {

        log.info("authentication: {}, oauth2User: {}", authentication, oauth2User);

        return authentication;
    }

    @GetMapping("/api/scope-openid")
    public Authentication openid(Authentication authentication, @AuthenticationPrincipal OidcUser oidcUser) {

        log.info("authentication: {}, oidcUser: {}", authentication, oidcUser);

        return authentication;
    }
}
