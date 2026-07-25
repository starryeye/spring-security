package dev.starryeye.consent.dto;

import java.util.List;

public record ConsentResponse(String sub, String clientId, List<String> scopes) {
}
