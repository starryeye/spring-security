package dev.starryeye.auth_service.web.admin.facade.response;

import dev.starryeye.auth_service.domain.MyResource;

public record ResourceResponse(
        String name,
        String type,
        String httpMethod,
        String orderNumber
) {

    public static ResourceResponse of(MyResource myResource) {
        return new ResourceResponse(
                myResource.getName(),
                myResource.getType(),
                myResource.getHttpMethod(),
                myResource.getOrderNumber()
        );
    }
}
