package dev.starryeye.production_ready_authorization_server.controller;

import java.util.List;

public record RegisterClientRequest(
        ClientType clientType,
        String clientName,
        String redirectUri, // CONFIDENTIAL, PUBLIC 은 필수. SERVICE 는 redirect 가 없는 grant 라 불필요
        List<String> scopes,
        Long accessTokenTimeToLiveSeconds, // 미지정(null) 시 TokenSettings 기본값(300초)
        Boolean requireAuthorizationConsent // 미지정(null) 시 true.. 자사(1st-party) client 는 동의 화면을 생략(false)하는 것이 일반적이다
) {

    public boolean requireAuthorizationConsentOrDefault() {
        return requireAuthorizationConsent == null || requireAuthorizationConsent;
    }
}
