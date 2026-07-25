package dev.starryeye.consent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SaveConsentRequest(String sub, String clientId, List<String> scopes) {
}
