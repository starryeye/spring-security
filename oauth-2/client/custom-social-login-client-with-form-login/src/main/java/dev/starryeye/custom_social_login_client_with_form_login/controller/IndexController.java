package dev.starryeye.custom_social_login_client_with_form_login.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@Controller
public class IndexController {

    @GetMapping("/")
    public String index(Model model, Authentication authentication, @AuthenticationPrincipal OAuth2User oAuth2User) {

        OAuth2AuthenticationToken oAuth2AuthenticationToken = (OAuth2AuthenticationToken) authentication;
        if (oAuth2AuthenticationToken != null) { // 인증 받았다면 null 이 아니다.

            Map<String, Object> attributes = oAuth2User.getAttributes();
            String name = (String) attributes.get("name");
            if ("my-naver".equals(oAuth2AuthenticationToken.getAuthorizedClientRegistrationId())) {
                Map<String, Object> response = (Map<String, Object>) attributes.get("response");
                name = (String) response.get("name");
            }

            model.addAttribute("user", name);
        } else {
            model.addAttribute("user", "방문자");
        }

        return "index";
    }
}
