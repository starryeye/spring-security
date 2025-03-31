package dev.starryeye.custom_social_login_client_with_form_login.exception;

public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String username) {
        super("User already exists with username: " + username);
    }
}
