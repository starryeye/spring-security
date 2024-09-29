package dev.starryeye.custom_csrf_2.csrf;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class CustomCsrfCookieFilter extends OncePerRequestFilter {

    /**
     * CustomCsrfCookieFilter..
     *
     * OncePerRequestFilter..
     *
     * CsrfFilter..
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
