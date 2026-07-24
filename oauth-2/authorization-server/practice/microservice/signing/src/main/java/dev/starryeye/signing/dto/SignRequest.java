package dev.starryeye.signing.dto;

import java.util.Map;

public record SignRequest(Map<String, Object> claims) {
}
