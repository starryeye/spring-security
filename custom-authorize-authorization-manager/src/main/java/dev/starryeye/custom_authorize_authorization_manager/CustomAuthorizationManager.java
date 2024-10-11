package dev.starryeye.custom_authorize_authorization_manager;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

public class CustomAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String REQUIRED_ROLE = "ROLE_SECURE";

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext object) {

        Authentication authenticated = authentication.get(); // 인증 객체 접근

        if (
                authenticated == null
                || !authenticated.isAuthenticated()
                || authenticated instanceof AnonymousAuthenticationToken
        ) {
            return new AuthorizationDecision(false);
        }

        // 인증 객체에 담겨있는 GrantedAuthority 객체에 ROLE_SECURE 권한이 존재하면 true
        boolean hasRequiredRole = authenticated.getAuthorities().stream()
                .anyMatch(grantedAuthority -> REQUIRED_ROLE.equals(grantedAuthority.getAuthority()));

        return new AuthorizationDecision(hasRequiredRole);
    }
}
