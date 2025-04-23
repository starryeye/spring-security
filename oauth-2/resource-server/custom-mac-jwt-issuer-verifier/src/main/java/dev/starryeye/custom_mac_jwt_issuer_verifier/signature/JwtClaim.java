package dev.starryeye.custom_mac_jwt_issuer_verifier.signature;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum JwtClaim {

    CLAIM_USERNAME("username"),
    CLAIM_AUTHORITIES("authorities"),
    ;

    private final String claimName;
}
