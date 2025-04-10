package dev.starryeye.custom_social_login_client_with_form_login.controller;

import dev.starryeye.custom_social_login_client_with_form_login.controller.request.RegisterUserRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class RegisterController {

    // todo, 해당 client 서버에서 직접 제공하는 회원가입 기능이다. (social 로 로그인 및 회원가입이 아님)
    //      사용해야할 객체 FormUser, UserService, UserRepository
    //      index.html 에 회원가입 버튼을 만들고..
    //          그에 따른 회원가입 페이지(register.html)도 추가로 만들고..

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute RegisterUserRequest request) {
        return "redirect:/login";
    }
}
