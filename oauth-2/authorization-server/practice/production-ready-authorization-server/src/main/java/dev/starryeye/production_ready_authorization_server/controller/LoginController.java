package dev.starryeye.production_ready_authorization_server.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    /**
     * 커스텀 로그인 페이지 렌더링. (custom-login-and-consent-page 프로젝트 이식)
     *      로그인 처리(POST "/login")는 기본값 그대로 UsernamePasswordAuthenticationFilter 가 담당한다.
     */

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
