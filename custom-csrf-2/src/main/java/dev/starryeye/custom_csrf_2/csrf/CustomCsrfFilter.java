package dev.starryeye.custom_csrf_2.csrf;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class CustomCsrfFilter extends OncePerRequestFilter {

    /**
     * CustomCsrfFilter..
     * 최초 요청 (GET "/script") 요청 시, GET 요청이라 csrf 토큰 관련 작업이 전혀 이루어지지 않는다..
     * 그래서 이전 필터 (CsrfFilter) 에서 지연 로딩을 위한 Supplier 셋팅 해둔 csrf 토큰을 getToken() 시켜 로딩 시킨다.
     * -> 로딩 시키는 순간, 쿠키에 csrf 토큰이 셋팅된다. JavaScript 응답(script.html) 과 함께 전달됨
     *      CookieCsrfTokenRepository::saveToken
     *          Csrf Token 원본 값이 셋팅 되는 것을 볼 수 있다..
     *
     * 참고.
     * 실제 CsrfFilter 역할을 대신하기 위해 만든 것은 아니다.
     *      실제 동작 상에서도 CsrfFilter 의 동작이 이루어지고 CustomCsrfFilter 가 동작한다.
     */

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");

        if (csrfToken != null) {
            csrfToken.getToken(); // 지연로딩 수행
        }

        filterChain.doFilter(request, response);
    }
}
