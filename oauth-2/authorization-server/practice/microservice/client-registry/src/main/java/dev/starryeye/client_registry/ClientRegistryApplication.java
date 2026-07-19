package dev.starryeye.client_registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class ClientRegistryApplication {

	/**
	 * client 메타데이터를 소유하는 서비스이다.
	 *      client 정보는 자주 변하지 않으므로 짧은 TTL 캐시(Caffeine)를 둔다.
	 *      -> client-registry 가 잠깐 느려지거나 죽어도 캐시된 client 로 authorize/token 이 견딘다. (분산 캐시 필요성)
	 */

	public static void main(String[] args) {
		SpringApplication.run(ClientRegistryApplication.class, args);
	}
}
