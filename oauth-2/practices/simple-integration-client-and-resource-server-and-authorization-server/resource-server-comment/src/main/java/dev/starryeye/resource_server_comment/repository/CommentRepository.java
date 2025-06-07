package dev.starryeye.resource_server_comment.repository;

import dev.starryeye.resource_server_comment.dto.Comment;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class CommentRepository {

    private final Map<Long, List<Comment>> comments;

    public CommentRepository() {
        this.comments = new ConcurrentHashMap<>();
    }

    public List<Comment> findByContentId(Long contentId) {
        return this.comments.get(contentId);
    }
}
