package dev.starryeye.custom_csrf;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class CsrfTokenController {

    @GetMapping("/csrf")
    public String getCsrfToken(HttpServletRequest request, HttpSession session, CsrfToken csrfToken) {

        /**
         * CsrfFilter 에서 requestHandler.handle 과정을 통해 request attribute 에 csrf 토큰(지연로딩)을 넣어준걸 꺼내쓸 수 있음
         * -> CsrfTokenRequestAttributeHandler::handle
         *
         * 참고
         * 아래 tokenByControllerParameter, tokenInRequestAttribute, tokenInSession 모두 동일함.
         * 같은 인스턴스를 얻기위한 다양한 방법을 보여준다.
         *
         * 참고
         * csrfToken1 는 csrfToken2 와 동일함
         * csrfToken1 의 CsrfToken.class.getName() 은 변경될수 있을 듯한데..
         * csrfToken2 의 "_csrf" 문자열은 CSRF 헤더나 요청 파라미터 이름을 변경하더라도 변경되지 않을듯.. 단순 requestAttribute 이름임..
         *
         * 참고
         * request attribute 에서..
         * getToken() 을 수행하면, 세션에 저장되어있던 csrf 토큰을 조회한다. (HttpSessionCsrfTokenRepository)
         * - 세션자체가 없거나 세션이 있더라도 csrf 토큰이 없으면 생성해서 세션에 넣어주고 리턴된다.
         *      해당 요청은 GET 이므로 application 최초 부팅 및 최초 요청이면 위와 같이 될것이고
         *      세션에 csrf 토큰이 존재한다면 존재하는 토큰이 조회 될것이다..
         */

        String tokenByControllerParameter = csrfToken.getToken();

        // request attribute 에 저장된..
        CsrfToken csrfToken1 = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        CsrfToken csrfToken2 = (CsrfToken) request.getAttribute("_csrf"); //CsrfTokenRequestAttributeHandler 의 csrfRequestAttributeName 필드
        String tokenInRequestAttribute = csrfToken1.getToken();

        // 세션에 저장된..
        CsrfToken csrfToken3 = (CsrfToken) session.getAttribute("org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN");
        String tokenInSession = csrfToken3.getToken();

        /**
         * tokenInSession 은 쿠키가 동일하면 항상 동일한 세션에 접근하게 되고 tokenInSession 도 항상 같은 값이다.
         * 하지만, tokenInRequestAttribute 은 매 요청 시, tokenInSession 에 난수를 인코딩하여 변경한 값이라.. 매번 다르다.
         * -> 근데.. 과거에 생성했던 tokenInRequestAttribute 로 요청해도 되네..?
         */
        log.info("tokenByControllerParameter: {}", tokenByControllerParameter);
        log.info("tokenInRequestAttribute: {}", tokenInRequestAttribute);
        log.info("tokenInSession: {}", tokenInSession);
        return tokenInRequestAttribute;
    }
}
