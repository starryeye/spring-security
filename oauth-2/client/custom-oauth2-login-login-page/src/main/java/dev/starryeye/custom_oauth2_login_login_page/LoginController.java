package dev.starryeye.custom_oauth2_login_login_page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/custom-login-page")
    public String loginPage() {
        return "login";
    }
}
