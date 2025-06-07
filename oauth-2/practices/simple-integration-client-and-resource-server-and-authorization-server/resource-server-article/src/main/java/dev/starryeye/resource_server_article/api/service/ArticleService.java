package dev.starryeye.resource_server_article.api.service;

import dev.starryeye.resource_server_article.client.CommentClient;
import dev.starryeye.resource_server_article.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ContentRepository contentRepository;
    private final CommentClient commentClient;
}
