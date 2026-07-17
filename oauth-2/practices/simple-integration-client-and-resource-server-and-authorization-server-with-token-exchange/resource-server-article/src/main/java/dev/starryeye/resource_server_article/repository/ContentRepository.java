package dev.starryeye.resource_server_article.repository;

import dev.starryeye.resource_server_article.dto.Content;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ContentRepository {

    private final Map<Long, Content> contents;

    public ContentRepository() {
        this.contents = new ConcurrentHashMap<>();

        OffsetDateTime now = OffsetDateTime.now();
        this.contents.put(1L, new Content(1L, "content 1", "this is content 1", "starryeye", now, now));
        this.contents.put(2L, new Content(2L, "content 2", "this is content 2", "starryeye", now, now));
        this.contents.put(3L, new Content(3L, "content 3", "this is content 3", "starryeye", now, now));
    }

    public Optional<Content> findById(Long id) {
        return Optional.ofNullable(this.contents.get(id));
    }

    public List<Content> findAll() {
        return List.copyOf(this.contents.values());
    }

}
