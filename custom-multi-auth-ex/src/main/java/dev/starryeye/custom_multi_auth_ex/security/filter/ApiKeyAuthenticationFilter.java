package dev.starryeye.custom_multi_auth_ex.security.filter;

import dev.starryeye.custom_multi_auth_ex.security.token.ApiKeyAuthenticationToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationManager authenticationManager;

    public ApiKeyAuthenticationFilter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String apiKey = request.getHeader("X-API-KEY");
        if (apiKey != null) {
            try {
                ApiKeyAuthenticationToken unauthenticated = ApiKeyAuthenticationToken.unauthenticated(apiKey);
                Authentication authResult = authenticationManager.authenticate(unauthenticated);

                SecurityContextHolder.getContextHolderStrategy().getContext().setAuthentication(authResult);
                /**
                 * stateless 인증, 세션 필요 없이 Api Key 로만 인증 처리하기 때문에
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
