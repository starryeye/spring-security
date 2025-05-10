package dev.starryeye.registered_client_repository;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RegisteredClientController {

    /**
     * RegisteredClientRepository 로 등록된 client 정보를 조회 가능하다. (등록도 가능)
     */

    private final RegisteredClientRepository registeredClientRepository;

    @GetMapping("/registered-client")
    public List<RegisteredClient> getRegisteredClients() {
        RegisteredClient registeredClient1 = registeredClientRepository.findByClientId("my-spring-client1");
        RegisteredClient registeredClient2 = registeredClientRepository.findByClientId("my-spring-client2");

        return List.of(registeredClient1, registeredClient2);
    }
}
