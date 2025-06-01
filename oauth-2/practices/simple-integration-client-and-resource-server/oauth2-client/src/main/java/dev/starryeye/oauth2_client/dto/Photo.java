package dev.starryeye.oauth2_client.dto;

public record Photo(
        Long id,
        String title,
        String description,
        Long userId
) {
}
