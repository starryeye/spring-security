package dev.starryeye.custom_authenticate_authentication_event.event;

import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.core.Authentication;

public class CustomAuthenticationSuccessEvent extends AbstractAuthenticationEvent {

    public CustomAuthenticationSuccessEvent(Authentication authentication) {
        super(authentication);
    }
}
