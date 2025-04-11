package dev.starryeye.custom_social_login_client_with_form_login.controller;

import dev.starryeye.custom_social_login_client_with_form_login.service.security.CustomPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthorizationInfoController {

    private final OAuth2AuthorizedClientService authorizedClientService;

    @GetMapping("/api/is-authorized-client-1")
    public OAuth2AuthorizedClient isAuthorizedClient1(@RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient authorizedClient) {

        log.info("OAuth2AccessToken = {}", authorizedClient.getAccessToken());
        log.info("OAuth2RefreshToken = {}", authorizedClient.getRefreshToken());
        log.info("ClientRegistration = {}", authorizedClient.getClientRegistration());
        log.info("principal name = {}", authorizedClient.getPrincipalName());
        return authorizedClient;
    }

    @GetMapping("/api/is-authorized-client-2")
    public OAuth2AuthorizedClient isAuthorizedClient2(@AuthenticationPrincipal CustomPrincipal customPrincipal) {

        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                customPrincipal.getProviderId(),
                customPrincipal.getName()
        );

        log.info("OAuth2AccessToken = {}", authorizedClient.getAccessToken());
        log.info("OAuth2RefreshToken = {}", authorizedClient.getRefreshToken());
        log.info("ClientRegistration = {}", authorizedClient.getClientRegistration());
        log.info("principal name = {}", authorizedClient.getPrincipalName());
        return authorizedClient;
    }

    @GetMapping("/api/is-authorized-client-3")
    public OAuth2AuthorizedClient isAuthorizedClient3() {

        Authentication authentication = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();

        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            OAuth2AuthorizedClient authorizedClient =
                    authorizedClientService.loadAuthorizedClient(
                            oauthToken.getAuthorizedClientRegistrationId(),
                            oauthToken.getName()
                    );

            if (authorizedClient != null) {
                log.info("OAuth2AccessToken = {}", authorizedClient.getAccessToken());
                log.info("OAuth2RefreshToken = {}", authorizedClient.getRefreshToken());
                log.info("ClientRegistration = {}", authorizedClient.getClientRegistration());
                log.info("principal name = {}", authorizedClient.getPrincipalName());
                return authorizedClient;
            }
        }

        return null;
    }
}
