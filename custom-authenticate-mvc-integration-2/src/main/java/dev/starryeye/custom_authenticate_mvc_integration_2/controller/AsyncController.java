package dev.starryeye.custom_authenticate_mvc_integration_2.controller;

import dev.starryeye.custom_authenticate_mvc_integration_2.service.AsyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AsyncController {

    private final AsyncService asyncService;

    @GetMapping("/async")
    public CompletableFuture<Authentication> async() {

        /**
         * CompletableFutureController 와 마찬가지로
         *      CallableController 처럼 ThreadLocal SecurityContext 가 연동 되지 않음에 주의하자.
         *
         * -> SecurityConfig 에서..
         *      SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL); 를 적용해주면..
         *      ThreadLocal 연동이 된다..
         *          다른 스레드에서 SecurityContextHolder.getContext() 접근 시 동일 객체 접근됨.
         *      대신.. @CurrentSecurityContext 가 동작안된다...(todo)
         */

        log.info("[Async-Controller] request thread : {}, security context : {}",
                Thread.currentThread().getName(),
                SecurityContextHolder.getContext().hashCode()
        );

        return asyncService.asyncMethod()
                .thenApply(authentication -> {
                    log.info("[Async-CompletableFuture] CompletableFuture thenAccept execution thread : {}, security context : {}",
                            Thread.currentThread().getName(),
                            SecurityContextHolder.getContext().hashCode()
                    );
                    return authentication;
                })
                ;
    }
}
