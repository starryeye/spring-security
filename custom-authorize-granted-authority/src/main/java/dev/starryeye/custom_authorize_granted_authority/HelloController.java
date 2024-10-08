package dev.starryeye.custom_authorize_granted_authority;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public String anonymous() {
        return "Hello anonymous";
    }

    @GetMapping("/user")
    public String user() {
        return "Hello user";
    }

    @GetMapping("/db")
    public String db() {
        return "Hello db";
    }

    @GetMapping("/admin")
    public String admin() {
        return "Hello admin";
    }
}
