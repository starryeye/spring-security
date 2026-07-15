package dev.starryeye.oauth2_authorization_purge.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /**
     * 만료 row 삭제.. 보유한 모든 토큰(code, access, refresh, id)이 만료된 row 가 대상이다.
     *      토큰 컬럼이 null 인 경우는 "만료" 로 취급하되(해당 토큰이 없는 것이므로),
     *      토큰이 하나도 없는 row(state 만 있는 진행 중 인가)는 대상에서 제외한다. (OAuth2AuthorizationPurgeScheduler 주석 참고)
     */
    @Modifying
    @Query("delete from OAuth2AuthorizationEntity a where" +
            " (a.authorizationCodeExpiresAt is null or a.authorizationCodeExpiresAt < :now)" +
            " and (a.accessTokenExpiresAt is null or a.accessTokenExpiresAt < :now)" +
            " and (a.refreshTokenExpiresAt is null or a.refreshTokenExpiresAt < :now)" +
            " and (a.oidcIdTokenExpiresAt is null or a.oidcIdTokenExpiresAt < :now)" +
            " and not (a.authorizationCodeExpiresAt is null and a.accessTokenExpiresAt is null" +
            " and a.refreshTokenExpiresAt is null and a.oidcIdTokenExpiresAt is null)")
    int deleteAllExpired(@Param("now") Instant now);
}
