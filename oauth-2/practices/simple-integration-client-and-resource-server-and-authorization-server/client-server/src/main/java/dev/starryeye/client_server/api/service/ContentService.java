package dev.starryeye.client_server.api.service;

import dev.starryeye.client_server.client.ContentClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContentService {

    private final ContentClient client;
}
