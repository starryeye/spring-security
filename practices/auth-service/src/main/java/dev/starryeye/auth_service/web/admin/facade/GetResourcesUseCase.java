package dev.starryeye.auth_service.web.admin.facade;

import dev.starryeye.auth_service.web.admin.facade.response.ResourceResponse;
import dev.starryeye.auth_service.web.admin.service.ResourceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetResourcesUseCase {

    private final ResourceQueryService resourceQueryService;

    public ResourceResponse getResourceBy(Long id) {
        return ResourceResponse.of(resourceQueryService.getResource(id));
    }

    public List<ResourceResponse> getResources() {
        return resourceQueryService.getUrlResourcesDesc().stream()
                .map(ResourceResponse::of)
                .toList();
    }
}
