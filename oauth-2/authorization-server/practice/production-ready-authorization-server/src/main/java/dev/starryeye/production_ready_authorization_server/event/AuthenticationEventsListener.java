package dev.starryeye.production_ready_authorization_server.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuthenticationEventsListener {

    /**
     * 인증 성공/실패 이벤트를 받아 감사 로그를 남긴다. (etc/authentication-events 프로젝트 이식)
     *      로그인(form login), client 인증, 토큰 발급까지 이벤트로 잡힌다.
     *
     * 다중 인스턴스 관점..
     *      이벤트는 처리한 인스턴스의 JVM 안에서만 발행되므로 감사 로그가 인스턴스별로 흩어진다.
     *      두 인스턴스의 로그를 대조하면 LB 가 각 단계를 어느 인스턴스에 분배했는지 보인다. (main class 확인 포인트 5)
     *      실제 운영이라면 로그 대신 중앙 저장소(감사 테이블, 로그 수집기)로 모으는 지점이다.
     */

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {

        Authentication authentication = event.getAuthentication();

        log.info("[인증 성공] event={}, authentication={}, principal={}",
                event.getClass().getSimpleName(),
                authentication.getClass().getSimpleName(),
                authentication.getName());
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {

        Authentication authentication = event.getAuthentication();

        log.info("[인증 실패] event={}, authentication={}, principal={}, 사유={}",
                event.getClass().getSimpleName(),
                authentication.getClass().getSimpleName(),
                authentication.getName(),
                event.getException().getMessage());
    }
}
