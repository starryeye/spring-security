package dev.starryeye.custom_multi_auth_ex;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public Authentication user(Authentication authentication) {
        return authentication;
    }

    @GetMapping("/admin")
    public Authentication admin(Authentication authentication) {
        return authentication;
    }

    @GetMapping("/api")
    public Authentication developer(Authentication authentication) {
        return authentication;
    }
}
