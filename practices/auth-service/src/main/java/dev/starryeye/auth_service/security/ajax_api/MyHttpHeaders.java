package dev.starryeye.auth_service.security.ajax_api;

import jakarta.servlet.http.HttpServletRequest;

public class MyHttpHeaders {

    // headers
    private static final String X_REQUESTED_WITH = "X-Requested-With";

    // headers value
    private static final String XML_HTTP_REQUEST = "XMLHttpRequest";

    public static boolean isAjax(HttpServletRequest request) {
        return XML_HTTP_REQUEST.equals(request.getHeader(X_REQUESTED_WITH));
    }
}
