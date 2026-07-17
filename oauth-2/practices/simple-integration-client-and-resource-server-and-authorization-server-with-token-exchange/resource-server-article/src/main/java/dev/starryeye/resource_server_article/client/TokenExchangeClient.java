package dev.starryeye.resource_server_article.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class TokenExchangeClient {

    /**
     * authorization server 의 token endpoint 로 token exchange (RFC 8693) 를 수행한다.
     *      relay 버전(simple-integration-client-and-resource-server-and-authorization-server)과의 유일한 차이가 이 교환이다.
     *      grant 자체의 상세는 authorization-server/grant/token-exchange 프로젝트 참고.
     *
     * 교환 요청 구성..
     *      subject_token : 사용자로부터 수신한 access token.. 발급될 토큰이 사용자 신원(sub)을 유지하는 근거
     *      actor_token : 이 서버(article) 자신의 client_credentials 토큰.. 발급 토큰의 act claim 으로 "누가 대신 호출하는가" 를 남긴다 (delegation)
     *      scope=comment : comment 서버 호출에 필요한 최소 scope 만 요청 (my-article-client 의 등록 scope 상한과 일치)
     *      resource : 발급될 토큰의 대상(comment 서버 URI) 지정.. RFC 8707 resource indicator
     *
     * 참고. actor token 과 교환된 토큰은 만료 전까지 재사용할 수 있으므로 운영에서는 캐시하는 것이 자연스럽다..
     *      여기서는 흐름이 잘 보이도록 요청마다 새로 수행한다.
     */

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    public TokenExchangeClient(
            RestClient.Builder builder,
            @Value("${my.token-exchange.token-uri}") String tokenUri,
            @Value("${my.token-exchange.client-id}") String clientId,
            @Value("${my.token-exchange.client-secret}") String clientSecret
    ) {
        this.restClient = builder.baseUrl(tokenUri).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String exchange(String subjectToken) {

        String actorToken = requestActorToken();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
        form.add("subject_token", subjectToken);
        form.add("subject_token_type", "urn:ietf:params:oauth:token-type:access_token");
        form.add("actor_token", actorToken);
        form.add("actor_token_type", "urn:ietf:params:oauth:token-type:access_token");
        form.add("scope", "comment");
        // 발급될 토큰의 대상 지정.. resource indicator (RFC 8707/8693) 는 절대 URI 로 대상을 가리킨다.
        //     (audience 파라미터는 논리적 이름으로 지정하는 변형.. 여기서는 resource 로 통일)
        //     기본 구현은 이 파라미터를 발급에 반영하지 않으므로 authorization server 의 검증(invalid_target) + customizer(aud 반영)가 처리한다.
        form.add("resource", "http://localhost:8082");

        return requestToken(form);
    }

    private String requestActorToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        return requestToken(form);
    }

    private String requestToken(MultiValueMap<String, String> form) {

        Map<String, Object> response = restClient.post()
                .headers(httpHeaders -> httpHeaders.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });

        return (String) response.get("access_token");
    }
}
