package dev.starryeye.auth_service.web.admin.service;

import dev.starryeye.auth_service.domain.MyResource;
import dev.starryeye.auth_service.domain.MyResourceRepository;
import dev.starryeye.auth_service.domain.type.MyResourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ResourceQueryService {

    private final MyResourceRepository myResourceRepository;

    public MyResource getResourceWithRole(Long resourceId) {

        return myResourceRepository.findOneWithRolesById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found with id " + resourceId));
    }

    public List<MyResource> getUrlResourcesAsc() {

        MyResourceType type = MyResourceType.URL;
        Sort sort = Sort.by(Sort.Direction.ASC, "orderNumber");
        return myResourceRepository.findAllByType(type, sort);
    }
}
