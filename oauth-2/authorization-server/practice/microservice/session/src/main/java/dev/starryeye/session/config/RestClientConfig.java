package dev.starryeye.session.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

	/**
	 * 내부 호출용 RestClient. 타임아웃을 두어 한 RP 가 느릴 때 발송 스레드가 묶이지 않게 한다.
	 */

	@Bean
	public RestClient.Builder restClientBuilder() {
		ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
				.withConnectTimeout(Duration.ofSeconds(2))
				.withReadTimeout(Duration.ofSeconds(2));
		return RestClient.builder().requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
	}
}
