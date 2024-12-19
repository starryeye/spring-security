package dev.starryeye.auth_service.api.service.request;

import lombok.Builder;
import lombok.Getter;

@Getter
public class RegisterUserServiceRequest {

    private final String identifier;
    private final String password;

    private final String username;
    private final Integer age;

    private final String roles;

    @Builder
    private RegisterUserServiceRequest(String identifier, String password, String username, Integer age, String roles) {
        this.identifier = identifier;
        this.password = password;
        this.username = username;
        this.age = age;
        this.roles = roles;
    }
}
