package dev.starryeye.auth_service.security.base;

import org.springframework.security.authorization.AuthorizationDecision;

import java.util.LinkedHashMap;
import java.util.Map;

public class MyMapBasedUrlRoleMapper implements MyUrlRoleMapper {

    /**
     * 일급 컬렉션
     */

    private final Map<String, String> mappings;

    public MyMapBasedUrlRoleMapper() {
        this.mappings = new LinkedHashMap<>();
        initialize();
    }

    private void initialize() {

        this.mappings.put("/css/**", "permitAll");
        this.mappings.put("/js/**", "permitAll");
        this.mappings.put("/images/**", "permitAll");
        this.mappings.put("/favicon/**", "permitAll");
        this.mappings.put("/*/icon-*", "permitAll");

        this.mappings.put("/", "permitAll");
        this.mappings.put("/users/signup", "permitAll");
        this.mappings.put("/login*", "permitAll");
        this.mappings.put("/logout", "permitAll");

        this.mappings.put("/denied", "authenticated");

        this.mappings.put("/user", "ROLE_USER");
        this.mappings.put("/admin/**", "ROLE_ADMIN");
        this.mappings.put("/manager", "ROLE_MANAGER");
        this.mappings.put("/db", "hasRole('DBA')");
    }

    @Override
    public Map<String, String> getMappings() {
        return this.mappings;
    }

    @Override
    public AuthorizationDecision getDefaultDecision() {
        return new AuthorizationDecision(false); // mappings 에 정의되어 있지 않으면 접근 권한을 허용하지 않는다.
    }
}
