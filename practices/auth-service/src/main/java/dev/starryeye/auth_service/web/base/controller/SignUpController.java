package dev.starryeye.auth_service.web.base.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SignUpController {

    @GetMapping("/users/signup")
    public String signUp() {
        return "/default/signup/signup";
    }
}
