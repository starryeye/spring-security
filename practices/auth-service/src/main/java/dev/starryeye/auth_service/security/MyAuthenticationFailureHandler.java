package dev.starryeye.auth_service.security;

import dev.starryeye.auth_service.security.exception.MySecretAuthenticationException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MyAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {

        /**
         * form 로그인 인증 예외가 발생할 경우 호출되는 메서드이다.
         *      AuthenticationException(인증 예외) 가 파라미터로 전달된다.
         */

        String errorMessage = "Authentication failed";

        if (exception instanceof BadCredentialsException) {
            errorMessage = "Bad credentials : " + exception.getMessage();
        } else if (exception instanceof UsernameNotFoundException) {
            errorMessage = "Username not found : " + exception.getMessage();
        } else if (exception instanceof CredentialsExpiredException) {
            errorMessage = "Credential expired : " + exception.getMessage();
        } else if (exception instanceof MySecretAuthenticationException) {
            errorMessage = "My exception : " + exception.getMessage();
        }

        // "/login?error=true&exception=" + errorMessage
        // 위 url 을 받을 수 있게 개발해줘야 한다. (login.html, LoginController)
        setDefaultFailureUrl("/login?error=true&exception=" + errorMessage);

        super.onAuthenticationFailure(request, response, exception);
    }
}
