package dev.starryeye.auth_service.web.admin.facade.usecase.resource;

import dev.starryeye.auth_service.web.admin.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class DeleteResourceUseCase {

    private final ResourceService resourceService;

    public void process(Long resourceId) {
        resourceService.deleteResource(resourceId);
    }
}
