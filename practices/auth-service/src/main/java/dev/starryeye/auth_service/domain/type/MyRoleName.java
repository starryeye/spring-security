package dev.starryeye.auth_service.domain.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MyRoleName {

    ROLE_USER("회원"),
    ROLE_ADMIN("관리자"),
    ROLE_MANAGER("매니저"),
    ;

    private final String roleName;
}
