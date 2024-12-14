package dev.starryeye.custom_authenticate_mvc_integration_2.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CompletableFutureController {

    private final Executor tempExecutor;

    @GetMapping("/return-completable-future")
    public CompletableFuture<Authentication> returnCompletableFuture(
            @CurrentSecurityContext SecurityContext securityContext
    ) {

        /**
         * CompletableFuture 로 supplyAsync 등을 이용해 별도의 스레드로 수행하도록 하면..
         *      CallableController 처럼 ThreadLocal SecurityContext 가 연동 되지 않음에 주의하자.
         */

        log.info("[CompletableFuture] request thread : {}, security context : {}",
                Thread.currentThread().getName(),
                securityContext.hashCode()
        );

        return CompletableFuture.supplyAsync(() -> {
                    /**
                     * 별도의 커스텀 TaskExecutor 영역 (tempExecutor)
                     */
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    log.info("[CompletableFuture-supplyAsync] CompletableFuture execution thread : {}, security context : {}",
                            Thread.currentThread().getName(),
                            SecurityContextHolder.getContext().hashCode()
                    );

                    return SecurityContextHolder.getContext().getAuthentication();
                }, tempExecutor) // 별도 커스텀 executor 로 수행
                .thenApply(completableFutureSecurityContext -> {

                    /**
                     * thenApply 이므로 위에서 작업한 스레드가 수행한다. (tempExecutor)
                     */
                    log.info("[CompletableFuture-thenApply] CompletableFuture execution thread : {}, security context : {}",
                            Thread.currentThread().getName(),
                            SecurityContextHolder.getContext().hashCode()
                    );
                    return SecurityContextHolder.getContext().getAuthentication();
                })
                ;
    }
}
