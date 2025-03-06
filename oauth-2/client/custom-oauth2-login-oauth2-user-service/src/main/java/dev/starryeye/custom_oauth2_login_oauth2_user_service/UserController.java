package dev.starryeye.custom_oauth2_login_oauth2_user_service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final ClientRegistrationRepository clientRegistrationRepository;

    /**
     * 해당 실습은 client 의 인증 처리 flow 를 이해하기 위한 controller 로 ..
     *      원래 OAuth2LoginAuthenticationProvider, OidcAuthorizationCodeAuthenticationProvider 의 일부 로직으로 제공된다.
     *      참고, OAuth2User 를 반환하고 있는데 원래 OAuth2User 로 인증 후속 처리가 이루어 져야한다. (session 저장 등)
     * 또한, access token, id token 을 발급 받고 해당 controller 를 호출 하도록 하자.
     *
     *
     *
     * access token, userinfo 로 인증 처리하는 방식을 이해
     * -> "/user"
     *
     * id token 으로 인증 처리하는 방식을 이해
     * -> "/oidc"
     */

    @PostMapping("/user")
    public OAuth2User user(@RequestBody UserRequest userRequest) {

        ClientRegistration clientRegistration = this.clientRegistrationRepository.findByRegistrationId("my-keycloak");
        OAuth2AccessToken oAuth2AccessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, userRequest.getAccessToken(), Instant.now(), Instant.MAX);

        OAuth2UserRequest oAuth2UserRequest = new OAuth2UserRequest(clientRegistration, oAuth2AccessToken);
        DefaultOAuth2UserService defaultOAuth2UserService = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = defaultOAuth2UserService.loadUser(oAuth2UserRequest);

        return oAuth2User;
    }

    @PostMapping("/oidc")
    public OAuth2User oidc(@RequestBody OidcRequest oidcRequest) {

        ClientRegistration clientRegistration = this.clientRegistrationRepository.findByRegistrationId("my-keycloak");
        OAuth2AccessToken oAuth2AccessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, oidcRequest.getAccessToken(), Instant.now(), Instant.MAX, Set.of("openid"));
        Map<String, Object> idTokenClaims = Map.of( // 원래는 인가서버로 부터 받는 정보이다.
                IdTokenClaimNames.ISS, "http://localhost:8090/realms/custom-realm",
                IdTokenClaimNames.SUB, "OIDC", // 사용자의 이름에 해당, 표준은 sub 이나 keycloak 은 preferred_username 이다.
                "preferred_username", "user"
        );
        OidcIdToken oidcIdToken = new OidcIdToken(oidcRequest.getIdToken(), Instant.now(), Instant.MAX, idTokenClaims);

        OidcUserRequest oidcUserRequest = new OidcUserRequest(clientRegistration, oAuth2AccessToken, oidcIdToken);
        OidcUserService oidcUserService = new OidcUserService();
        OAuth2User oAuth2User = oidcUserService.loadUser(oidcUserRequest);

        return oAuth2User;
    }

    @Getter
    public static class UserRequest {
        private final String accessToken;

        public UserRequest(String accessToken) {
            this.accessToken = accessToken;
        }
    }

    @Getter
    public static class OidcRequest {
        private final String accessToken;
        private final String idToken;

        public OidcRequest(String accessToken, String idToken) {
            this.accessToken = accessToken;
            this.idToken = idToken;
        }
    }
}
