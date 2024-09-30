package dev.starryeye.custom_csrf_2.csrf;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

public class CustomCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

    /**
     * CustomCsrfTokenRequestHandler..
     * - XorCsrfTokenRequestAttributeHandler 대신 사용할 것이다.
     * - 헤더의 csrf 토큰은 원본으로 사용
     * - 요청 파라미터의 csrf 토큰은 암호화/복호화가 필요하도록 사용
     *      JavaScript 에서 헤더가 아닌 요청 파라미터로 셋팅했다면 이것 역시.. 원본 사용이 필요함.
     *
     *
     * 참고
     * CsrfTokenRequestAttributeHandler..
     * - HttpServletRequest 의 Attribute 에 csrf 토큰을 넣어주는 역할
     * - 요청 데이터의 헤더(X-XSRF-TOKEN, X-CSRF-TOKEN) 나 요청 파라미터(_csrf) 에서 csrf 토큰을 파싱해내는 역할
     *
     * XorCsrfTokenRequestAttributeHandler..
     * - CsrfTokenRequestAttributeHandler 을 상속하고 기본으로 설정되는 인스턴스이다.
     * - Client 로 보낼 csrf 토큰은 암호화하고 Client 에서 받은 csrf 토큰은 복호화한다.
     */

    private final CsrfTokenRequestHandler delegate;

    public CustomCsrfTokenRequestHandler() {
        this.delegate = new XorCsrfTokenRequestAttributeHandler();
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> deferredCsrfToken) {

        /**
         * HttpServletRequest 의 Attribute 에 csrf 토큰을 넣어주는 역할
         *      supplier 를 이용하여 셋팅만 한다.
         *          실제 필요시 getToken 을 하면 그때 모든 초기화 및
         *          사용중인 csrfTokenRepository 를 이용하여 세션에 저장을 하던.. 쿠키 셋팅을 하던.. 하고 토큰 생성함..
         */

        this.delegate.handle(request, response, deferredCsrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {

        /**
         * 요청 데이터에서 헤더나 요청 파라미터에 존재하는 csrf 토큰을 추출함
         */

        if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
            return super.resolveCsrfTokenValue(request, csrfToken); // 복호화 하지 않고 얻는다.
        }

        return this.delegate.resolveCsrfTokenValue(request, csrfToken); // 복호화 하여 얻는다.
    }
}
