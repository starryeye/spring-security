package dev.starryeye.custom_authenticate_exception_handling.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccessDeniedController {

    @GetMapping("/denied")
    public String accessDenied() {
        return "Access denied";
    }
}
