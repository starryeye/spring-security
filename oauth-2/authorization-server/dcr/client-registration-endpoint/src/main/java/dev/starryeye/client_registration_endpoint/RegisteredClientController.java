package dev.starryeye.client_registration_endpoint;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RegisteredClientController {

    /**
     * 동적으로 등록된 client 가 저장소에 어떤 모습으로 저장되었는지 관찰용 api 이다.
     *      표준 조회(GET "/connect/register")는 응답 필드가 등록 metadata 형식이라..
     *      RegisteredClient 로서의 전체 모습(ClientSettings/TokenSettings 기본값 등)은 이쪽이 잘 보인다.
     */

    private final RegisteredClientRepository registeredClientRepository;

    @GetMapping("/registered-client")
    public RegisteredClient getRegisteredClient(@RequestParam("client_id") String clientId) {
        return registeredClientRepository.findByClientId(clientId);
    }
}
