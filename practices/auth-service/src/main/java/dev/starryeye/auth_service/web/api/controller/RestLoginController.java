package dev.starryeye.auth_service.web.api.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RestLoginController {

    @PostMapping("/login")
    public String login() {
        return "login successful";
    }
}
