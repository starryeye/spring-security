package dev.starryeye.auth_service.security.base;

import dev.starryeye.auth_service.domain.MyResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;

import java.util.LinkedHashMap;
import java.util.Map;

@RequiredArgsConstructor
public class MyPersistenceUrlRoleMapper implements MyUrlRoleMapper {

    private final MyResourceRepository myResourceRepository;

    // todo, @Cacheable
    @Override
    public Map<String, String> getMappings() {

        Map<String, String> mappings = new LinkedHashMap<>();

        myResourceRepository.findAllWithRoles().forEach(
                myResource -> myResource.getRoles().forEach(
                        myRoleResource -> mappings.put(myResource.getName(), myRoleResource.getMyRole().getName())
                )
        );

        return mappings;
    }

    @Override
    public AuthorizationDecision getDefaultDecision() {
        return new AuthorizationDecision(true); // mappings 에 정의되어 있지 않으면 접근 권한을 허용한다.
    }
}
