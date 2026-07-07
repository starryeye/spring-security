package dev.starryeye.custom_oauth2_authorization_service;

import dev.starryeye.custom_oauth2_authorization_service.jpa.OAuth2AuthorizationEntity;
import dev.starryeye.custom_oauth2_authorization_service.jpa.OAuth2AuthorizationEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class OAuth2AuthorizationController {

    /**
     * DB 에 저장된 OAuth2Authorization 을 관찰하는 controller 이다.
     *
     * "/oauth2-authorization?token={access token}"
     *      access token 값으로 findByToken 조회.. entity 에서 OAuth2Authorization 으로 복원된 모습 (oauth2-authorization-service 프로젝트와 동일한 관찰)
     * "/oauth2-authorizations/raw"
     *      entity 원문 조회..
     *      attributes 컬럼에 principal 의 Authentication 과 OAuth2AuthorizationRequest 가 @class 타입 정보와 함께 JSON 직렬화된 것과..
     *      code 사용 후 authorization_code_metadata 의 invalidated 가 true 로 갱신된 것을 볼 수 있다.
     */

    private final OAuth2AuthorizationService oAuth2AuthorizationService;
    private final OAuth2AuthorizationEntityRepository entityRepository;

    @GetMapping("/oauth2-authorization")
    public OAuth2Authorization oauth2Authorization(@RequestParam("token") String token) {
        return oAuth2AuthorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
    }

    @GetMapping("/oauth2-authorizations/raw")
    public List<OAuth2AuthorizationEntity> oauth2AuthorizationEntities() {
        return entityRepository.findAll();
    }
}
