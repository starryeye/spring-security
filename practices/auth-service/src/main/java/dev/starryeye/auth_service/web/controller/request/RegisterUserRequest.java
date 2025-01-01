package dev.starryeye.auth_service.web.controller.request;

import dev.starryeye.auth_service.web.service.request.RegisterUserServiceRequest;

public record RegisterUserRequest(
        String username,
        String password,

        Integer age,

        String roles
) {

    public RegisterUserServiceRequest toServiceRequest() {
        return RegisterUserServiceRequest.builder()
                .username(username)
                .password(password)
                .age(age)
                .roles(roles)
                .build();
    }
}
