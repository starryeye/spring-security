package dev.starryeye.custom_opaque_token_introspector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class HelloController {

    @GetMapping("/")
    public Authentication hello(Authentication authentication) {
        return authentication;
    }

    @GetMapping("/authentication-principal")
    public Authentication authenticationPrincipal(Authentication authentication, @AuthenticationPrincipal OAuth2IntrospectionAuthenticatedPrincipal principal) {

        // opaqueToken() 설정에서는 최종 인증객체 타입이 BearerTokenAuthentication 이다.
        BearerTokenAuthentication bearerTokenAuthentication = (BearerTokenAuthentication) authentication;
        log.info("token active = {}", bearerTokenAuthentication.getTokenAttributes().get("active"));

        // opaqueToken() 설정에서는 인증객체의 principal 에 저장 되는 타입은 OAuth2IntrospectionAuthenticatedPrincipal 이다.
        log.info("principal = {}", principal);

        return bearerTokenAuthentication;
    }
}
