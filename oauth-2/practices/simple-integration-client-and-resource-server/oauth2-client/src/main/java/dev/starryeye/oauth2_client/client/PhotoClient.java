package dev.starryeye.oauth2_client.client;

import dev.starryeye.oauth2_client.dto.Photo;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PhotoClient {

    private final RestClient restClient;

    public PhotoClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("http://localhost:8081")
                .build();
    }

    public List<Photo> findAll(OAuth2AccessToken accessToken) {
        return restClient.get()
                .uri("/photos")
                .header("Authorization", "Bearer " + accessToken.getTokenValue())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

}
