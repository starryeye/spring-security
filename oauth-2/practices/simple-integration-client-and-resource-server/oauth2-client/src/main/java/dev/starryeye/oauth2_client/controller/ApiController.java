package dev.starryeye.oauth2_client.controller;

import dev.starryeye.oauth2_client.dto.Photo;
import dev.starryeye.oauth2_client.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ApiController {

    private final PhotoService photoService;

    @GetMapping("/photos")
    public List<Photo> photos() {
        return photoService.getPhotos();
    }

    @GetMapping("/token")
    public String token(@RegisteredOAuth2AuthorizedClient("/my-keycloak") OAuth2AuthorizedClient authorizedClient) {

        return authorizedClient.getAccessToken().getTokenValue();
    }
}
