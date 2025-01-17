package dev.starryeye.auth_service.security.base;

import org.springframework.security.authorization.AuthorizationDecision;

import java.util.Map;

public interface MyUrlRoleMapper {

    Map<String, String> getMappings();

    AuthorizationDecision getDefaultDecision();
}
