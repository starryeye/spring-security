package dev.starryeye.auth_service.web.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginApiController {

    @GetMapping("/api/login")
    public String login() {
        /**
         * 해당 페이지에서 비롯된 로그인은 POST /api/login 으로
         * rest 방식의 로그인이다. (restSecurityFilterChain)
         */
        return "/api/login";
    }
}
