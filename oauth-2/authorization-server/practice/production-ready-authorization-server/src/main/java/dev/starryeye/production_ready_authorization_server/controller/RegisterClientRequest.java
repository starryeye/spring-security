package dev.starryeye.production_ready_authorization_server.controller;

import java.util.List;

public record RegisterClientRequest(
        ClientType clientType,
        String clientName,
        String redirectUri, // CONFIDENTIAL, PUBLIC 은 필수. SERVICE 는 redirect 가 없는 grant 라 불필요
        List<String> scopes,
        Long accessTokenTimeToLiveSeconds // 미지정(null) 시 TokenSettings 기본값(300초)
) {
}
