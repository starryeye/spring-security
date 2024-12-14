package dev.starryeye.custom_authenticate_mvc_integration_2.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class AsyncService {

    @Async
    public CompletableFuture<Authentication> asyncMethod() {

        /**
         * Global Async 스레드 영역 (@Async)
         */

        log.info("[Async-Service] @Async execution thread : {}, security context : {}",
                Thread.currentThread().getName(),
                SecurityContextHolder.getContext().hashCode()
        );

        // CompletableFuture::completedFuture api 는 비동기로 수행하지 않고
        // 현재 실행 중인 스레드(@Async 작업 스레드)에서 곧바로 완료된 Future를 만들어 넘겨주는 것
        return CompletableFuture.completedFuture(SecurityContextHolder.getContext().getAuthentication());
    }
}
