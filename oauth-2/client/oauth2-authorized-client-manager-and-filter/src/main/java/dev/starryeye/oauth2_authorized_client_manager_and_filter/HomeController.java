package dev.starryeye.oauth2_authorized_client_manager_and_filter;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Configuration
@RequiredArgsConstructor
public class HomeController {

    private final OAuth2AuthorizedClientService authorizedClientService;

    @GetMapping("/")
    public String home(Model model) {

        Authentication authentication = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();

        OAuth2AuthorizedClient oAuth2AuthorizedClient = authorizedClientService.loadAuthorizedClient("my-keycloak-password-credentials", authentication.getName());

        model.addAttribute("authentication", authentication);
        model.addAttribute("accessToken", oAuth2AuthorizedClient.getAccessToken().getTokenValue());
        model.addAttribute("refreshToken", oAuth2AuthorizedClient.getRefreshToken().getTokenValue());

        return "home";
    }
}
