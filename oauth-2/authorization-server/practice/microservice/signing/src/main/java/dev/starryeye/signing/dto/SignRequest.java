package dev.starryeye.signing.dto;

import java.util.Map;

public record SignRequest(Map<String, Object> claims, Map<String, Object> header) {
}
