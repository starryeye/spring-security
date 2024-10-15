package dev.starryeye.custom_authorize_method_interceptor.config;

import lombok.RequiredArgsConstructor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@RequiredArgsConstructor
public class CustomMethodInterceptor implements MethodInterceptor {

    /**
     * 커스텀 MethodInterceptor (Advice) 를 만들어본다.
     *
     * AuthorizationManagerBeforeMethodInterceptor 는 AuthorizationAdvisor 를 구현하며
     * AuthorizationAdvisor 는 MethodInterceptor 를 상속한다.
     * MethodInterceptor 는 최종적으로 Advice 를 상속한다.
     */

    // 권한을 심사할 AuthorizationManager 를 주입 받는다.
    private final AuthorizationManager<MethodInvocation> authorizationManager;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {

        // SecurityContextHolder 에 존재하는 인증 객체 얻기
        Authentication authentication = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();

        // 주입된 AuthorizationManager 로 권한 심사를 진행
        boolean granted = authorizationManager.check(
                () -> authentication,
                invocation
        ).isGranted();

        // 권한 심사 결과에 따라 원본 객체 수행 or AccessDeniedException
        if (granted) {
            return invocation.proceed();
        }

        throw new AccessDeniedException("Access denied");
    }
}
