package dev.starryeye.client_registry;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {

	/**
	 * client 조회 캐시.. 짧은 TTL(30초)로 원본 갱신을 곧 반영하면서도 반복 조회 부하를 줄인다.
	 */
	@Bean
	public CacheManager cacheManager() {
		CaffeineCacheManager manager = new CaffeineCacheManager("clients");
		manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)));
		return manager;
	}
}
