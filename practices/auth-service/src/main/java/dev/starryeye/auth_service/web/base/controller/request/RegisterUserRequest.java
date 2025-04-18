package dev.starryeye.auth_service.web.base.controller.request;

import dev.starryeye.auth_service.web.base.service.request.RegisterUserServiceRequest;

public record RegisterUserRequest(
        String username,
        String password,

        Integer age

) {

    public RegisterUserServiceRequest toServiceRequest() {
        return RegisterUserServiceRequest.builder()
                .username(username)
                .password(password)
                .age(age)
//                .roles(roles)
                .build();
    }
}
