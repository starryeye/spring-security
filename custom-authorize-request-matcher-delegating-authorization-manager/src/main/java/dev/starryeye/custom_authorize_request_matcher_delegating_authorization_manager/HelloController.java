package dev.starryeye.custom_authorize_request_matcher_delegating_authorization_manager;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public String home() {
        return "Hello World!";
    }

    @GetMapping("/user")
    public String user() {
        return "Hello user";
    }

    @GetMapping("/admin")
    public String admin() {
        return "Hello admin";
    }

    @GetMapping("/db")
    public String db() {
        return "Hello db";
    }

    @GetMapping("/secure")
    public String secure() {
        return "Hello secure";
    }
}
