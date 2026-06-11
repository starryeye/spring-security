package dev.starryeye.hello_jpa_authorization_server;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RegisteredClientController {

    /**
     * DB 에 저장된 client 등록 정보를 조회해보는 관찰용 controller 이다.
     *      호출 시 show-sql 로 select 쿼리가 찍히는 것을 볼 수 있다.
     *      서버를 재기동하고 호출해도 동일하게 조회된다. (InMemory 와의 차이점)
     */

    private final RegisteredClientRepository registeredClientRepository;

    @GetMapping("/registered-client")
    public RegisteredClient getRegisteredClient() {
        return registeredClientRepository.findByClientId("my-spring-client");
    }
}
