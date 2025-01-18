package dev.starryeye.auth_service.security.base;

import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcherEntry;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class MyDynamicAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    /**
     * RequestMatcherDelegatingAuthorizationManager 의 역할과 비슷하게 만듬
     * - 참조하고있는 AuthorizationManager 에게 권한심사를 위임한다.
     *
     * 참고.
     * MyDynamicAuthorizationManager 는 RequestMatcherDelegatingAuthorizationManager 를 대체하게 되는게 아니라
     *      RequestMatcherDelegatingAuthorizationManager 에 의해 호출되어 수행되는 AuthorizationManager 이다.
     *          SecurityConfig 에서 어떠한 요청이 오더라도 AuthorizationManager 를 하나(MyDynamicAuthorizationManager)만 쓰도록 하였음
     */
    private final MyDynamicAuthorizationService authorizationService;
    private final HandlerMappingIntrospector introspector;

    private final AuthorizationDecision defaultAuthorizationDecision;

    private final List<RequestMatcherEntry<AuthorizationManager<RequestAuthorizationContext>>> matcherEntries;

    public MyDynamicAuthorizationManager(HandlerMappingIntrospector introspector, MyDynamicAuthorizationService authorizationService) {

        this.defaultAuthorizationDecision = authorizationService.getDefaultDecision();
        this.introspector = introspector;
        this.authorizationService = authorizationService;
        this.matcherEntries = initializeMatcherEntries();
    }

    public synchronized void refreshMatcherEntries() {
        this.matcherEntries.clear();
        this.matcherEntries.addAll(initializeMatcherEntries());
    }

    // deprecated..
    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext requestAuthorizationContext) {

        // 권한 심사
        for (RequestMatcherEntry<AuthorizationManager<RequestAuthorizationContext>> matcherEntry : this.matcherEntries) {
            RequestMatcher matcher = matcherEntry.getRequestMatcher();
            RequestMatcher.MatchResult matchResult = matcher.matcher(requestAuthorizationContext.getRequest());

            if (matchResult.isMatch()) {
                AuthorizationManager<RequestAuthorizationContext> manager = matcherEntry.getEntry();
                return manager.check( // 권한 심사 위임
                        authentication,
                        new RequestAuthorizationContext(
                                requestAuthorizationContext.getRequest(),
                                matchResult.getVariables()
                        )
                );
            }
        }

        return defaultAuthorizationDecision;
    }
    @Override
    public void verify(Supplier<Authentication> authentication, RequestAuthorizationContext object) {
        AuthorizationManager.super.verify(authentication, object);
    }

    private List<RequestMatcherEntry<AuthorizationManager<RequestAuthorizationContext>>> initializeMatcherEntries() {
        Map<String, String> urlRoleMappings = authorizationService.getUrlRoleMappings();
        return urlRoleMappings.entrySet().stream()
                .map(entry ->
                        new RequestMatcherEntry<>(
                                new MvcRequestMatcher(introspector, entry.getKey()), // resource path
                                getAuthorizationManager(entry.getValue()) // 권한 검사기
                        )
                )
                .toList();
    }

    private AuthorizationManager<RequestAuthorizationContext> getAuthorizationManager(String role) {

        if (StringUtils.hasText(role)) {

            if (role.startsWith("ROLE_")) {
                return AuthorityAuthorizationManager.hasAuthority(role);
            } else {
                return new WebExpressionAuthorizationManager(role);
            }
        }

        return null;
    }
}
