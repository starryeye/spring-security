package dev.starryeye.authentication_and_principal;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/authentication")
    public Authentication authentication(Authentication authentication) {

        /**
         * oauth2-resource-server 의 oauth2ResourceServer() 설정을 이용할 경우..
         * client 가 요청에 Authorization 헤더로 JWT 를 넣어주면..
         * filter 에서 생성하는 인증 객체는 JwtAuthenticationToken 타입이다.
         */
        JwtAuthenticationToken authenticationToken = (JwtAuthenticationToken) authentication;

        // getTokenAttributes 는 JWT 의 claim 정보이다.
        Map<String, Object> tokenAttributes = authenticationToken.getTokenAttributes();
        String sub = (String) tokenAttributes.get("sub");
        String scope = (String) tokenAttributes.get("scope");


        return authenticationToken;
    }

    @GetMapping("/authentication-principal")
    public Jwt authenticationPrincipal(@AuthenticationPrincipal Jwt jwt) {

        /**
         * 인증 객체에 적재되는 principal 은 Jwt 타입이다.
         */

        // claim 을 접근할 때 해당 필드 값의 타입을 미리 선택 가능
        String sub = jwt.getClaimAsString("sub");

        // client 가 넘긴 실제 token 값, 다른 resource server 에 접근시 활용 가능
        String tokenValue = jwt.getTokenValue();

        return jwt;
    }
}
