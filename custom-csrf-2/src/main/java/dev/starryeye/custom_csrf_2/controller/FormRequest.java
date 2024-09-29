package dev.starryeye.custom_csrf_2.controller;

import org.springframework.security.web.csrf.CsrfToken;

public record FormRequest(
        CsrfToken csrfToken,
        String username,
        String password
) {
}
