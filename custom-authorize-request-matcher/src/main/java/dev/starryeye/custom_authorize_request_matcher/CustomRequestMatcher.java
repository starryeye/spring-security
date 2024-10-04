package dev.starryeye.custom_authorize_request_matcher;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

public class CustomRequestMatcher implements RequestMatcher {

    private final String urlPattern;

    public CustomRequestMatcher(String urlPattern) {
        this.urlPattern = urlPattern;
    }

    @Override
    public boolean matches(HttpServletRequest request) {

        /**
         * 요청 url path 가 this.urlPattern 의 하위 경로이던가 일치하면 true
         */

        String requestURI = request.getRequestURI();

        return requestURI.startsWith(this.urlPattern);
    }
}
