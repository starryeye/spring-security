package dev.starryeye.auth_service.web.admin.facade.usecase.resource;

import dev.starryeye.auth_service.web.admin.service.ResourceQueryService;
import dev.starryeye.auth_service.web.admin.service.RoleQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class CreateResourceUseCase {

    private final ResourceQueryService resourceQueryService;
    private final RoleQueryService roleQueryService;

    public void process() {

    }
}
