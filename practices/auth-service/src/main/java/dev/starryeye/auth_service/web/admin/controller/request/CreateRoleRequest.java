package dev.starryeye.auth_service.web.admin.controller.request;

import dev.starryeye.auth_service.web.admin.facade.request.CreateRoleUseCaseRequest;

public record CreateRoleRequest(
        String roleName,
        String roleDesc,
        String isExpression
) {

    public CreateRoleUseCaseRequest toUseCase() {
        return new CreateRoleUseCaseRequest(roleName, roleDesc, isExpression);
    }
}
