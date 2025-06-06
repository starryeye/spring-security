package dev.starryeye.resource_server_comment.api.service;

import dev.starryeye.resource_server_comment.dto.Comment;
import dev.starryeye.resource_server_comment.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository repository;

    public List<Comment> getCommentsBy(Long contentId) {

        List<Comment> comments = repository.findByContentId(contentId);

        if (comments.isEmpty()) {
            return getFallbackComments(contentId);
        }

        return comments;
    }

    private List<Comment> getFallbackComments(Long contentId) {
        return List.of(Comment.ofDefault(contentId));
    }
}
