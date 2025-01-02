package dev.starryeye.auth_service.web.base.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "/default/home";
    }

    @GetMapping("/user")
    public String user() {
        return "/default/user";
    }

    @GetMapping("/manager")
    public String manager() {
        return "/default/manager";
    }

    @GetMapping("/admin")
    public String admin() {
        return "/default/admin";
    }
}
