package dev.starryeye.custom_multi_auth_ex.security.service;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ApiKeyService {

    private final Map<String, String> apiKeyDeveloperMap = Map.of(
            "keyofadmin", "admin1"
    );

    public String findUsernameByApiKey(String apiKey) {
        return apiKeyDeveloperMap.get(apiKey);
    }
}
