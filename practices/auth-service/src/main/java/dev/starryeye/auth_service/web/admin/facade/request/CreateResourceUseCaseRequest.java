package dev.starryeye.auth_service.web.admin.facade.request;

public record CreateResourceUseCaseRequest(
        String resourceName,
        String resourceType,
        String httpMethod,
        String orderNum,

        String roleName
) {
}
