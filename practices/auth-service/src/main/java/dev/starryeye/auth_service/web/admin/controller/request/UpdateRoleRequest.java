package dev.starryeye.auth_service.web.admin.controller.request;

import dev.starryeye.auth_service.web.admin.facade.request.ModifyRoleUseCaseRequest;

public record UpdateRoleRequest(
        String id,
        String roleName,
        String roleDesc,
        String isExpression
) {

    public ModifyRoleUseCaseRequest toUseCase() {
        return new ModifyRoleUseCaseRequest(Long.parseLong(id), roleName, roleDesc, isExpression);
    }
}
