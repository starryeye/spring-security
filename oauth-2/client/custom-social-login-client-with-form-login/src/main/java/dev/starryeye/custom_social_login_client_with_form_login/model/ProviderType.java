package dev.starryeye.custom_social_login_client_with_form_login.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProviderType {

    GOOGLE("my-google"),
    NAVER("my-naver"),
    KAKAO("my-kakao"),
    KEYCLOAK("my-keycloak")
    ;

    private final String providerId; // todo, application.yml 과 통합 필요
}
