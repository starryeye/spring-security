package dev.starryeye.auth_service.web.admin.facade.usecase.resource;

import dev.starryeye.auth_service.domain.MyResource;
import dev.starryeye.auth_service.domain.MyRole;
import dev.starryeye.auth_service.domain.MyRoleResource;
import dev.starryeye.auth_service.domain.type.MyResourceType;
import dev.starryeye.auth_service.security.base.MyDynamicAuthorizationManager;
import dev.starryeye.auth_service.web.admin.facade.request.ModifyResourceUseCaseRequest;
import dev.starryeye.auth_service.web.admin.service.ResourceQueryService;
import dev.starryeye.auth_service.web.admin.service.RoleQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
@RequiredArgsConstructor
@Transactional
public class ModifyResourceUseCase {

    private final ResourceQueryService resourceQueryService;

    private final RoleQueryService roleQueryService;

    private final MyDynamicAuthorizationManager authorizationManager;

    public void process(ModifyResourceUseCaseRequest request) {

        MyResource resource = resourceQueryService.getResourceWithRole(request.id());

        MyRole newRole = roleQueryService.getRoleByName(request.roleName());
        Set<MyRoleResource> newRoleResource = Set.of(MyRoleResource.create(newRole, resource));

        resource.changeName(request.resourceName());
        resource.changeType(MyResourceType.fromString(request.resourceType()));
        resource.changeHttpMethod(request.httpMethod());
        resource.changeOrderNumber(Integer.parseInt(request.orderNum()));
        resource.changeRoles(newRoleResource);

        authorizationManager.refreshMatcherEntries(); // todo, 따로 빼보기..
    }
}
