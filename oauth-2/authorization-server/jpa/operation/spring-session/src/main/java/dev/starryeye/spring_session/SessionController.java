package dev.starryeye.spring_session;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class SessionController {

    /**
     * 세션 외부화 관찰용 controller 이다.
     *
     * "/whoami"
     *      현재 요청을 처리한 인스턴스(port)와 인증 상태를 보여준다.
     *      8091 에서 로그인한 뒤 같은 쿠키로 8092 의 "/whoami" 를 호출하면.. 다른 인스턴스인데 인증이 유지되어 있는 것을 볼 수 있다.
     * "/sessions/raw"
     *      Redis 에 저장된 spring:session:* 키와 TTL(세션 타임아웃) 관찰.
     */

    private final StringRedisTemplate redisTemplate;

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

    @GetMapping("/sessions/raw")
    public List<Map<String, Object>> sessions() {

        Set<String> keys = redisTemplate.keys("spring:session:*");

        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : keys) {
            result.add(Map.of(
                    "key", key,
                    "type", String.valueOf(redisTemplate.type(key)),
                    "ttlSeconds", redisTemplate.getExpire(key, TimeUnit.SECONDS)
            ));
        }
        result.sort((a, b) -> String.valueOf(a.get("key")).compareTo(String.valueOf(b.get("key"))));

        return result;
    }
}
