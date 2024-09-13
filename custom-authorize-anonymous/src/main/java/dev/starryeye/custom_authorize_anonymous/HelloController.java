package dev.starryeye.custom_authorize_anonymous;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public String hello() {
        return "Hello Spring Security!";
    }

    @GetMapping("/anonymous")
    public String anonymous() {
        return "Hello Anonymous!";
    }

    @GetMapping("/anonymous/context")
    public String anonymousContext(@CurrentSecurityContext SecurityContext securityContext) {
        return securityContext.getAuthentication().getName(); // 이 방법은 익명이든 인증된 사용자든 동일하게 Authentication 객체에 접근이 된다.
    }

    @GetMapping("/authentication")
    public String authentication(Authentication authentication) {
        if (authentication instanceof AnonymousAuthenticationToken) {
            return "Anonymous Authentication!"; // 여기는 절대로 도달못함.. 익명 사용자의 authentication 을 접근하려면 anonymousContext() 처럼 사용 할 것.
        } else {
//            return authentication.getName(); // 이렇게 하면 익명 사용자일때는 authentication 이 null 이므로 error 가 발생하여 에러페이지로 접근한다. (에러페이지 권한이 없으므로 로그인 페이지로 도달함..)

            if (authentication == null) {
                return "Authentication is null!"; // 익명 사용자 의 경우
            } else {
                return authentication.getName();
            }
        }
    }
}
