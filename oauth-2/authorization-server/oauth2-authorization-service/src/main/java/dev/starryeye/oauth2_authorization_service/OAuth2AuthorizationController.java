package dev.starryeye.oauth2_authorization_service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OAuth2AuthorizationController {

    private final OAuth2AuthorizationService oAuth2AuthorizationService;

    @GetMapping("/oauth2-authorization")
    public OAuth2Authorization oauth2Authorization(@RequestParam("token") String token) {

        return oAuth2AuthorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
    }
}
