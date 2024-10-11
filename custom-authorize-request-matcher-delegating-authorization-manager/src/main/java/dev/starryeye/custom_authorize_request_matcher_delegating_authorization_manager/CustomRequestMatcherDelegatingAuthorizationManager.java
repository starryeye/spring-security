package dev.starryeye.custom_authorize_request_matcher_delegating_authorization_manager;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.access.intercept.RequestMatcherDelegatingAuthorizationManager;
import org.springframework.security.web.util.matcher.RequestMatcherEntry;

import java.util.List;
import java.util.function.Supplier;

public class CustomRequestMatcherDelegatingAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final RequestMatcherDelegatingAuthorizationManager requestMatcherDelegatingAuthorizationManager;

    public CustomRequestMatcherDelegatingAuthorizationManager(List<RequestMatcherEntry<AuthorizationManager<RequestAuthorizationContext>>> mappings) {
        /**
         * CustomRequestMatcherDelegatingAuthorizationManager 는 RequestMatcherDelegatingAuthorizationManager 를 내부 필드로 가지며
         * check, verify 메서드는 바로 위임만 한다.
         *
         * 내부 RequestMatcherDelegatingAuthorizationManager 필드 인스턴스는 생성자에서 직접 생성하며
         * 외부에서 requestMatcherEntries 를 주입한 것을 사용한다.
         */
        this.requestMatcherDelegatingAuthorizationManager = RequestMatcherDelegatingAuthorizationManager.builder()
                .mappings(requestMatcherEntries ->
                        requestMatcherEntries.addAll(mappings)
                )
                .build();
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext object) {
        return requestMatcherDelegatingAuthorizationManager.check(authentication,object.getRequest()); // 위임
    }

    @Override
    public void verify(Supplier<Authentication> authentication, RequestAuthorizationContext object) {
        AuthorizationManager.super.verify(authentication, object);
    }
}
