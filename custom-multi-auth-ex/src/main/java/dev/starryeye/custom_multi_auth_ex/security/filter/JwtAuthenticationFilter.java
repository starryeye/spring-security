package dev.starryeye.custom_multi_auth_ex.security.filter;

import dev.starryeye.custom_multi_auth_ex.security.token.JwtAuthenticationToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationManager authenticationManager;

    public JwtAuthenticationFilter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {

            String token = authHeader.substring(7);
            try {
                JwtAuthenticationToken unauthenticated = JwtAuthenticationToken.unauthenticated(token);
                Authentication authResult = authenticationManager.authenticate(unauthenticated);

                SecurityContextHolder.getContext().setAuthentication(authResult);
                /**
                 * stateless 인증, 세션 필요 없이 토큰으로만 인증 처리하기 때문에
                 * 매 요청 마다 X-API-KEY 로 인증을 수행한다.
                 * -> SecurityContextRepository 에 저장하는 로직은 필요 없음.
                 */
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
