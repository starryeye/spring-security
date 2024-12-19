package dev.starryeye.auth_service.api.controller.request;

import dev.starryeye.auth_service.api.service.request.RegisterUserServiceRequest;

public record RegisterUserRequest(
        String identifier,
        String password,

        String username,
        Integer age,

        String roles
) {

    public RegisterUserServiceRequest toServiceRequest() {
        return RegisterUserServiceRequest.builder()
                .identifier(identifier)
                .password(password)
                .username(username)
                .age(age)
                .roles(roles)
                .build();
    }
}
