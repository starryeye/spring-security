package dev.starryeye.hello_jpa_authorization_server;

import java.util.List;

public record RegisterClientRequest(
        String clientName,
        String redirectUri,
        List<String> scopes
) {
}
