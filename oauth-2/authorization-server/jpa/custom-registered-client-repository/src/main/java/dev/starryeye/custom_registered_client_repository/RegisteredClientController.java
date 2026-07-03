package dev.starryeye.custom_registered_client_repository;

import dev.starryeye.custom_registered_client_repository.jpa.RegisteredClientEntity;
import dev.starryeye.custom_registered_client_repository.jpa.RegisteredClientEntityRepository;
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
     * DB 에 저장된 client 등록 정보를 관찰하는 controller 이다.
     *
     * "/registered-clients"
     *      RegisteredClient 로 변환된 결과를 조회한다. (comma 문자열/JSON 이 원래 타입으로 복원된 모습)
     * "/registered-clients/raw"
     *      entity 를 그대로 조회한다.
     *      client_settings, token_settings 컬럼에 @class 타입 정보가 포함된 JSON 문자열과..
     *      "{bcrypt}..." 로 인코딩되어 저장된 client_secret 을 눈으로 확인할 수 있다.
     */

    private final RegisteredClientEntityRepository entityRepository;
    private final RegisteredClientRepository registeredClientRepository;

    @GetMapping("/registered-clients")
    public List<RegisteredClient> getRegisteredClients() {
        return entityRepository.findAll().stream()
                .map(entity -> registeredClientRepository.findById(entity.getId()))
                .toList();
    }

    @GetMapping("/registered-clients/raw")
    public List<RegisteredClientEntity> getRegisteredClientEntities() {
        return entityRepository.findAll();
    }
}
