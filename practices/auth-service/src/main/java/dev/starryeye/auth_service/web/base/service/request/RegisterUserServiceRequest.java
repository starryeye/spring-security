package dev.starryeye.auth_service.web.base.service.request;

import lombok.Builder;
import lombok.Getter;

@Getter
public class RegisterUserServiceRequest {

    private final String username;
    private final String password;

    private final Integer age;

//    private final String roles;

    @Builder
    private RegisterUserServiceRequest(String username, String password, Integer age) {
        this.username = username;
        this.password = password;
        this.age = age;
    }
}
