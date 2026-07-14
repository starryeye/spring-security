package dev.starryeye.custom_login_and_consent_page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    /**
     * 커스텀 로그인 페이지 렌더링.
     *      로그인 처리(POST "/login") 는 여기서 하지 않는다.. 기본값 그대로 UsernamePasswordAuthenticationFilter 가 처리한다.
     *      즉, 화면만 바꾸고 인증 처리는 프레임워크에 그대로 맡기는 구조이다.
     */

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
