package dev.starryeye.custom_rsa_jwt_issuer_verifier.security.username_password.success_handler;

import dev.starryeye.custom_rsa_jwt_issuer_verifier.signature.JwtGenerator;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtIssuerOnAuthenticationSuccess implements AuthenticationSuccessHandler {

    private final JwtGenerator jwtGenerator;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        // 인증에 성공하면 토큰 발행 후 리턴
        User principal = (User) authentication.getPrincipal();
        String token = jwtGenerator.generateSignedToken(principal);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.getWriter().write(token);
    }
}
