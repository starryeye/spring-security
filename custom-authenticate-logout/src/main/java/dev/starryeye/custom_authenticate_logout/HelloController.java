package dev.starryeye.custom_authenticate_logout;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public String hello() {
        return "Hello Spring Security!";
    }

    @GetMapping("/logoutSuccess")
    public String logoutSuccess() {
        return "Logout success!";
    }
}
