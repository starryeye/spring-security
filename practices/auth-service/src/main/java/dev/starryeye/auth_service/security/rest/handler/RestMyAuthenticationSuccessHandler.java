package dev.starryeye.auth_service.security.rest.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.auth_service.security.MyPrincipal;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestMyAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    /**
     * Rest 인증에 성공했을 경우에 호출되는 메서드를 구현
     */

    private final ObjectMapper objectMapper;

    public RestMyAuthenticationSuccessHandler() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        MyPrincipal principal = (MyPrincipal) authentication.getPrincipal();
        LoginResponse loginResponse = new LoginResponse(principal.username(), principal.age(), principal.roles());

        // 인증 성공 시, 응답 데이터를 설정
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), loginResponse);

        clearAuthenticationAttributes(request);
    }

    private void clearAuthenticationAttributes(HttpServletRequest request) {
        // 인증에 성공하면, 과거 인증 실패 예외 기록을 말소 시킨다.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        }
    }

    @Getter
    private static class LoginResponse {

        private final String username;
        private final Integer age;
        private final String roles;

        public LoginResponse(String username, Integer age, String roles) {
            this.username = username;
            this.age = age;
            this.roles = roles;
        }
    }
}
