package dev.starryeye.custom_rsa_jwt_issuer_verifier.api.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public Authentication home() {
        return SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
    }
}
