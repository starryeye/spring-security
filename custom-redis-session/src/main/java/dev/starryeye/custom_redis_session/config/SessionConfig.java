package dev.starryeye.custom_redis_session.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

// You can configure the RedisSessionRepository by using the @EnableRedisHttpSession annotation
// This creates a Spring bean with the name of springSessionRepositoryFilter that implements Filter.
// The filter is in charge of replacing the HttpSession implementation to be backed by Spring Session.
@EnableRedisHttpSession // 라이브러리와 application.yml 만 설정해도 필수적인 어노테이션은 아닌듯함
@Configuration
public class SessionConfig {

    // Spring Boot automatically creates a RedisConnectionFactory
    // that connects Spring Session to a Redis Server on localhost on port 6379 (default port).
}
