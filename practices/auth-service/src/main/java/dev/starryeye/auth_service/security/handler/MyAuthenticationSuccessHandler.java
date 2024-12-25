package dev.starryeye.auth_service.security.handler;

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
     *      개발자가 직접 커스텀해서 지정해주지 않으면 시큐리티는 기본적으로 SimpleUrlAuthenticationSuccessHandler 를 사용한다.
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

        /**
         * 참고로
         *      로그인 페이지를 요청(로그인 버튼 클릭)해서 로그인을 수행하면 이전 요청은 로그인 페이지가 되는 것이다.
         *          이때는 savedRequest 가 null 이다.
         *      회원 전용 페이지를 요청(회원 전용 버튼 클릭)해서 로그인을 수행하면 이전 요청은 회원전용 페이지가 되고..
         *          이때는 savedRequest 는 null 이 아니며, SavedRequest::getRedirectUrl 은 /user 이다.
         */
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
