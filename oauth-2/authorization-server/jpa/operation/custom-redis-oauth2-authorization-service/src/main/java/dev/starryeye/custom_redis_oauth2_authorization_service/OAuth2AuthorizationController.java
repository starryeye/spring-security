package dev.starryeye.custom_redis_oauth2_authorization_service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class OAuth2AuthorizationController {

    /**
     * Redis 에 저장된 인가 상태 키들과 TTL 을 관찰하는 controller 이다.
     *      grant 수행 -> 본체 hash + 토큰별 역색인 키 생성, TTL 이 줄어들다가 0 이 되면 스스로 사라진다.
     *      (RDB 였다면 배치로 지워야 했을 것들.. oauth2-authorization-purge 프로젝트와 대조해볼 것)
     */

    private final StringRedisTemplate redisTemplate;

    @GetMapping("/oauth2-authorizations/raw")
    public List<Map<String, Object>> keys() {

        Set<String> keys = redisTemplate.keys("oauth2:authorization:*");

        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : keys) {
            result.add(Map.of(
                    "key", shorten(key),
                    "type", String.valueOf(redisTemplate.type(key)),
                    "ttlSeconds", redisTemplate.getExpire(key, TimeUnit.SECONDS)
            ));
        }
        result.sort((a, b) -> String.valueOf(a.get("key")).compareTo(String.valueOf(b.get("key"))));

        return result;
    }

    // 토큰 값이 긴 키는 잘라서 표시
    private String shorten(String key) {
        return key.length() > 60 ? key.substring(0, 60) + ".." : key;
    }
}
