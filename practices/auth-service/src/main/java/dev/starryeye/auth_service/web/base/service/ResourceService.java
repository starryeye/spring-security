package dev.starryeye.auth_service.web.base.service;

import dev.starryeye.auth_service.domain.MyResource;
import dev.starryeye.auth_service.domain.MyResourceRepository;
import dev.starryeye.auth_service.domain.type.MyResourceType;
import dev.starryeye.auth_service.web.base.service.response.ResourceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final MyResourceRepository myResourceRepository;

    @Transactional(readOnly = true)
    public ResourceResponse getResource(Long resourceId) {

        MyResource myResource = myResourceRepository.findById(resourceId)
                .orElseGet(() -> MyResource.builder().build());

        return ResourceResponse.of(myResource);
    }

    @Transactional(readOnly = true)
    public List<ResourceResponse> getResources() {

        MyResourceType type = MyResourceType.URL;
        Sort sort = Sort.by(Sort.Direction.DESC, "orderNumber");
        List<MyResource> myResources = myResourceRepository.findResourcesByType(type, sort);

        return myResources.stream()
                .map(ResourceResponse::of)
                .toList();
    }

    @Transactional
    public void createResource() {

    }

    @Transactional
    public void deleteResource(Long resourceId) {
        myResourceRepository.deleteById(resourceId);
    }
}
