package dev.starryeye.custom_authenticate_servlet_integration.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Slf4j
@RestController
public class HelloController {

    @GetMapping("/")
    public String home() {
        return "Hello World!";
    }

    @GetMapping("/user")
    public String user() {
        return "user";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }

    @GetMapping("/db")
    public String db() {
        return "db";
    }

    @GetMapping("/login")
    public String login(
            HttpServletRequest request,
            User user
    ) throws ServletException {

        // AuthenticationManager 를 사용하여 사용자가 인증할 수 있도록 한다.
        request.login(user.username(), user.password());

        log.info("login successful, username={}, password={}", user.username(), user.password());
        return "login";
    }

    @GetMapping("/is-authenticated")
    public String isAuthenticated(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

        // 사용자가 인증되어있는지 확인
        //      인증되어있지 않다면, 로그인 페이지로 보낸다.
        boolean authenticated = request.authenticate(response);

        if (authenticated) {
            log.info("isAuthenticated successful");
            return "authenticated";
        }

        log.info("isAuthenticated failed");
        return "Not authenticated";
    }
}
