package dev.starryeye.auth_service.web.api.controller;

import dev.starryeye.auth_service.security.MyPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/api")
public class HomeApiController {

    @GetMapping()
    public String home() {
        return "/api/home";
    }

    @ResponseBody
    @GetMapping("/user")
    public MyPrincipal user(@AuthenticationPrincipal MyPrincipal principal) {
        return principal;
    }

    @ResponseBody
    @GetMapping("/manager")
    public MyPrincipal manager(@AuthenticationPrincipal MyPrincipal principal) {
        return principal;
    }

    @ResponseBody
    @GetMapping("/admin")
    public MyPrincipal admin(@AuthenticationPrincipal MyPrincipal principal) {
        return principal;
    }
}
