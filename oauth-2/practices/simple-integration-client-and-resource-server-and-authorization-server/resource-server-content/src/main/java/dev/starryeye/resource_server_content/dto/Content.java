package dev.starryeye.resource_server_content.dto;

public record Content(
        Long id,
        String title,
        String details,

        String owner
) {
}
