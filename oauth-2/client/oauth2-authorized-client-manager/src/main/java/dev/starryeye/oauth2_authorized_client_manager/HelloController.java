package dev.starryeye.oauth2_authorized_client_manager;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequiredArgsConstructor
public class HelloController {

    private final RestClient restClient;
    private final RestClient restClientForInternalAPI;

    @GetMapping("/authorization-code-grant")
    public String authorizationCodeGrant() {
        // todo, to resource server call
        return "todo";
    }

    @GetMapping("/client-credentials-grant")
    public String clientCredentialsGrant() {
        // todo, to resource server call
        return "todo";
    }
}
