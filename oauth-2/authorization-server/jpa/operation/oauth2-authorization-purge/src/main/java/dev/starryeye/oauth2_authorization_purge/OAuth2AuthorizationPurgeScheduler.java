package dev.starryeye.oauth2_authorization_purge;

import dev.starryeye.oauth2_authorization_purge.jpa.OAuth2AuthorizationEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthorizationPurgeScheduler {

    /**
     * 만료된 OAuth2Authorization row 를 주기적으로 삭제하는 배치이다.
     *
     * 삭제 기준.. "보유한 모든 토큰이 만료된 row"
     *      grant 한 건의 row 는 여러 토큰(code, access, refresh, id)을 가지며 각각 만료 시각이 다르다.
     *      가장 늦게 만료되는 토큰까지 지나야 그 row 를 참조할 수 있는 요청(refresh, introspect 등)이 없어진다.
     *
     * id token 도 기준에 포함해야 하는 이유..
     *      OIDC RP-Initiated Logout 에서 id_token_hint 로 저장소를 조회한다. (oidc/custom-logout-endpoint 프로젝트 참고)
     *      주의. id token 의 만료는 TokenSettings 로 조정할 수 없고 JwtGenerator 에 30분으로 고정되어 있다.
     *          -> openid scope 를 포함한 grant row 는 refresh 를 짧게 줄여도 최소 30분 유지된다. (main class 관찰 결과 참고)
     *
     * 대상에서 제외한 것.. state 만 있는 row (토큰 만료 컬럼이 전부 null)
     *      consent 진행 중(로그인/동의 대기)인 인가 상태라서 지우면 진행 중인 flow 가 끊긴다.
     *      다만 사용자가 이탈하면 이 row 는 영영 안 지워진다..
     *      todo, 엔티티에 생성 시각 컬럼을 추가하여 오래된 미완료 인가도 정리하도록 확장할 것
     *
     * 참고.
     * 배치 주기(my.purge.fixed-delay)는 관찰을 위해 20초로 설정했다.
     *      운영에서는 만료 row 가 남아있어도 기능상 문제는 없고(조회 시 만료 검증으로 거부됨) 용량/성능 문제이므로 수 시간~일 단위면 충분하다.
     */

    private final OAuth2AuthorizationEntityRepository repository;

    @Scheduled(fixedDelayString = "${my.purge.fixed-delay}")
    @Transactional
    public void purgeExpiredAuthorizations() {

        int deleted = repository.deleteAllExpired(Instant.now());
        long remaining = repository.count();

        log.info("[purge] 만료 row {}건 삭제, 남은 row {}건", deleted, remaining);
    }
}
