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
