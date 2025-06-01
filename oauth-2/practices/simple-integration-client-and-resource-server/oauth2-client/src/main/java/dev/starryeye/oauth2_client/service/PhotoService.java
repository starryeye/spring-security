package dev.starryeye.oauth2_client.service;

import dev.starryeye.oauth2_client.client.PhotoClient;
import dev.starryeye.oauth2_client.dto.Photo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PhotoService {

    private static final String CLIENT_REGISTRATION_ID = "my-keycloak";

    private final OAuth2AuthorizedClientService authorizedClientService;

    private final PhotoClient photoClient;

    public List<Photo> getPhotos() {

        OAuth2AccessToken accessToken = getAccessToken();

        return photoClient.findAll(accessToken);
    }

    private OAuth2AccessToken getAccessToken() {
        Authentication authentication = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();

        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(CLIENT_REGISTRATION_ID, authentication.getName());

        return authorizedClient.getAccessToken();
    }
}
