package dev.starryeye.oauth2_resource_server.dto;

public record Photo(
        Long id,
        String title,
        String description,
        Long userId
) {
}
