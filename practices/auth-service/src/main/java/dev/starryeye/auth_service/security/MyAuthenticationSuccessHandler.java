package dev.starryeye.auth_service.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MyAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    /**
     * AuthenticationSuccessHandler..
     *      인증에 성공하면 수행할 작업을 담당한다.
     *
     * RequestCache..
     *      구현체로 HttpSessionRequestCache 를 가지며..
     *              HttpSessionRequestCache 는 세션에 인증요청 이전의 요청정보(SavedRequest 객체)를 가진다.
     */

    private final RequestCache requestCache;
    private final RedirectStrategy redirectStrategy;

    public MyAuthenticationSuccessHandler() {
        this.requestCache = new HttpSessionRequestCache();
        this.redirectStrategy = new DefaultRedirectStrategy();
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        setDefaultTargetUrl("/");

        // RequestCache 로 부터 인증요청 이전의 요청정보(SavedRequest)를 꺼낸다.
        SavedRequest savedRequest = requestCache.getRequest(request, response);

        if (savedRequest != null) {
            // 이전에 요청한 url 로 리다이렉트 시킨다.
            String targetUrl = savedRequest.getRedirectUrl();
            redirectStrategy.sendRedirect(request, response, targetUrl);
        } else {
            // 기본 url 인 root 로 리다이렉트 시킨다.
            redirectStrategy.sendRedirect(request, response, getDefaultTargetUrl());
        }
    }
}
