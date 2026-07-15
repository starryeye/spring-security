package dev.starryeye.authentication_events;

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
     * 인증 성공/실패 이벤트를 받아 감사 로그를 남긴다. (발행 구조는 main class 주석 참고)
     *
     * 실제 운영이라면 로그 대신..
     *      감사 테이블 적재, 실패 횟수 카운트(brute force 잠금), 알림 발송 등으로 확장하는 지점이다.
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
