package dev.starryeye.auth_service.web.admin.facade.request;

public record ModifyResourceUseCaseRequest(
        Long id,
        String resourceName,
        String resourceType,
        String httpMethod,
        String orderNum,

        String roleName
) {
}
