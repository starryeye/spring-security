package dev.starryeye.oauth2_authorized_client_manager.client.interceptor.oauth2;

import dev.starryeye.oauth2_authorized_client_manager.config.OAuth2ClientConfig;
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
public class ClientCredentialsGrantRequestInterceptor implements ClientHttpRequestInterceptor {

    private final OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

        /**
         * todo OAuth2AuthorizedClientManager 내부 동작 설명하기..
         *  DefaultOAuth2AuthorizedClientManager 에는 successHandler 가 기본적으로 있는데, 여기서 동일한 핸들러(+ 로깅)로 등록해본다.
         *  아래 간단 정리 부분을 디버깅으로 확인해볼 것.
         *
         * 1. OAuth2AuthorizedClientManager 는 OAuth2AuthorizationContext 에 OAuth2AuthorizedClient 가 존재한다면 반환한다.
         * 2. OAuth2AuthorizationContext 에 OAuth2AuthorizedClient 가 존재하지 않으면, OAuth2AuthorizedClientProvider 에 토큰 요청을 위임한다.
         *
         * OAuth2AuthorizedClientProvider 는 권한 획득 방식에 따라 구현체가 다르다.
         *      client credentials grant :  ClientCredentialsOAuth2AuthorizedClientProvider
         *      refresh token : RefreshTokenOAuth2AuthorizedClientProvider (access token 이 만료 되었는데 refresh token 이 존재하면 이 방식으로 처리된다.)
         *
         */

        Authentication authentication = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();

        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId("my-keycloak-authorization-code")
                .principal(authentication.getName())
                .attribute(OAuth2ClientConfig.CUSTOM_ATTRIBUTE, "client_credentials_grant") // 여기에 값을 추가하면 request data 에 추가로 담길 것 같지만.. 아닌듯 (OAuth2ClientConfig::contextAttributesMapper 참고)
                .build();

        OAuth2AuthorizedClient authorizedClient = oAuth2AuthorizedClientManager.authorize(authorizeRequest);

        if (authorizedClient == null) {
            throw new IllegalStateException("OAuth2 클라이언트 인증 실패");
        }

        String token = authorizedClient.getAccessToken().getTokenValue();
        log.info("access token: {}", token);

        request.getHeaders().setBearerAuth(token);

        return execution.execute(request, body);
    }
}
