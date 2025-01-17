package dev.starryeye.auth_service.web.admin.facade.usecase.resource;

import dev.starryeye.auth_service.domain.MyResource;
import dev.starryeye.auth_service.web.admin.facade.response.ResourceDetailsResponse;
import dev.starryeye.auth_service.web.admin.facade.response.ResourceResponse;
import dev.starryeye.auth_service.web.admin.facade.response.RoleResponse;
import dev.starryeye.auth_service.web.admin.service.ResourceQueryService;
import dev.starryeye.auth_service.web.admin.service.RoleQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetResourcesUseCase {

    private final ResourceQueryService resourceQueryService;
    private final RoleQueryService roleQueryService;

    public List<ResourceResponse> getResources() {
        return resourceQueryService.getUrlResourcesDesc().stream()
                .map(ResourceResponse::from)
                .toList();
    }

    public ResourceDetailsResponse getResourceBy(Long id) {

        List<RoleResponse> allRoles = roleQueryService.getAllRoles().stream()
                .map(RoleResponse::from)
                .toList();

        MyResource myResource = resourceQueryService.getResourceWithRole(id);

        List<String> roleNamesOfResource = myResource.getRoles().stream()
                .map(roleResource -> roleResource.getMyRole().getName())
                .toList();

        return new ResourceDetailsResponse(
                allRoles,
                roleNamesOfResource,
                ResourceResponse.from(myResource)
        );
    }
}
