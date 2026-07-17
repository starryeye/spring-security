package dev.starryeye.production_ready_authorization_server.config;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.StringUtils;

public class RequestObjectRejectingAuthenticationProvider implements AuthenticationProvider {

    /**
     * 인가 요청의 request/request_uri 파라미터(JWT request object)를 스펙대로 거부한다.
     *
     * 왜 필요한가..
     *      spring authorization server 는 request object(OIDC Core 6)를 지원하지 않는데..
     *      기본 동작은 해당 파라미터를 "조용히 무시" 하고 쿼리 파라미터만으로 인가를 진행하는 것이다. (거부 로직이 아예 없다)
     *      스펙은 미지원 OP 가 request(request_uri) 파라미터를 받으면
     *      request_not_supported(request_uri_not_supported) 에러를 응답하도록 요구한다. (OIDC Core 6.1)
     *      무시하고 진행하면 client 는 request object 안의 값이 반영됐다고 믿게 되므로 조용한 무시가 더 위험하다.
     *
     * 동작 구조..
     *      기본 converter(OAuth2AuthorizationCodeRequestAuthenticationConverter)는 표준 파라미터 외 전부를
     *      additionalParameters 로 넘기므로, 기본 provider 를 감싸 위임 전에 request/request_uri 존재를 검사한다.
     *      등록은 OAuth2AuthorizationEndpointConfigurer::authenticationProviders 로 기본 provider 를 교체하는 방식이다. (AuthorizationServerConfig 참고)
     *
     * 에러 응답 방식.. OAuth2AuthorizationEndpointFilter::sendErrorResponse 의 규칙을 따른다.
     *      예외에 담긴 token 의 redirectUri 가 있으면 -> client 로 error redirect (state 포함)
     *      token 이 없으면(null) -> 400 에러 페이지
     *      redirect 는 등록된 redirect uri 와 정확히 일치할 때만 허용해야 한다.. 검증 없이 token 을 실어 던지면
     *      공격자가 지정한 주소로 redirect 되는 open redirect 가 된다. (기본 provider 도 redirect uri 검증 전 오류는 에러 페이지로 처리한다)
     */

    private final AuthenticationProvider delegate;
    private final RegisteredClientRepository registeredClientRepository;

    public RequestObjectRejectingAuthenticationProvider(AuthenticationProvider delegate, RegisteredClientRepository registeredClientRepository) {
        this.delegate = delegate;
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        OAuth2AuthorizationCodeRequestAuthenticationToken authenticationToken = (OAuth2AuthorizationCodeRequestAuthenticationToken) authentication;

        if (authenticationToken.getAdditionalParameters().containsKey("request")) {
            throwError(authenticationToken, "request_not_supported", "request parameter (request object) is not supported");
        }
        if (authenticationToken.getAdditionalParameters().containsKey("request_uri")) {
            throwError(authenticationToken, "request_uri_not_supported", "request_uri parameter is not supported");
        }

        return delegate.authenticate(authentication);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }

    private void throwError(OAuth2AuthorizationCodeRequestAuthenticationToken authenticationToken, String errorCode, String description) {

        OAuth2Error error = new OAuth2Error(errorCode, description, "https://openid.net/specs/openid-connect-core-1_0.html#RequestObject");

        if (isRegisteredRedirectUri(authenticationToken)) {
            // 검증된 redirect uri 이므로 client 에게 error redirect 로 알린다.
            throw new OAuth2AuthorizationCodeRequestAuthenticationException(error, authenticationToken);
        }
        // redirect uri 를 신뢰할 수 없으면 에러 페이지(400)로 처리한다.
        throw new OAuth2AuthorizationCodeRequestAuthenticationException(error, null);
    }

    private boolean isRegisteredRedirectUri(OAuth2AuthorizationCodeRequestAuthenticationToken authenticationToken) {

        if (!StringUtils.hasText(authenticationToken.getRedirectUri())) {
            return false;
        }

        RegisteredClient registeredClient = registeredClientRepository.findByClientId(authenticationToken.getClientId());

        // 기본 provider 와 동일하게 정확 일치(exact match)로 판단한다.
        return registeredClient != null && registeredClient.getRedirectUris().contains(authenticationToken.getRedirectUri());
    }
}
