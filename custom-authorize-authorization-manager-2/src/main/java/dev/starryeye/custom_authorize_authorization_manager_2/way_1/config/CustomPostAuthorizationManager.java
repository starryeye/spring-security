package dev.starryeye.custom_authorize_authorization_manager_2.way_1.config;

import dev.starryeye.custom_authorize_authorization_manager_2.Account;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.method.MethodInvocationResult;
import org.springframework.security.core.Authentication;

import java.util.function.Supplier;

public class CustomPostAuthorizationManager implements AuthorizationManager<MethodInvocationResult> {

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, MethodInvocationResult resultObject) {

        Authentication authenticated = authentication.get(); // 인증 객체 접근

        if (
                authenticated == null
                        || !authenticated.isAuthenticated()
                        || authenticated instanceof AnonymousAuthenticationToken
        ) {
            return new AuthorizationDecision(false);
        }

        // 메서드 최종 리턴 객체 접근
        if (!(resultObject.getResult() instanceof Account account)) { // instanceof + 패턴 매칭
            throw new IllegalArgumentException("Return Object is not Account Type");
        }

        // 메서드 최종 리턴 객체(Account) 의 owner 가 인증 객체의 name 과 동일해야 true
        boolean isGranted = account.owner().equals(authenticated.getName());

        return new AuthorizationDecision(isGranted);
    }
}
