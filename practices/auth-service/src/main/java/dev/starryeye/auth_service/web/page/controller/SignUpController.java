package dev.starryeye.auth_service.web.page.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SignUpController {

    @GetMapping("/users/signup")
    public String signUp() {
        return "/login/signup";
    }
}
