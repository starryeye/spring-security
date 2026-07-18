package dev.starryeye.production_ready_authorization_server.controller;

public enum ClientType {

    CONFIDENTIAL, // secret 보유, authorization code + refresh token + client credentials
    PUBLIC,       // secret 없음, authorization code + PKCE 필수 (requireProofKey)
    SERVICE,      // secret 보유, client credentials 전용 (server to server, resource server 등)
    EXCHANGE      // secret 보유, token exchange + client credentials.. 다른 서버를 대신 호출하는 서버(resource server 가 client 로 나서는 신원)용
}
