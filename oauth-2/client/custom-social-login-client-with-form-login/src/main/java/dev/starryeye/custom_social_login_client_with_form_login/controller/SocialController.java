package dev.starryeye.custom_social_login_client_with_form_login.controller;

import dev.starryeye.custom_social_login_client_with_form_login.service.security.CustomPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SocialController {

    @GetMapping("/social")
    public String social(Model model, Authentication authentication) {

        // @AuthenticationPrincipal CustomPrincipal principal 대체 가능
        CustomPrincipal principal = (CustomPrincipal) authentication.getPrincipal();

        if (authentication != null) {
            /**
             * authentication 타입이 OAuth2AuthenticationToken 이면
             *      OAuth 2.0 access token /userinfo 로 인증했거나
             *      OAuth 2.0 OIDC id token (혹은 추가로.. + access token /userinfo) 로 인증했거나
             *      form 로그인으로 인증하진 않음
             */

            String username = principal.getUsername();
            model.addAttribute("user", username);
        } else {
            model.addAttribute("user", "방문자");
        }

        return "social";
    }
}
