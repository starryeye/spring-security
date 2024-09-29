package dev.starryeye.custom_csrf_2.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping("/cookie")
    public String cookie() {
        return "cookie";
    }

    @GetMapping("/form")
    public String form() {
        return "form";
    }
}
