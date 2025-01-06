package dev.starryeye.auth_service.web.admin.facade.response;

import dev.starryeye.auth_service.domain.MyResource;

public record ResourceResponse(
        String id,
        String resourceName,
        String resourceType,
        String httpMethod,
        String orderNum
) {

    public static ResourceResponse from(MyResource myResource) {
        return new ResourceResponse(
                myResource.getId().toString(),
                myResource.getName(),
                myResource.getType().name(),
                myResource.getHttpMethod(),
                myResource.getOrderNumber()
        );
    }
}
