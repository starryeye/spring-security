package dev.starryeye.custom_authenticate_authentication_event_publisher.event;

import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

public class CustomDefaultAuthenticationFailureEvent extends AbstractAuthenticationFailureEvent {

    public CustomDefaultAuthenticationFailureEvent(Authentication authentication, AuthenticationException exception) {
        super(authentication, exception);
    }
}
