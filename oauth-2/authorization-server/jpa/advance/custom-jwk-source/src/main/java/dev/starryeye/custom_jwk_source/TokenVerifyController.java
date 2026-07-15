package dev.starryeye.custom_jwk_source;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TokenVerifyController {

    /**
     * 발급된 JWT(access token) 의 서명을 검증해보는 관찰용 controller 이다.
     *      JwtDecoder 는 jwkSource 기반이므로 리소스 서버가 JWKS 로 검증하는 것과 같은 검증이다.
     *
     * 핵심 확인 포인트..
     *      서버를 재기동한 뒤, 재기동 전에 발급받은 access token 으로 호출해도 검증을 통과한다.
     *      기존 프로젝트들(부팅 시 RSA 생성)이었다면 키가 바뀌어 서명 검증에 실패했을 시나리오이다.
     */

    private final JwtDecoder jwtDecoder;

    @GetMapping("/verify-token")
    public Map<String, Object> verifyToken(@RequestParam("token") String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            return Map.of(
                    "valid", true,
                    "kid", String.valueOf(jwt.getHeaders().get("kid")), // 어떤 키로 서명되었는지
                    "sub", String.valueOf(jwt.getSubject()),
                    "expiresAt", String.valueOf(jwt.getExpiresAt())
            );
        } catch (JwtException e) {
            return Map.of(
                    "valid", false,
                    "reason", e.getMessage()
            );
        }
    }
}
