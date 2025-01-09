package dev.starryeye.auth_service.web.admin.facade.usecase.role;

import dev.starryeye.auth_service.web.admin.facade.response.RoleResponse;
import org.springframework.stereotype.Component;

@Component
public class PrepareRoleRegisterUseCase {

    public RoleResponse process() {
        return RoleResponse.empty();
    }
}
