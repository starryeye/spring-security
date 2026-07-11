package dev.starryeye.oauth2_authorization_purge;

import dev.starryeye.oauth2_authorization_purge.jpa.OAuth2AuthorizationEntity;
import dev.starryeye.oauth2_authorization_purge.jpa.OAuth2AuthorizationEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class OAuth2AuthorizationController {

    /**
     * oauth2_authorization row 의 증감을 관찰하는 controller 이다.
     *      grant 수행 -> row 생성, 토큰 만료 + 배치 주기 경과 -> row 삭제를 확인해볼 것.
     */

    private final OAuth2AuthorizationEntityRepository entityRepository;

    @GetMapping("/oauth2-authorizations/raw")
    public List<OAuth2AuthorizationEntity> oauth2AuthorizationEntities() {
        return entityRepository.findAll();
    }
}
