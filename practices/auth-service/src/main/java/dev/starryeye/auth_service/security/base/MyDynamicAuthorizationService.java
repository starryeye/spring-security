package dev.starryeye.auth_service.security.base;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;

import java.util.Map;

@RequiredArgsConstructor
public class MyDynamicAuthorizationService {

    private final MyUrlRoleMapper delegate;

    public Map<String, String> getUrlRoleMappings() {
        return delegate.getMappings();
    }

    public AuthorizationDecision getDefaultDecision() {
        return delegate.getDefaultDecision();
    }
}
