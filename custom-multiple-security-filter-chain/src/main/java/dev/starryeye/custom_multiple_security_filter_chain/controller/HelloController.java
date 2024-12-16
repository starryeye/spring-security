package dev.starryeye.custom_multiple_security_filter_chain.controller;

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
        return "user";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }

    @GetMapping("/db")
    public String db() {
        return "db";
    }

    @GetMapping("/api")
    public String api() {
        return "api";
    }
}
