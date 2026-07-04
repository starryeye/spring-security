package dev.starryeye.hello_jpa_authorization_server;

public record RegisterClientResponse(
        String clientId,
        String clientSecret, // raw secret 은 등록 응답에서 한번만 노출된다.
        String clientName
) {
}
