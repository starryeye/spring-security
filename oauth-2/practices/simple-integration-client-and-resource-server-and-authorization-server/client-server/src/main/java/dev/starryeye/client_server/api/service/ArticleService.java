package dev.starryeye.client_server.api.service;

import dev.starryeye.client_server.api.service.exception.NotFoundException;
import dev.starryeye.client_server.client.ArticleClient;
import dev.starryeye.client_server.dto.Article;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleClient articleClient;

    public Article getArticle(Long id) {
        return articleClient.getArticleBy(id)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));
    }
}
