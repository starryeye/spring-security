package dev.starryeye.resource_server_comment.repository;

import dev.starryeye.resource_server_comment.dto.Comment;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class CommentRepository {

    private final Map<Long, List<Comment>> comments;

    public CommentRepository() {
        this.comments = new ConcurrentHashMap<>();

        OffsetDateTime now = OffsetDateTime.now();
        this.comments.put(1L, List.of(
                new Comment(1L, "user1", "comment 1 of content 1", now, now, 1L),
                new Comment(2L, "user2", "comment 2 of content 1", now, now, 1L)
        ));
        this.comments.put(2L, List.of(
                new Comment(3L, "user1", "comment 1 of content 2", now, now, 2L)
        ));
        // contentId 3 은 댓글이 없는 상태로 두어 CommentService 의 fallback 동작을 확인한다.
    }

    public List<Comment> findByContentId(Long contentId) {
        return this.comments.getOrDefault(contentId, List.of());
    }
}
