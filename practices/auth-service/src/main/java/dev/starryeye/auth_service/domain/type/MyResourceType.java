package dev.starryeye.auth_service.domain.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MyResourceType {

    URL("url"),
    ;

    private final String value;

    public static MyResourceType fromString(String resourceType) {

        for (MyResourceType myResourceType : MyResourceType.values()) {
            if (myResourceType.name().equalsIgnoreCase(resourceType)) {
                return myResourceType;
            }
        }

        throw new IllegalArgumentException("해당 리소스 타입이 존재하지 않습니다: " + resourceType);
    }
}
