package dev.starryeye.custom_authenticate_mvc_integration_2.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Callable;

@Slf4j
@RestController
public class CallableController {

    @GetMapping("/return-callable")
    public Callable<Authentication> returnCallable(
            @CurrentSecurityContext SecurityContext securityContext
    ) {
        /**
         * Callable 로 리턴할 경우 ..
         *      요청 스레드가 아닌 다른 스레드로 Callable 내부 작업이 수행되는데..
         *      WebAsyncManagerIntegrationFilter, WebAsyncManager, SecurityContextCallableProcessionInterceptor 등을 통해
         *      기존 스레드의 ThreadLocal 에 존재하던 SecurityContext 가 다른스레드에서 연동되게 지원한다.
         */

        // Spring Web MVC 의 Controller 에서 Callable 을 리턴하면 요청 스레드에서 수행하지 않고 ..
        // 기본적으로 별도의 스레드(or 스레드풀.. 작업이 생기면 스레드를 생성 및 할당하는 방식일 수도 있음)로 수행한다.
        // -> 개발자가 컨트롤 하기 위해 WebMvcConfig 처럼 직접 설정해 줄 수 도 있다.

        /**
         * 요청 스레드 영역
         */
        log.info("[Callable] request thread : {}, security context : {}",
                Thread.currentThread().getName(),
                securityContext.hashCode()
        );

        return new Callable<Authentication>() {
            @Override
            public Authentication call() throws Exception {

                /**
                 * Callable<Authentication>::call 을 수행하는 스레드 영역
                 */

                SecurityContext callableSecurityContext = SecurityContextHolder.getContext();

                log.info("[Callable] callable execution thread : {}, security context : {}",
                        Thread.currentThread().getName(),
                        callableSecurityContext.hashCode()
                );

                return callableSecurityContext.getAuthentication();
            }
        };
    }
}
