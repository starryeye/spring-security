package dev.starryeye.production_ready_authorization_server.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

public class PostToGetAuthorizationRequestEntryPoint implements AuthenticationEntryPoint {

    /**
     * 미인증 POST 인가 요청을 GET redirect 로 바꿔 로그인 왕복을 견디게 하는 AuthenticationEntryPoint 이다.
     *
     * 왜 필요한가..
     *      OIDC 는 authorize endpoint 가 GET 과 POST 를 모두 지원하도록 요구한다. (OIDC Core 3.1.2.1)
     *      인증된 세션의 POST 인가 요청은 정상 동작하지만.. 미인증 POST 는 로그인을 거쳐 saved request 로 복귀하는데
     *      spring security 의 saved request 재현이 GET 이라 POST form 파라미터가 유실된다.
     *          saved request wrapper 가 저장된 파라미터를 노출해주더라도..
     *          spring authorization server 의 converter 는 GET 요청에서 query string 에 있는 파라미터만 읽으므로
     *          (OAuth2EndpointUtils::getQueryParameters) 재현된 요청은 invalid_request(400)가 된다.
     *
     * 동작..
     *      미인증 POST 인가 요청(response_type 존재)이면 form 파라미터를 query 로 옮긴 같은 주소로 302 redirect 한다.
     *      브라우저는 302 를 GET 으로 따라가므로.. 이후는 일반 GET 인가 요청과 동일하게 로그인 왕복을 견딘다.
     *      그 외 요청은 위임 진입점(로그인 페이지 redirect)으로 처리한다.
     *
     * 참고. 같은 POST "/oauth2/authorize" 라도 consent 제출에는 response_type 파라미터가 없어 이 변환 대상이 아니다.
     *      (consent 제출은 인증된 세션에서만 일어나므로 애초에 entry point 까지 오지 않는다)
     * 참고. POST 인가 요청은 파라미터를 URL(브라우저 히스토리, 서버 access log)에 남기지 않는 장점이 있는데..
     *      이 변환은 그 장점을 포기하는 트레이드오프다. (인가 요청 파라미터에 비밀 값은 없으므로 수용 가능)
     */

    private final AuthenticationEntryPoint delegate;

    public PostToGetAuthorizationRequestEntryPoint(AuthenticationEntryPoint delegate) {
        this.delegate = delegate;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, jakarta.servlet.ServletException {

        if ("POST".equals(request.getMethod()) && request.getParameter(OAuth2ParameterNames.RESPONSE_TYPE) != null) {

            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(request.getRequestURL().toString());
            request.getParameterMap().forEach((name, values) -> {
                for (String value : values) {
                    builder.queryParam(name, value);
                }
            });

            response.sendRedirect(builder.encode().build().toUriString());
            return;
        }

        delegate.commence(request, response, authException);
    }
}
