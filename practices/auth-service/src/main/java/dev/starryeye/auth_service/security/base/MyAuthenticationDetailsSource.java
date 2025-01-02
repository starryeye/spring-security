package dev.starryeye.auth_service.security.base;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

@Component
public class MyAuthenticationDetailsSource implements AuthenticationDetailsSource<HttpServletRequest, WebAuthenticationDetails> {

    @Override
    public WebAuthenticationDetails buildDetails(HttpServletRequest request) {
        /**
         * AuthenticationFilter(UsernamePasswordAuthenticationFilter) 는..
         *      AuthenticationDetailsSource(MyAuthenticationDetailsSource) 를 이용하여
         *      WebAuthenticationDetails(MyWebAuthenticationDetails) 를 생성한다.
         */
        return new MyWebAuthenticationDetails(request);
    }
}
