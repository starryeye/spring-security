package dev.starryeye.custom_authenticate_authentication_event_publisher.event;

import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

public class CustomAuthenticationFailureEvent extends AbstractAuthenticationFailureEvent {

    /**
     * 인증 실패 이벤트는 Success 와 다르게 (success 는 AbstractAuthenticationEvent 를 상속받아도 됨)
     * AbstractAuthenticationFailureEvent 를 상속받아야 API 호환이 좋다.
     */

    public CustomAuthenticationFailureEvent(Authentication authentication, AuthenticationException exception) {
        super(authentication, exception);
    }
}
