package dev.starryeye.auth_service.api.controller.request;

import dev.starryeye.auth_service.api.service.request.RegisterUserServiceRequest;

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
