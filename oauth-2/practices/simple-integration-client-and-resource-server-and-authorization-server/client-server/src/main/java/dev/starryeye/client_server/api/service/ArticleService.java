package dev.starryeye.client_server.api.service;

import dev.starryeye.client_server.client.ArticleClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleClient client;
}
