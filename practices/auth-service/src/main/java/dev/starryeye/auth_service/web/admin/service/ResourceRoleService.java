package dev.starryeye.auth_service.web.admin.service;

import dev.starryeye.auth_service.domain.MyRoleResource;
import dev.starryeye.auth_service.domain.MyRoleResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ResourceRoleService {

    private final MyRoleResourceRepository myRoleResourceRepository;

    public void createResourceWithRole(MyRoleResource myRoleResource) {
        myRoleResourceRepository.save(myRoleResource);
    }
}
