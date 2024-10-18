package dev.starryeye.custom_authenticate_authentication_event.event;

import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

public class CustomAuthenticationFailureEvent extends AbstractAuthenticationFailureEvent {

    public CustomAuthenticationFailureEvent(Authentication authentication, AuthenticationException exception) {
        super(authentication, exception);
    }
}
