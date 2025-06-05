package dev.starryeye.client_server.dto;

public record Content(
        Long id,
        String title,
        String details,

        String owner
) {
}
