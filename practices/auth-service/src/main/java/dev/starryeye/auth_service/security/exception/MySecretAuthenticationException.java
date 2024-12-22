package dev.starryeye.auth_service.security.exception;

import org.springframework.security.core.AuthenticationException;

public class MySecretAuthenticationException extends AuthenticationException {

    public MySecretAuthenticationException(String msg) {
        super(msg);
    }
}
