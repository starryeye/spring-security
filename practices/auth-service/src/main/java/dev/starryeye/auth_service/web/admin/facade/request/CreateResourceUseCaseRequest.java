package dev.starryeye.auth_service.web.admin.facade.request;

public record CreateResourceUseCaseRequest(
        String resourceName,
        String resourceType,
        String httpMethod,
        Integer orderNum,

        String roleName
) {
}
