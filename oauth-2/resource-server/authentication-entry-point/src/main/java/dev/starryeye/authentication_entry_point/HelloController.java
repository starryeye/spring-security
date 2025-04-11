package dev.starryeye.authentication_entry_point;

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
