package dev.starryeye.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConsentInfo(String sub, String clientId, List<String> scopes) {
}
