package dev.starryeye.auth_service.security.base;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.util.matcher.RequestMatcherEntry;

import java.util.List;
import java.util.function.Supplier;

@RequiredArgsConstructor
public class MyDynamicAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final AuthorizationDecision DEFAULT_AUTHORIZATION_DECISION_IS_DENY = new AuthorizationDecision(false);

    private final List<RequestMatcherEntry<AuthorizationManager<RequestAuthorizationContext>>> matcherEntries;

    // deprecated..
    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext object) {

        // 권한 심사

        return DEFAULT_AUTHORIZATION_DECISION_IS_DENY;
    }

    @Override
    public void verify(Supplier<Authentication> authentication, RequestAuthorizationContext object) {
        AuthorizationManager.super.verify(authentication, object);
    }
}
