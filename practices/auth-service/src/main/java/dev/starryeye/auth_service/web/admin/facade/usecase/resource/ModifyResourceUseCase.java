package dev.starryeye.auth_service.web.admin.facade.usecase.resource;

import dev.starryeye.auth_service.domain.MyResource;
import dev.starryeye.auth_service.web.admin.facade.request.ModifyResourceUseCaseRequest;
import dev.starryeye.auth_service.web.admin.service.ResourceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ModifyResourceUseCase {

    private final ResourceQueryService resourceQueryService;

    public void process(ModifyResourceUseCaseRequest request) {

        MyResource resource = resourceQueryService.getResourceWithRole(request.id());


    }
}
