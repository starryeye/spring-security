package dev.starryeye.resource_server_article.api.service;

import dev.starryeye.resource_server_article.api.service.exception.NotFoundException;
import dev.starryeye.resource_server_article.api.service.response.Article;
import dev.starryeye.resource_server_article.client.CommentClient;
import dev.starryeye.resource_server_article.dto.Comment;
import dev.starryeye.resource_server_article.dto.Content;
import dev.starryeye.resource_server_article.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ContentRepository contentRepository;
    private final CommentClient commentClient;

    public Article getArticleBy(Long contentId) {

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));

        List<Comment> comments = commentClient.getCommentsBy(contentId);


        return new Article(contentId, content, comments);
    }
}
