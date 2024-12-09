package dev.starryeye.custom_authorize_authorization_event.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.authorization.event.AuthorizationEvent;
import org.springframework.security.authorization.event.AuthorizationGrantedEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuthorizationEventListener {

    @EventListener
    public void onAuthorizationEvent(AuthorizationEvent event) {
        // 인가 최상위 이벤트
        log.info("Authorization event received: {}", event);
    }

    @EventListener
    public void onAuthorizationDeniedEvent(AuthorizationDeniedEvent event) {
        // 인가 거부 이벤트
        log.info("Authorization denied event received: {}", event);
    }

    @EventListener
    public void onAuthorizationGrantedEvent(AuthorizationGrantedEvent event) {
        // 인가 허용 이벤트
        log.info("Authorization granted event received: {}", event);
    }
}
