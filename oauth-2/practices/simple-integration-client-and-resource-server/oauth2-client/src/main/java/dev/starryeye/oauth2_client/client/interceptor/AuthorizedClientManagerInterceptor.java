package dev.starryeye.oauth2_client.client.interceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizedClientManagerInterceptor implements ClientHttpRequestInterceptor {

    private static final String CLIENT_REGISTRATION_ID = "my-keycloak";
    private final OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 인증된 사용자이고 anonymousUser가 아닐 경우에만 token 설정
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())) {

            OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                    .withClientRegistrationId(CLIENT_REGISTRATION_ID)
                    .principal(authentication)
                    .build();

            OAuth2AuthorizedClient authorizedClient = oAuth2AuthorizedClientManager.authorize(authorizeRequest);

            if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
                String token = authorizedClient.getAccessToken().getTokenValue();
                log.debug("Access token found, setting Authorization header. access token: {}", token);
                request.getHeaders().setBearerAuth(token);
            } else {
                log.debug("No authorized client or access token available. Proceeding without Authorization header.");
            }

        } else {
            log.debug("No authenticated user found. Proceeding without Authorization header.");
        }

        // 항상 요청은 실행됨
        return execution.execute(request, body);
    }
}
