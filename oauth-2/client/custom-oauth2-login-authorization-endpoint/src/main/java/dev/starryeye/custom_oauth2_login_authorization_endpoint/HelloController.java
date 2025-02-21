package dev.starryeye.custom_oauth2_login_authorization_endpoint;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public String hello() {
        return "Hello OAuth2.0 Client";
    }
}
