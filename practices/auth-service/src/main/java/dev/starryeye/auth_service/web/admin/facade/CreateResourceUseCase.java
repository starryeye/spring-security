package dev.starryeye.auth_service.web.admin.facade;

import dev.starryeye.auth_service.web.admin.service.ResourceQueryService;
import dev.starryeye.auth_service.web.admin.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class CreateResourceUseCase {

    private final ResourceQueryService resourceQueryService;
    private final RoleService roleService;

    public void process() {

    }
}
