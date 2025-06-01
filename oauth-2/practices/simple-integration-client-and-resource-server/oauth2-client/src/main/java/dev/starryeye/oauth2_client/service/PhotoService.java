package dev.starryeye.oauth2_client.service;

import dev.starryeye.oauth2_client.client.PhotoClient;
import dev.starryeye.oauth2_client.dto.Photo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoClient photoClient;

    public List<Photo> getPhotos() {

        return photoClient.findAll();
    }
}
