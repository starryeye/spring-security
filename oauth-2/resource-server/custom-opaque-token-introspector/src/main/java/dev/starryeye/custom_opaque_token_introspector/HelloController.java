package dev.starryeye.custom_opaque_token_introspector;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public Authentication hello(Authentication authentication) {
        return authentication;
    }
}
