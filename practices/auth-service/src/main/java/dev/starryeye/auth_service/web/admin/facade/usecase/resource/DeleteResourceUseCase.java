package dev.starryeye.auth_service.web.admin.facade.usecase.resource;

import dev.starryeye.auth_service.security.base.MyDynamicAuthorizationManager;
import dev.starryeye.auth_service.web.admin.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class DeleteResourceUseCase {

    private final ResourceService resourceService;

    private final MyDynamicAuthorizationManager authorizationManager;

    public void by(Long resourceId) {
        resourceService.deleteResource(resourceId);

        authorizationManager.refreshMatcherEntries(); // todo, 따로빼보기..
    }
}
