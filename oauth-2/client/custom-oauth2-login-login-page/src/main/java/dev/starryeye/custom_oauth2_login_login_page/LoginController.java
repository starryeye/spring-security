package dev.starryeye.custom_oauth2_login_login_page;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {

    @GetMapping("/custom-login-page")
    public String loginPage() {
        return "This is custom Login Page";
    }
}
