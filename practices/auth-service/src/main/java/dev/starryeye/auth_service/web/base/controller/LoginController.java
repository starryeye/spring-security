package dev.starryeye.auth_service.web.base.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "exception", required = false) String exception,
            Model model
    ) {
        /**
         * 해당 페이지에서 비롯된 로그인은 POST /login 으로
         * form 로그인이다. (formSecurityFilterChain)
         */
        model.addAttribute("error", error);
        model.addAttribute("exception", exception);

        return "/default/login/login";
    }
}
