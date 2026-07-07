package dev.starryeye.custom_oauth2_authorization_service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class LoggingOAuth2AuthorizationService implements OAuth2AuthorizationService {

    /**
     * 프레임워크가 OAuth2AuthorizationService 를 언제 호출하는지 관찰하기 위한 로깅 데코레이터이다.
     *      실제 저장은 delegate(JpaOAuth2AuthorizationService)에 위임하고 호출 내용만 로그로 남긴다.
     *      grant flow 를 수행하면서 호출 시퀀스를 관찰해볼 것. (관찰 결과는 main class 주석에 정리해놓음)
     */

    private final OAuth2AuthorizationService delegate;

    @Override
    public void save(OAuth2Authorization authorization) {
        log.info("[save] id={}, grant={}, principal={}, 보유중인 토큰={}",
                authorization.getId(), authorization.getAuthorizationGrantType().getValue(), authorization.getPrincipalName(), tokenSummary(authorization));
        delegate.save(authorization);
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        log.info("[remove] id={}, grant={}, 보유중인 토큰={}",
                authorization.getId(), authorization.getAuthorizationGrantType().getValue(), tokenSummary(authorization));
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        log.info("[findById] id={}", id);
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        OAuth2Authorization result = delegate.findByToken(token, tokenType);
        log.info("[findByToken] tokenType={}, token(head)={}.., 조회결과={}",
                tokenType != null ? tokenType.getValue() : "null(전체 대상)", head(token), result != null ? result.getId() : "null");
        return result;
    }

    private String tokenSummary(OAuth2Authorization authorization) {

        List<String> summary = new ArrayList<>();

        if (authorization.getAttribute(OAuth2ParameterNames.STATE) != null) {
            summary.add("state");
        }
        OAuth2Authorization.Token<OAuth2AuthorizationCode> code = authorization.getToken(OAuth2AuthorizationCode.class);
        if (code != null) {
            summary.add("code(invalidated=" + code.isInvalidated() + ")");
        }
        if (authorization.getToken(OAuth2AccessToken.class) != null) {
            summary.add("accessToken");
        }
        if (authorization.getToken(OAuth2RefreshToken.class) != null) {
            OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getToken(OAuth2RefreshToken.class);
            summary.add("refreshToken(invalidated=" + refreshToken.isInvalidated() + ")");
        }
        if (authorization.getToken(OidcIdToken.class) != null) {
            summary.add("idToken");
        }

        return summary.isEmpty() ? "없음" : String.join(", ", summary);
    }

    private String head(String token) {
        return token.length() > 10 ? token.substring(0, 10) : token;
    }
}
