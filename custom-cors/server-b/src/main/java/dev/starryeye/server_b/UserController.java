package dev.starryeye.server_b;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserController {

    @GetMapping("/users")
    public String users() {

        return "{\"name\":\"starry eye\"}";
    }
}
