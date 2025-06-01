package dev.starryeye.oauth2_resource_server;

import dev.starryeye.oauth2_resource_server.dto.Photo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PhotoController {

    @GetMapping("/photos")
    public List<Photo> photos() {

        return List.of(
                new Photo(1L, "photo 1", "this is photo 1", 1L),
                new Photo(2L, "photo 2", "this is photo 2", 1L),
                new Photo(3L, "photo 3", "this is photo 3", 1L)
        );
    }
}
