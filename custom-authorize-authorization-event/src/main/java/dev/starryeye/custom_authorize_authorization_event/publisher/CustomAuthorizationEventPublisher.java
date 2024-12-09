package dev.starryeye.custom_authorize_authorization_event.publisher;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authorization.AuthorityAuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.event.AuthorizationGrantedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.function.Supplier;

public class CustomAuthorizationEventPublisher implements AuthorizationEventPublisher {

    private final AuthorizationEventPublisher delegate;
    private final ApplicationEventPublisher applicationEventPublisher;

    public CustomAuthorizationEventPublisher(AuthorizationEventPublisher delegate, ApplicationEventPublisher applicationEventPublisher) {
        this.delegate = delegate;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    // Deprecated.. 이지만 오버라이드는 해줘야한다. 어디에서 사용은되지 않는다.
    @Override
    public <T> void publishAuthorizationEvent(Supplier<Authentication> authentication, T object, AuthorizationDecision decision) {
        publishAuthorizationEvent(authentication, object, (AuthorizationResult) decision); // AuthorizationResult 사용하는 아래 메서드로 위임
    }

    @Override
    public <T> void publishAuthorizationEvent(Supplier<Authentication> authentication, T object, AuthorizationResult result) {
        /**
         * Authentication : 인증 객체
         * T : 권한 심사 대상 자원..
         * AuthorizationResult : AuthorizationManager 의 반환값으로 인가(권한 심사) 성공/실패 등의 정보가 담겨있다.
         *
         * 해당 메서드는 AuthorizationFilter 에 의해 호출된다.
         * 호출 시점은 권한 심사가 모두 끝나고 해당 메서드가 호출된다.
         */
        if (result == null) {
            return;
        }

        if (!result.isGranted()) {
            // 권한 심사 실패함.
            // delegate 로 실패 이벤트 발행 책임을 위임.
            this.delegate.publishAuthorizationEvent(authentication, object, result);
            return;
        }

        // 권한 심사 성공함.
        if (hasAdminAuthority(result)) {
            AuthorizationGrantedEvent<T> granted = new AuthorizationGrantedEvent<>(authentication, object, result);
            applicationEventPublisher.publishEvent(granted); // 성공 이벤트 발행
        }
    }

    private boolean hasAdminAuthority(AuthorizationResult result) {
        /**
         * AuthorityAuthorizationDecision (result) 이 가지고 있는 GrantedAuthority 들(자원에 접근하기 위한 권한 목록이다.) 중..
         * User 가 존재하면 true
         *
         * 즉, SecurityConfig 에서 requestMatcher 로 설정한 것들임.
         * /user 에 접근할 때만 true 가 될 것이다.
         */
        return (result instanceof AuthorityAuthorizationDecision authorityDecision)
                && authorityDecision.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_USER".equals(authority.getAuthority()));
    }
}
