package dev.starryeye.production_ready_authorization_server.config;

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

public class ResourceIndicatorValidatingAuthenticationProvider implements AuthenticationProvider {

    /**
     * resource indicator (RFC 8707) 의 발급 측 검증이다. (practices/simple-integration-...-with-token-exchange 이식.. 상세 원리는 이식 원본 주석 참고)
     *      client 가 요청한 resource 가 "그 client 에게 허용된 자원인지" 를 발급 시점에 확인하고 아니면 invalid_target 으로 거부한다.
     *      resource server 의 aud 검증(수신 측 방어)과 별개로.. 받으면 안 될 토큰의 발급 자체를 막는 통제다.
     *      (scope 를 등록 scope 상한으로 통제하는 것과 같은 구조)
     *
     * 허용 자원 데이터.. 이식 원본은 하드코딩 Map 이었지만 여기서는 DB 로 옮겼다.
     *      client 별 허용 목록은 client 등록 정보의 일부이므로 ClientSettings 의 커스텀 설정(아래 상수)으로 담는다.
     *      -> JpaRegisteredClientRepository 의 JSON 직렬화를 그대로 타고 DB(client_settings 컬럼)에 저장되고,
     *         검증 시점에는 RegisteredClient 조회로 함께 읽힌다. (별도 자원 카탈로그 테이블은 자원 메타데이터가 커질 때의 선택지)
     *
     * 검증 지점.. (이식 원본과 동일)
     *      authorization code 는 authorize 단계에서(refresh 재발급은 검증된 값의 재사용), token exchange 는 token 단계에서 검증한다.
     *      authorize 단계 에러는 등록된 redirect uri 일 때만 error redirect (open redirect 방지), token 단계는 400 JSON.
     *
     * 경계 조건..
     *      resource 파라미터가 없는 요청은 검증 대상이 아니다.. 대상 지정이 없으므로 기본 aud(요청 client 의 client_id)로 발급된다.
     *          (그 토큰은 aud 를 검증하는 resource server 에서는 어차피 수락되지 않는다)
     *      허용 목록이 등록되지 않은 client 가 resource 를 지정하면 무조건 거부된다.. 기본값은 "전부 허용" 이 아니라 "전부 거부" 다.
     */

    public static final String ALLOWED_RESOURCES_SETTING = "my.allowed-resources";

    private static final String ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc8707";

    private final AuthenticationProvider delegate;
    private final RegisteredClientRepository registeredClientRepository;

    public ResourceIndicatorValidatingAuthenticationProvider(AuthenticationProvider delegate, RegisteredClientRepository registeredClientRepository) {
        this.delegate = delegate;
        this.registeredClientRepository = registeredClientRepository;
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

        List<String> requestedResources = resolveResources(authorizeRequest.getAdditionalParameters().get("resource"));
        if (requestedResources.isEmpty()) {
            return;
        }

        RegisteredClient registeredClient = registeredClientRepository.findByClientId(authorizeRequest.getClientId());
        for (String resource : requestedResources) {
            if (!isAcceptable(registeredClient, resource)) {
                OAuth2Error error = new OAuth2Error("invalid_target", "unknown or unauthorized resource: " + resource, ERROR_URI);
                if (isRegisteredRedirectUri(registeredClient, authorizeRequest)) {
                    throw new OAuth2AuthorizationCodeRequestAuthenticationException(error, authorizeRequest); // client 로 error redirect
                }
                throw new OAuth2AuthorizationCodeRequestAuthenticationException(error, null); // 에러 페이지(400)
            }
        }
    }

    private void validateExchangeRequest(OAuth2TokenExchangeAuthenticationToken exchangeRequest) {

        RegisteredClient registeredClient = ((OAuth2ClientAuthenticationToken) exchangeRequest.getPrincipal()).getRegisteredClient();

        for (String resource : exchangeRequest.getResources()) {
            if (!isAcceptable(registeredClient, resource)) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_target", "unknown or unauthorized resource: " + resource, ERROR_URI));
            }
        }
    }

    /**
     * RFC 8707 의 형식 요건(절대 URI, fragment 금지)과 DB 에 저장된 client 별 허용 목록을 함께 검사한다.
     *      참고. token exchange 경로의 형식 위반은 기본 converter 가 먼저 invalid_request 로 거른다.. 여기 형식 검사는 주로 authorize 경로용.
     */
    private boolean isAcceptable(RegisteredClient registeredClient, String resource) {

        try {
            URI uri = URI.create(resource);
            if (!uri.isAbsolute() || uri.getFragment() != null) {
                return false;
            }
        } catch (IllegalArgumentException e) {
            return false;
        }

        if (registeredClient == null) {
            return false;
        }
        List<String> allowedResources = registeredClient.getClientSettings().getSetting(ALLOWED_RESOURCES_SETTING);
        return allowedResources != null && allowedResources.contains(resource);
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

    private boolean isRegisteredRedirectUri(RegisteredClient registeredClient, OAuth2AuthorizationCodeRequestAuthenticationToken authorizeRequest) {
        return StringUtils.hasText(authorizeRequest.getRedirectUri())
                && registeredClient != null
                && registeredClient.getRedirectUris().contains(authorizeRequest.getRedirectUri());
    }
}
