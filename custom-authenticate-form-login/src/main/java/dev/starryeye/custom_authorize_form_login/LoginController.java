package dev.starryeye.custom_authorize_form_login;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {

    @GetMapping("/loginPage")
    public String loginPage() {
        return "This is Login Page!";
    }

    @GetMapping("/home")
    public String home() {
        return "This is Home Page!";
    }

    @GetMapping("/failed")
    public String failed() {
        return "This is Login Failed Page!";
    }
}
