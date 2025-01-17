package dev.starryeye.auth_service.web.admin.controller.request;

import dev.starryeye.auth_service.web.admin.facade.request.ModifyUserUseCaseRequest;

import java.util.List;

public record UpdateUserRequest(
        String id,
        String username,
        Integer age,
        String password,
        List<String> roles
) {

    public ModifyUserUseCaseRequest toUseCase() {
        return new ModifyUserUseCaseRequest(Long.parseLong(id), username, age, password, roles);
    }
}
