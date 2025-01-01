package dev.starryeye.auth_service.web.controller;

import dev.starryeye.auth_service.security.MyPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AccessDeniedController {

    @GetMapping("/denied")
    public String accessDenied(
            @RequestParam(value = "exception", required = false) String exception,
            @AuthenticationPrincipal MyPrincipal principal,
            Model model
    ) {
        model.addAttribute("exception", exception);
        model.addAttribute("username", principal.username());

        return "/denied/denied";
    }
}
