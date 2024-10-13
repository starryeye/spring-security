package dev.starryeye.custom_authorize_authorization_manager_2.config;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;

import java.util.function.Supplier;

public class CustomPreAuthorizationManager implements AuthorizationManager<MethodInvocation> {

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, MethodInvocation object) {

        /**
         * 인증 여부를 심사 한다. 인증 되어 있다면 true
         */

        Authentication authenticated = authentication.get(); // 인증 객체 접근

        if (
                authenticated == null
                        || !authenticated.isAuthenticated()
                        || authenticated instanceof AnonymousAuthenticationToken
        ) {
            return new AuthorizationDecision(false);
        }


        return new AuthorizationDecision(true);
    }
}
