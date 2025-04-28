package dev.starryeye.custom_jwt_authentication_converter;

import lombok.Builder;
import lombok.Getter;

@Getter
public class Photo {

    private final Long id;
    private final String title;
    private final String description;

    private final Long ownerId;

    @Builder
    private Photo(Long id, String title, String description, Long ownerId) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.ownerId = ownerId;
    }
}
