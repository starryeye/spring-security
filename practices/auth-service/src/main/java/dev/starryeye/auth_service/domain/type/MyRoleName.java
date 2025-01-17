package dev.starryeye.auth_service.domain.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MyRoleName {

    /**
     * RoleName 은 동적으로 수정 및 생성, 삭제가 가능해야하므로 사용하지 못함.
     * -> String 으로 변경
     */

    ROLE_USER("회원"),
    ROLE_ADMIN("관리자"),
    ROLE_MANAGER("매니저"),
    ;

    private final String roleName;

    public static MyRoleName fromString(String roleName) {

        for (MyRoleName myRoleName : MyRoleName.values()) {
            if (myRoleName.name().equalsIgnoreCase(roleName)) {
                return myRoleName;
            }
        }

        throw new IllegalArgumentException("해당 권한이 존재하지 않습니다: " + roleName);
    }
}
