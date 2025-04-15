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

    /**
     * 주의 사항.
     * 현재, apiKeyAuthenticationManager (bean) 은 기본(child) AuthenticationManager 로 ..
     *  ApiKeyAuthenticationProvider 를 가지는 ProviderManager 가 설정되어있고
     *  parent AuthenticationManager 로..
     *  DaoAuthenticationProvider 를 가지는 ProviderManager 가 설정되어있다.
     *  그런데..
     *      authenticationManager.authenticate(unauthenticated) 를 호출하고..
     *          ApiKeyAuthenticationProvider::authenticate 에서 실패하면 예외가 발생하여
     *          parent 쪽 로직이 동작하지 않음. (ProviderManager::authenticate 참고)
     *          그리고 parent 쪽 동작하도록 null 을 리턴하더라도..
     *          ApiKeyAuthenticationToken 타입이라 DaoAuthenticationProvider::support 를 만족하지 못해서 실패함.
     *
     *-> todo, ApiKeyAuthenticationToken 을 UsernamePasswordAuthenticationToken 을 상속하도록하고..
     *      ApiKeyAuthenticationProvider::authenticate 에서 실패하면, null 리턴해서 테스트 해보기
     *
     */

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
