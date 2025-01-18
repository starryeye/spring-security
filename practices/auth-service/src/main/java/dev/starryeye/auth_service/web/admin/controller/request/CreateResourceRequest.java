package dev.starryeye.auth_service.web.admin.controller.request;

import dev.starryeye.auth_service.web.admin.facade.request.CreateResourceUseCaseRequest;

public record CreateResourceRequest(
        String resourceName,
        String resourceType,
        String httpMethod,
        String orderNum,

        String roleName
) {

    public CreateResourceUseCaseRequest toUseCase() {
        return new CreateResourceUseCaseRequest(resourceName, resourceType, httpMethod, Integer.parseInt(orderNum), roleName);
    }
}
