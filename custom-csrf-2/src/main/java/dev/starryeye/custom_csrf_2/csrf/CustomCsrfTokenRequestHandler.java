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
     *
     * CsrfTokenRequestAttributeHandler..
     *
     * XorCsrfTokenRequestAttributeHandler..
     *
     *
     */

    private final CsrfTokenRequestHandler delegate;

    public CustomCsrfTokenRequestHandler() {
        this.delegate = new XorCsrfTokenRequestAttributeHandler();
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> deferredCsrfToken) {
        this.delegate.handle(request, response, deferredCsrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {

        if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
            return super.resolveCsrfTokenValue(request, csrfToken); // 인코딩 하지 않음
        }

        return this.delegate.resolveCsrfTokenValue(request, csrfToken); // 인코딩 함
    }
}
