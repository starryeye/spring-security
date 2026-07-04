package dev.starryeye.custom_registered_client_repository;

public record RegisterClientResponse(
        String clientId,
        String clientSecret, // raw secret 은 등록 응답에서 한번만 노출된다. (PUBLIC client 는 null)
        String clientName,
        ClientType clientType
) {
}
