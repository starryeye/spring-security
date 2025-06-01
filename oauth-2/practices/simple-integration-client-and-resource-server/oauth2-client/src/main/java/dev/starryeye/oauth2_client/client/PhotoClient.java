package dev.starryeye.oauth2_client.client;

import dev.starryeye.oauth2_client.client.interceptor.AuthorizedClientManagerInterceptor;
import dev.starryeye.oauth2_client.dto.Photo;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class PhotoClient {

    private final RestClient restClient;

    public PhotoClient(RestClient.Builder restClientBuilder, AuthorizedClientManagerInterceptor authorizedClientManagerInterceptor) {
        this.restClient = restClientBuilder
                .baseUrl("http://localhost:8081")
                .requestInterceptor(authorizedClientManagerInterceptor)
                .build();
    }

    public List<Photo> findAll() {
        return restClient.get()
                .uri("/photos")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

}
