package dev.starryeye.custom_mac_jwt_issuer_verifier.security.filter.username_password;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.custom_mac_jwt_issuer_verifier.security.filter.username_password.request.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

public class CustomUsernamePasswordAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    /**
     * username, password 로 인증
     */

    private final ObjectMapper mapper;

    public CustomUsernamePasswordAuthenticationFilter() {
        this.mapper = new ObjectMapper();
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        // username, password 인증 수행
        try {
            LoginRequest loginRequest = mapper.readValue(request.getInputStream(), LoginRequest.class);
            if (loginRequest == null || loginRequest.username() == null || loginRequest.password() == null) {
                throw new BadCredentialsException("Username or password must not be null");
            }

            UsernamePasswordAuthenticationToken unauthenticated = UsernamePasswordAuthenticationToken.unauthenticated(loginRequest.username(), loginRequest.password());

            return getAuthenticationManager().authenticate(unauthenticated);
        } catch (Exception e) {
            throw new BadCredentialsException("Failed to parse login request", e);
        }
    }
}
