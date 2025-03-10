package dev.starryeye.auth_service.web.base.controller;

import dev.starryeye.auth_service.web.base.controller.request.RegisterUserRequest;
import dev.starryeye.auth_service.web.base.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/users")
public class RegisterUserController {

    private final UserService service;

    @PostMapping("/signup")
    public String registerUser(@ModelAttribute RegisterUserRequest request) {

        service.registerUser(request.toServiceRequest());

        return "redirect:/";
    }
}
