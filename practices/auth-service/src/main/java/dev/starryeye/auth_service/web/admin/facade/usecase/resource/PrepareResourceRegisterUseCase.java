package dev.starryeye.auth_service.web.admin.facade.usecase.resource;

import dev.starryeye.auth_service.web.admin.facade.response.ResourceDetailsResponse;
import dev.starryeye.auth_service.web.admin.facade.response.ResourceResponse;
import dev.starryeye.auth_service.web.admin.facade.response.RoleResponse;
import dev.starryeye.auth_service.web.admin.service.RoleQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PrepareResourceRegisterUseCase {

    private final RoleQueryService roleQueryService;

    public ResourceDetailsResponse process() {

        List<RoleResponse> allRoles = roleQueryService.getAllRoles().stream()
                .map(RoleResponse::from)
                .toList();

        return new ResourceDetailsResponse(
                allRoles,
                new ArrayList<>(),
                ResourceResponse.empty()
        );
    }
}
