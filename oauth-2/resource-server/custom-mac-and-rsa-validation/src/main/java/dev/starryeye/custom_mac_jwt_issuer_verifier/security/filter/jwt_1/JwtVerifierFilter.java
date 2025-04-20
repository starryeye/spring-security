package dev.starryeye.custom_mac_and_rsa_validation.security.filter.jwt_1;

import dev.starryeye.custom_mac_and_rsa_validation.security.filter.jwt_1.authentication.JwtAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtVerifierFilter extends OncePerRequestFilter {

    private static final String BEARER_TYPE = "Bearer ";

    private final AuthenticationManager authenticationManager;

    public JwtVerifierFilter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");
        
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_TYPE)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(BEARER_TYPE.length()).trim();

        JwtAuthentication unauthenticated = JwtAuthentication.unauthenticated(token);
        Authentication authenticated = authenticationManager.authenticate(unauthenticated);

        SecurityContextHolder.getContextHolderStrategy().getContext().setAuthentication(authenticated);

        filterChain.doFilter(request, response);
    }
}
