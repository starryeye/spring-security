package dev.starryeye.auth_service.security.base;

import java.util.Map;

public class MyPersistenceUrlRoleMapper implements MyUrlRoleMapper{
    @Override
    public Map<String, String> getMappings() {
        return Map.of();
    }
}
