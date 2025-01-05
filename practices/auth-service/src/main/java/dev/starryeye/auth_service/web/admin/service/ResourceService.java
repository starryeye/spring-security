package dev.starryeye.auth_service.web.admin.service;

import dev.starryeye.auth_service.domain.MyResource;
import dev.starryeye.auth_service.domain.MyResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ResourceService {

    private final MyResourceRepository myResourceRepository;

    public void createResource(MyResource myResource) {
        myResourceRepository.save(myResource);
    }

    public void deleteResource(Long resourceId) {
        myResourceRepository.deleteById(resourceId);
    }
}
