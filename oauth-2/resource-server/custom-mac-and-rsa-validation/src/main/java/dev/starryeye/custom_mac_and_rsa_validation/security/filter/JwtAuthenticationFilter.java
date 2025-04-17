package dev.starryeye.custom_mac_and_rsa_validation.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import dev.starryeye.custom_mac_and_rsa_validation.security.filter.request.LoginRequest;
import dev.starryeye.custom_mac_and_rsa_validation.signature.JWTGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    /**
     * username, password 로 인증
     */

    private final JWTGenerator tokenGenerator;

    private final ObjectMapper mapper;

    public JwtAuthenticationFilter(JWTGenerator tokenGenerator) {
        this.mapper = new ObjectMapper();
        this.tokenGenerator = tokenGenerator;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        // username, password 인증 수행
        try {
            LoginRequest loginRequest = mapper.readValue(request.getInputStream(), LoginRequest.class);
            if (loginRequest == null || loginRequest.username() == null || loginRequest.password() == null) {
//                throw new BadCredentialsException("Username or password must not be null");
                return null;
            }

            UsernamePasswordAuthenticationToken unauthenticated = UsernamePasswordAuthenticationToken.unauthenticated(loginRequest.username(), loginRequest.password());

            return getAuthenticationManager().authenticate(unauthenticated);
        } catch (Exception e) {
            throw new BadCredentialsException("Failed to parse login request", e);
        }
    }
}
