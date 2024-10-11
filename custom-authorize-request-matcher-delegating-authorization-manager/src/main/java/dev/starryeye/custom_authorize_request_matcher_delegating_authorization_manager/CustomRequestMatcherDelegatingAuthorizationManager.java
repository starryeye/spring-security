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
