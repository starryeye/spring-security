package dev.starryeye.custom_mac_and_rsa_validation.api.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public Authentication home(Authentication authentication) {
        return authentication;
    }
}
