package dev.starryeye.custom_oauth2_token_customizer;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TokenClaimsController {

    /**
     * 발급된 JWT(access token, id token)의 header 와 claims 를 관찰하는 controller 이다.
     *      access token 에는 authorities claim 이, id token 에는 nickname claim 이 추가된 것을 확인해볼 것.
     */

    private final JwtDecoder jwtDecoder;

    @GetMapping("/token-claims")
    public Map<String, Object> tokenClaims(@RequestParam("token") String token) {

        Jwt jwt = jwtDecoder.decode(token);

        return Map.of(
                "headers", jwt.getHeaders(),
                "claims", jwt.getClaims()
        );
    }
}
