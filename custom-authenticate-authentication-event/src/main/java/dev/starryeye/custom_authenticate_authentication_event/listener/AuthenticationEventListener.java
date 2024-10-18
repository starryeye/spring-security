package dev.starryeye.custom_authenticate_authentication_event.listener;

import dev.starryeye.custom_authenticate_authentication_event.event.CustomAuthenticationSuccessEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.*;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationEventListener {

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent success) {
        // AuthenticationSuccessEvent 이벤트 수신기
        /**
         * 인증 처리 과정 중, ProviderManager(AuthenticationManager)::authenticate 에서
         * AuthenticationEventPublisher 를 통해 publishAuthenticationSuccess 를 호출하는 것을 볼 수 있다.
         *
         * publishAuthenticationSuccess 를 호출할 때 파라미터로 넣어주는 객체는
         * UsernamePasswordAuthenticationToken(Authentication 인증 객체) 이다.
         * -> publishAuthenticationSuccess 내부에서 Authentication 객체를 AuthenticationSuccessEvent 로 다시 감싸서 실제 발행
         */
        System.out.println("AuthenticationSuccessEvent success = " + success.getAuthentication().getName());
    }

    @EventListener
    public void onSuccess(InteractiveAuthenticationSuccessEvent success) {
        // InteractiveAuthenticationSuccessEvent 이벤트 수신기
        /**
         * UsernamePasswordAuthenticationFilter(AuthenticationFilter) 의 상위 클래스인 AbstractAuthenticationProcessingFilter 의
         * successfulAuthentication 메서드에서 발행하는 이벤트이다.
         */
        System.out.println("InteractiveAuthenticationSuccessEvent success = " + success.getAuthentication().getName());
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent failures) {
        // AuthenticationFailureBadCredentialsEvent 이벤트 수신기
        /**
         * 인증 처리 과정 중 예외가 발생하면..
         * ProviderManager(AuthenticationManager)::prepareException 를 통해 이벤트 발행
         */
        System.out.println("AuthenticationFailureBadCredentialsEvent failures = " + failures.getException().getMessage());
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent failures) {
        // AuthenticationFailureEvent 이벤트 수신기
        /**
         * AbstractAuthenticationFailureEvent 는 AuthenticationFailureBadCredentialsEvent 의 상위 클래스이다.
         * AuthenticationFailureBadCredentialsEvent 를 받는 수신기가 동작되면 AbstractAuthenticationFailureEvent 수신기도 동작한다.
         */
        System.out.println("AbstractAuthenticationFailureEvent failures = " + failures.getException().getMessage());
    }

    @EventListener
    public void onSuccess(CustomAuthenticationSuccessEvent success) {
        // 커스텀 CustomAuthenticationSuccessEvent 이벤트 수신기
        /**
         * 커스텀 이벤트를 발행하기 위해서 successHandler 를 이용한다. -> SecurityConfig 보자
         */
        System.out.println("CustomAuthenticationSuccessEvent success = " + success.getAuthentication().getName());
    }
}
