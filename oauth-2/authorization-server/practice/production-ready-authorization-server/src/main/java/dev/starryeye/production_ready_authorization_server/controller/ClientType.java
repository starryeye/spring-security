package dev.starryeye.production_ready_authorization_server.controller;

public enum ClientType {

    CONFIDENTIAL, // secret 보유, authorization code + refresh token + client credentials
    PUBLIC,       // secret 없음, authorization code + PKCE 필수 (requireProofKey)
    SERVICE       // secret 보유, client credentials 전용 (server to server, resource server 등)
}
