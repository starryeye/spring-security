package dev.starryeye.custom_authenticate_authentication_event_publisher.exception;

import org.springframework.security.core.AuthenticationException;

public class CustomAuthenticationException extends AuthenticationException {
    /**
     * 커스텀 예외
     */
    public CustomAuthenticationException(String msg) {
        super(msg);
    }
}
