package dev.starryeye.authorization_server;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenExchangeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ResourceIndicatorValidatingAuthenticationProvider implements AuthenticationProvider {

    /**
     * resource indicator (RFC 8707) 의 발급 측 검증을 담당한다.
     *      client 가 요청한 resource 가 "이 서버가 아는 자원인지 + 그 client 에게 허용된 자원인지" 를 발급 시점에 확인하고..
     *      아니면 스펙이 정의한 invalid_target 에러로 거부한다.
     *
     * 왜 resource server 의 aud 검증만으로 부족한가.. (양쪽의 목적이 다르다)
     *      resource server 의 aud 검증 : "이 토큰이 나를 위한 것인가" (수신 측 방어.. 잘못 온 토큰을 거른다)
     *      여기(발급 측) 검증 : "이 client 가 이 대상용 토큰을 받을 자격이 있는가" (받으면 안 될 토큰이 발급되는 것 자체를 막는다)
     *      scope 와 같은 구조다.. scope 도 resource server 가 검증하지만 발급은 등록 scope 상한으로 통제하듯,
     *      대상(aud)도 발급 시점 통제가 있어야 "권한 축소" 의미가 성립한다. (fail-fast 로 디버깅이 쉬워지는 것은 덤)
     *
     * 검증 지점..
     *      authorization code : authorize 단계(OAuth2AuthorizationCodeRequestAuthenticationToken)에서 검증한다.
     *          (token 단계에는 resource 가 authorization 저장소 안에 있어 여기서 걸러두는 것이 단순하다.. refresh 재발급도 검증된 값의 재사용)
     *      token exchange : token 단계(OAuth2TokenExchangeAuthenticationToken)에서 getResources() 를 검증한다.
     *      에러 전달.. authorize 단계는 등록된 redirect uri 일 때만 error redirect (open redirect 방지), token 단계는 400 JSON.
     */

    private static final String ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc8707";

    private final AuthenticationProvider delegate;
    private final RegisteredClientRepository registeredClientRepository;
    private final Map<String, Set<String>> allowedResourcesByClientId;

    public ResourceIndicatorValidatingAuthenticationProvider(
            AuthenticationProvider delegate,
            RegisteredClientRepository registeredClientRepository,
            Map<String, Set<String>> allowedResourcesByClientId
    ) {
        this.delegate = delegate;
        this.registeredClientRepository = registeredClientRepository;
        this.allowedResourcesByClientId = allowedResourcesByClientId;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        if (authentication instanceof OAuth2AuthorizationCodeRequestAuthenticationToken authorizeRequest) {
            validateAuthorizeRequest(authorizeRequest);
        } else if (authentication instanceof OAuth2TokenExchangeAuthenticationToken exchangeRequest) {
            validateExchangeRequest(exchangeRequest);
        }

        return delegate.authenticate(authentication);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }

    private void validateAuthorizeRequest(OAuth2AuthorizationCodeRequestAuthenticationToken authorizeRequest) {

        for (String resource : resolveResources(authorizeRequest.getAdditionalParameters().get("resource"))) {
            if (!isAcceptable(authorizeRequest.getClientId(), resource)) {
                OAuth2Error error = new OAuth2Error("invalid_target", "unknown or unauthorized resource: " + resource, ERROR_URI);
                if (isRegisteredRedirectUri(authorizeRequest)) {
                    throw new OAuth2AuthorizationCodeRequestAuthenticationException(error, authorizeRequest); // client 로 error redirect
                }
                throw new OAuth2AuthorizationCodeRequestAuthenticationException(error, null); // 에러 페이지(400)
            }
        }
    }

    private void validateExchangeRequest(OAuth2TokenExchangeAuthenticationToken exchangeRequest) {

        String clientId = ((OAuth2ClientAuthenticationToken) exchangeRequest.getPrincipal()).getRegisteredClient().getClientId();

        for (String resource : exchangeRequest.getResources()) {
            if (!isAcceptable(clientId, resource)) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_target", "unknown or unauthorized resource: " + resource, ERROR_URI));
            }
        }
    }

    /**
     * RFC 8707 의 형식 요건(절대 URI, fragment 금지)과 허용 목록을 함께 검사한다.
     *      참고. token exchange 경로의 형식 위반은 기본 converter 가 먼저 invalid_request 로 거른다..
     *      (값을 발급에 쓰지 않으면서 URI 형식 검증만은 한다) 여기의 형식 검사는 주로 authorize 경로(형식 검증 없음)용이다.
     */
    private boolean isAcceptable(String clientId, String resource) {
        try {
            URI uri = URI.create(resource);
            if (!uri.isAbsolute() || uri.getFragment() != null) {
                return false;
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
        return allowedResourcesByClientId.getOrDefault(clientId, Set.of()).contains(resource);
    }

    // authorize 요청의 resource 파라미터는 반복 지정이 허용된다(RFC 8707).. 단수(String)와 복수(String[]) 모두 다룬다.
    private List<String> resolveResources(Object resourceParameter) {
        List<String> resources = new ArrayList<>();
        if (resourceParameter instanceof String value) {
            resources.add(value);
        } else if (resourceParameter instanceof String[] values) {
            resources.addAll(List.of(values));
        }
        return resources;
    }

    private boolean isRegisteredRedirectUri(OAuth2AuthorizationCodeRequestAuthenticationToken authorizeRequest) {
        if (!StringUtils.hasText(authorizeRequest.getRedirectUri())) {
            return false;
        }
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(authorizeRequest.getClientId());
        return registeredClient != null && registeredClient.getRedirectUris().contains(authorizeRequest.getRedirectUri());
    }
}
