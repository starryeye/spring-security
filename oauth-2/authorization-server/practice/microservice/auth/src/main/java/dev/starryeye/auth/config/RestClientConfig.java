package dev.starryeye.auth.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RestClientConfig {

	/**
	 * 내부 REST 호출(user-directory)은 짧은 타임아웃(2초)만 두고 재시도는 하지 않는다.
	 *      org.springframework.boot.web.client.ClientHttpRequestFactorySettings/ClientHttpRequestFactories 는
	 *      Boot 3.4 부터 deprecated(forRemoval) 이므로, org.springframework.boot.http.client 패키지의
	 *      ClientHttpRequestFactorySettings.defaults() + ClientHttpRequestFactoryBuilder.detect().build(settings) 를 사용한다.
	 */
	@Bean
	public RestClientCustomizer restClientCustomizer() {
		ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
				.withConnectTimeout(Duration.ofSeconds(2))
				.withReadTimeout(Duration.ofSeconds(2));
		return builder -> builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
	}
}
