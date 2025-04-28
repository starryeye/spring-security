package dev.starryeye.custom_jwt_authentication_converter;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;

@RestController
public class PhotoController {

    @GetMapping("/photos/{photoId}")
    public Photo getPhoto(@PathVariable Long photoId) {

        return Photo.builder()
                .id(photoId)
                .title("Photo " + photoId)
                .description("This is a photo with ID " + photoId)
                .ownerId(new Random().nextLong(100))
                .build();
    }

    @GetMapping("/private/photos/{photoId}")
    public Photo getPrivatePhoto(@PathVariable Long photoId) {

        return Photo.builder()
                .id(photoId)
                .title("Private Photo " + photoId)
                .description("This is a private photo with ID " + photoId)
                .ownerId(new Random().nextLong(100))
                .build();
    }
}
