package dev.starryeye.custom_oauth2_authorization_service.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OAuth2AuthorizationEntityRepository extends JpaRepository<OAuth2AuthorizationEntity, String> {

    Optional<OAuth2AuthorizationEntity> findByState(String state);

    Optional<OAuth2AuthorizationEntity> findByAuthorizationCodeValue(String authorizationCode);

    Optional<OAuth2AuthorizationEntity> findByAccessTokenValue(String accessToken);

    Optional<OAuth2AuthorizationEntity> findByRefreshTokenValue(String refreshToken);

    Optional<OAuth2AuthorizationEntity> findByOidcIdTokenValue(String idToken);

    // token type 을 특정하지 않고 조회하는 경우 (OAuth2AuthorizationService::findByToken 의 tokenType null 대응)
    @Query("select a from OAuth2AuthorizationEntity a where a.state = :token" +
            " or a.authorizationCodeValue = :token" +
            " or a.accessTokenValue = :token" +
            " or a.refreshTokenValue = :token" +
            " or a.oidcIdTokenValue = :token")
    Optional<OAuth2AuthorizationEntity> findByAnyTokenValue(@Param("token") String token);
}
