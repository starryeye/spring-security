package dev.starryeye.hello_jpa_authorization_server;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RegisteredClientController {

    /**
     * client 사전 등록(pre-registration)용 admin API 이다.
     *
     * authorization server 에 client 를 등록하는 기능은 프레임워크가 제공하지 않는다.
     *      관리자가 client 를 사전 등록하는 기능(client_id, client_secret 생성 -> RegisteredClientRepository::save)은
     *      아래처럼 개발자가 직접 만들어야 한다. (여기서는 학습용이라 관리자 인증/인가는 생략함)
     *      참고. 이는 의도된 설계이다.. spring authorization server 는 제품(Keycloak 처럼 admin console 포함)이 아니라
     *          제품을 만들기 위한 framework 라는 것이 공식 입장이며, 첫 client 등록은 out-of-band 로 하라고 안내한다. (gh-1037)
     *
     * 프레임워크가 save 를 호출하는 경우는 두가지 뿐이다.
     *      1. ClientSecretAuthenticationProvider 에서 보면..
     *          client 인증 성공 직후 PasswordEncoder::upgradeEncoding 이 true 면 secret 을 재인코딩하고 save 를 호출한다.
     *              DelegatingPasswordEncoder::upgradeEncoding 이 true 인 조건..
     *                  저장된 값의 prefix id 가 현재 encode 기본 방식(bcrypt)과 다르거나, 같아도 해당 encoder 기준으로 강도가 낮을 때(bcrypt cost 등)
     *              재인코딩 시점이 인증 성공 직후인 이유.. 해시는 역산이 안되므로 서버가 raw secret 을 아는 유일한 순간이기 때문
     *          -> 등록이 아니라 이미 등록된 client 의 secret 을 프레임워크가 갱신하는 경우이다.
     *      2. OIDC Dynamic Client Registration 기능(기본 비활성)을 켜면..
     *          client 가 "/connect/register" 로 스스로를 런타임에 등록하는 표준 기능으로..
     *          OidcClientRegistrationAuthenticationProvider::registerClient 에서 요청받은 client 메타데이터를 save 한다.
     *
     * 등록 API 설계..
     *      client_id, client_secret 은 서버가 생성하고, raw secret 은 등록 응답에서 한번만 노출한다.
     *      단, 저장은 일부러 "{noop}" 으로 한다..
     *          위 1번(secret 인코딩 upgrade)을 관찰하기 위함이다.
     *          등록한 client 로 인증을 한번 수행하고 "/registered-client/{clientId}" 로 다시 조회하면 "{bcrypt}.." 로 바뀌어 있는 것을 볼 수 있다.
     *          운영에서는 upgrade 에 기대지 말고 처음부터 인코딩해서 저장해야 한다. (custom-registered-client-repository 프로젝트 참고)
     */

    private final RegisteredClientRepository registeredClientRepository;

    @PostMapping("/registered-client")
    public RegisterClientResponse register(@RequestBody RegisterClientRequest request) {

        String clientId = UUID.randomUUID().toString();
        String clientSecret = UUID.randomUUID().toString();

        RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret("{noop}" + clientSecret) // secret 인코딩 upgrade 관찰용, 운영에서는 인코딩해서 저장할 것 (위 주석 참고)
                .clientIdIssuedAt(Instant.now())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientName(request.clientName())
                .redirectUri(request.redirectUri())
                .scopes(scopes -> scopes.addAll(request.scopes()))
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
                .build();

        registeredClientRepository.save(registeredClient);

        return new RegisterClientResponse(clientId, clientSecret, request.clientName());
    }

    // DB 에 저장된 client 등록 정보 조회 (관찰용, secret 이 어떤 형태로 저장되어 있는지 확인 가능)
    @GetMapping("/registered-client/{clientId}")
    public RegisteredClient getRegisteredClient(@PathVariable String clientId) {
        return registeredClientRepository.findByClientId(clientId);
    }
}
