package dev.starryeye.auth_service.web.admin.facade;

import dev.starryeye.auth_service.web.admin.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeleteResourceUseCase {

    private final ResourceService resourceService;

    public void process(Long resourceId) {
        resourceService.deleteResource(resourceId);
    }
}
