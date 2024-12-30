package dev.starryeye.auth_service.security.rest.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestMyAuthenticationFailureHandler implements AuthenticationFailureHandler {

    /**
     * Rest 인증에 실패했을 경우에 호출되는 메서드를 구현
     */

    private final ObjectMapper objectMapper;

    public RestMyAuthenticationFailureHandler() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {

        String errorMessage = "Authentication failed";

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        if (exception instanceof BadCredentialsException) {
            errorMessage = "Bad credentials : " + exception.getMessage();
        } else if (exception instanceof UsernameNotFoundException) {
            errorMessage = "Username not found : " + exception.getMessage();
        }

        objectMapper.writeValue(response.getWriter(), errorMessage);
    }
}
