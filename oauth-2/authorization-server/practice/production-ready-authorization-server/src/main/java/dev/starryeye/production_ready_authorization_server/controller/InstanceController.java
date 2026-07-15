package dev.starryeye.production_ready_authorization_server.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class InstanceController {

    /**
     * 어떤 인스턴스가 요청을 처리했는지 관찰용 api 이다. (jpa/advance/spring-session 프로젝트의 "/whoami" 이식)
     *      LB(9000) 로 연속 호출하면 nginx round robin 에 의해 instancePort 가 8091/8092 로 번갈아 나온다.
     *      로그인 후 호출하면 어느 인스턴스에서도 인증이 유지되는 것(세션 외부화)도 함께 보인다.
     */

    @Value("${server.port}")
    private int serverPort;

    @GetMapping("/whoami")
    public Map<String, Object> whoami(Authentication authentication) {
        return Map.of(
                "instancePort", serverPort,
                "authenticated", authentication != null && authentication.isAuthenticated(),
                "principal", authentication != null ? authentication.getName() : "anonymous"
        );
    }
}
