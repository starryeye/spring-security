package dev.starryeye.resource_server_article.repository;

import dev.starryeye.resource_server_article.dto.Content;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ContentRepository {

    private final Map<Long, Content> contents;

    public ContentRepository() {
        this.contents = new ConcurrentHashMap<>();
    }

    public Optional<Content> findById(Long id) {
        return Optional.ofNullable(this.contents.get(id));
    }

}
