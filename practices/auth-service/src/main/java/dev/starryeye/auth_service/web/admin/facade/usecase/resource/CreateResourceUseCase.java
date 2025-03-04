package dev.starryeye.auth_service.web.admin.facade.usecase.resource;

import dev.starryeye.auth_service.domain.MyResource;
import dev.starryeye.auth_service.domain.MyRole;
import dev.starryeye.auth_service.domain.MyRoleResource;
import dev.starryeye.auth_service.domain.type.MyResourceType;
import dev.starryeye.auth_service.security.base.MyDynamicAuthorizationManager;
import dev.starryeye.auth_service.web.admin.facade.request.CreateResourceUseCaseRequest;
import dev.starryeye.auth_service.web.admin.service.ResourceRoleService;
import dev.starryeye.auth_service.web.admin.service.ResourceService;
import dev.starryeye.auth_service.web.admin.service.RoleQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class CreateResourceUseCase {

    private final ResourceService resourceService;
    private final ResourceRoleService resourceRoleService;
    private final RoleQueryService roleQueryService;

    private final MyDynamicAuthorizationManager authorizationManager;

    public void process(CreateResourceUseCaseRequest request) {

        /**
         * TODO
         *  1. Role 을 찾아서 있는 경우에만 유저를 등록한다.(없으면 에러처리)
         *  2. MyRole entity 가 MyResource(aggregate root) 의 aggregate 에 속하므로..
         *      Role 을 찾는 과정을 MyResource.createUser 로 묶으면 좋을 것 같은데..
         *  3. cascade 를 이용해보자..
         */

        String resourceRoleName = request.roleName();
        MyRole role = roleQueryService.getRoleByName(resourceRoleName);

        MyResource resource = MyResource.create(
                request.resourceName(),
                MyResourceType.fromString(request.resourceType()),
                request.httpMethod(),
                request.orderNum()
        );
        resourceService.createResource(resource);

        resourceRoleService.createResourceWithRole(
                MyRoleResource.create(
                        role,
                        resource
                )
        );

        authorizationManager.refreshMatcherEntries(); //todo .. 따로 빼보기..
    }
}
