package dev.starryeye.resource_server_article.api.service;

import dev.starryeye.resource_server_article.client.CommentClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContentService {

    private final CommentClient client;
}
