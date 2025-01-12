package dev.starryeye.auth_service.web.admin.controller.request;

import dev.starryeye.auth_service.web.admin.facade.request.UpdateUserUseCaseRequest;

import java.util.List;

public record UpdateUserRequest(
        String id,
        String username,
        Integer age,
        String password,
        List<String> roles
) {

    public UpdateUserUseCaseRequest toUseCase() {
        return new UpdateUserUseCaseRequest(id, username, age, password, roles);
    }
}
