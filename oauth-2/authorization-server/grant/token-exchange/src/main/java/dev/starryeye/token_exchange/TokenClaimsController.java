package dev.starryeye.token_exchange;

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
     * 발급된 JWT 의 claim 관찰용 api 이다. (custom-oauth2-token-customizer 프로젝트의 관찰용 컨트롤러와 같은 패턴)
     *      교환 전/후 토큰의 sub, aud, scope, act claim 을 비교하는 데 쓴다.
     */

    private final JwtDecoder jwtDecoder;

    @GetMapping("/token-claims")
    public Map<String, Object> tokenClaims(@RequestParam("token") String token) {
        Jwt jwt = jwtDecoder.decode(token);
        return jwt.getClaims();
    }
}
