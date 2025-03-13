package dev.starryeye.custom_oauth2_login_authorization_request_resolver;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.Map;
import java.util.function.Consumer;

public class MyOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    /**
     * PKCE 가 정상동작 하도록 커스텀하였다.
     */

    private static final String REGISTRATION_ID_URI_VARIABLE_NAME = "registrationId";
    private static final Consumer<OAuth2AuthorizationRequest.Builder> DEFAULT_PKCE_APPLIER = OAuth2AuthorizationRequestCustomizers
            .withPkce();

    private final AntPathRequestMatcher authorizationRequestMatcher;

    // 원래 개발자의 커스텀 OAuth2AuthorizationRequestResolver 이 없으면, DefaultOAuth2AuthorizationRequestResolver 객체가 사용된다.
    private final DefaultOAuth2AuthorizationRequestResolver defaultOAuth2AuthorizationRequestResolver;

    public MyOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository, String  authorizationRequestBaseUri) {
        this.authorizationRequestMatcher = new AntPathRequestMatcher(
                authorizationRequestBaseUri + "/{" + REGISTRATION_ID_URI_VARIABLE_NAME + "}");

        defaultOAuth2AuthorizationRequestResolver = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, authorizationRequestBaseUri);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {

        String clientRegistrationId = resolveRegistrationId(request);
        if (clientRegistrationId == null) {
            return null;
        }

        if ("keycloak-authorization-code-with-pkce-with-client-authentication".equals(clientRegistrationId)) { // pkce + client authentication 요청이면 추가적인 작업을 해준다.
            OAuth2AuthorizationRequest oAuth2AuthorizationRequest = defaultOAuth2AuthorizationRequestResolver.resolve(request);
            return customResolve(oAuth2AuthorizationRequest);
        }

        return defaultOAuth2AuthorizationRequestResolver.resolve(request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {

        if ("keycloak-authorization-code-with-pkce-with-client-authentication".equals(clientRegistrationId)) { // pkce + client authentication 요청이면 추가적인 작업을 해준다.
            OAuth2AuthorizationRequest oAuth2AuthorizationRequest = defaultOAuth2AuthorizationRequestResolver.resolve(request); // pkce 용 OAuth2AuthorizationRequest 가 아니라, 기본 OAuth2AuthorizationRequest 이 리턴된다.
            return customResolve(oAuth2AuthorizationRequest);
        }

        return defaultOAuth2AuthorizationRequestResolver.resolve(request, clientRegistrationId);
    }

    private OAuth2AuthorizationRequest customResolve(OAuth2AuthorizationRequest oAuth2AuthorizationRequest) {

        /**
         *  application.yml 에 pkce-with-client-authentication 설정에 보면, client-authentication-method 이 "none" 이 아니기 때문에
         *  PKCE 용 request 가 만들어지지 않는다. 그래서 DEFAULT_PKCE_APPLIER.accept(builder) 를 통해 PKCE 용 request 로 만들어준다.
         *
         *  참고, 아래와 같이 표준 파라미터가 아닌 커스텀 파라미터를 넣어 줄 수 있다. (extra)
         */

        Map<String, Object> extra = Map.of(
                "customKey1", "customValue1",
                "customKey2", "customValue2"
        );

        OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.from(oAuth2AuthorizationRequest)
                .additionalParameters(extra)
                ;
        DEFAULT_PKCE_APPLIER.accept(builder); // PKCE 용으로 만들어 줌, code_challenge, code_challenge_method 값 생성

        return builder.build();
    }

    private String resolveRegistrationId(HttpServletRequest request) {
        if (this.authorizationRequestMatcher.matches(request)) {
            return this.authorizationRequestMatcher.matcher(request)
                    .getVariables()
                    .get(REGISTRATION_ID_URI_VARIABLE_NAME);
        }
        return null;
    }
}
