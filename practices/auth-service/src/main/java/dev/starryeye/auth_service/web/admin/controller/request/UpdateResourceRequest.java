package dev.starryeye.auth_service.web.admin.controller.request;

import dev.starryeye.auth_service.web.admin.facade.request.ModifyResourceUseCaseRequest;

public record UpdateResourceRequest(
        String id,
        String resourceName,
        String resourceType,
        String httpMethod,
        String orderNum,

        String roleName
) {

    public ModifyResourceUseCaseRequest toUseCase() {
        return new ModifyResourceUseCaseRequest(Long.parseLong(id), resourceName, resourceType, httpMethod, orderNum, roleName);
    }
}
