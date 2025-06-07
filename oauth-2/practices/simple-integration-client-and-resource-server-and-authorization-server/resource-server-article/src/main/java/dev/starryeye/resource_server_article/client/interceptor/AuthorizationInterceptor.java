package dev.starryeye.resource_server_article.client.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class AuthorizationInterceptor implements ClientHttpRequestInterceptor {

    /**
     * client 에서는
     * access token 이 만료가 되면 refresh token 으로 갱신 시키는 OAuth2AuthorizedClientManager 를 사용하였지만,
     * resource server 에서는
     * refresh token 도 없고 갱신 할 의무도 없기 때문에 외부로 부터 수신한 access token 을 그대로 헤더에 붙여서 보내기만 한다.
     */

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

        Authentication authentication = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();

        // 인증된 사용자이고 anonymousUser가 아닐 경우에만 token 설정
        if (authentication instanceof JwtAuthenticationToken jwtAuth
                && jwtAuth.isAuthenticated()
                && !"anonymousUser".equals(jwtAuth.getName())
        ) {
            String accessToken = jwtAuth.getToken().getTokenValue();
            log.debug("전달할 Access Token: {}", accessToken);
            request.getHeaders().setBearerAuth(accessToken);
        }

        return execution.execute(request, body);
    }
}
