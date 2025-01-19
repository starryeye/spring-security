package dev.starryeye.auth_service.security.base;

import dev.starryeye.auth_service.domain.MyResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@RequiredArgsConstructor
public class MyPersistenceUrlRoleMapper implements MyUrlRoleMapper {

    private final MyResourceRepository myResourceRepository;

    @Transactional(readOnly = true)
    @Override
    public Map<String, String> getMappings() {
        /**
         * MyDynamicAuthorizationManager 의 생성자에서 최초 1회만 호출하므로..
         * 권한 매핑 정보가 변경되면 껏다켜야함..
         */

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
