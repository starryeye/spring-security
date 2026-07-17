package dev.starryeye.resource_server_article.client.interceptor;

import dev.starryeye.resource_server_article.client.TokenExchangeClient;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class TokenExchangeInterceptor implements ClientHttpRequestInterceptor {

    /**
     * relay 버전의 AuthorizationInterceptor 와의 대비가 이 프로젝트의 주제다..
     *      relay : 수신한 access token 을 "그대로" 다음 서버로 전달한다.
     *          -> 사용자 토큰에 comment scope 까지 있어야 하고, article 이 대신 호출한다는 사실이 토큰에 남지 않는다.
     *      exchange : 수신한 토큰을 subject 로 제출해 "comment 호출용으로 새로 발급받은" 토큰을 전달한다.
     *          -> 사용자 토큰은 article 몫(content)까지만 가지면 되고, 발급 토큰의 act claim 에 article 이 남는다.
     */

    private final TokenExchangeClient tokenExchangeClient;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

        Authentication authentication = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();

        // 인증된 사용자이고 anonymousUser가 아닐 경우에만 token 설정
        if (authentication instanceof JwtAuthenticationToken jwtAuth
                && jwtAuth.isAuthenticated()
                && !"anonymousUser".equals(jwtAuth.getName())
        ) {
            String exchangedToken = tokenExchangeClient.exchange(jwtAuth.getToken().getTokenValue());
            log.debug("교환으로 발급받은 Access Token: {}", exchangedToken);
            request.getHeaders().setBearerAuth(exchangedToken);
        }

        return execution.execute(request, body);
    }
}
