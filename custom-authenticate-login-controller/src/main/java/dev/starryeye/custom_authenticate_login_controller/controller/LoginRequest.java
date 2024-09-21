package dev.starryeye.custom_authenticate_login_controller.controller;

public record LoginRequest(
        String username,
        String password
) {
}
