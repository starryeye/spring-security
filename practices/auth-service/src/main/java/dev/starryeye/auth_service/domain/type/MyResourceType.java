package dev.starryeye.auth_service.domain.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MyResourceType {

    URL("url"),
    ;

    private final String value;
}
