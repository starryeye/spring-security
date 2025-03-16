package dev.starryeye.oauth2_authorized_client_manager.config;

import dev.starryeye.oauth2_authorized_client_manager.client.interceptor.OAuth2ClientCredentialsRequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(OAuth2ClientCredentialsRequestInterceptor interceptor) {
        return RestClient.builder()
                .requestInterceptor(interceptor)
                .build();
    }
}
