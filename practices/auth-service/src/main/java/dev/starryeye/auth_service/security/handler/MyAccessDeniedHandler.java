package dev.starryeye.auth_service.security.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MyAccessDeniedHandler implements AccessDeniedHandler {

    private static final String ERROR_PAGE = "/denied";

    private final RedirectStrategy redirectStrategy;

    public MyAccessDeniedHandler() {
        this.redirectStrategy = new DefaultRedirectStrategy();
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {

        /**
         * 인증을 받은 사람이 접근 거부가 되었을 때 호출되는 메서드이다.
         * 에러 페이지로 리다이렉트 시킨다.
         */

        String redirectUrl = ERROR_PAGE + "?exception=" + accessDeniedException.getMessage();

        redirectStrategy.sendRedirect(request, response, redirectUrl);
    }
}
