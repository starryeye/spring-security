## simple-integration-client-and-resource-server-and-authorization-server-with-token-exchange
- simple-integration-client-and-resource-server-and-authorization-server 의 **token exchange 버전**이다.
- 구성과 흐름은 원본과 동일하고.. article -> comment 호출 방식만 다르다.
  - 원본(relay): 수신한 access token 을 **그대로** comment 로 전달
  - 이 버전(exchange): 수신한 토큰을 subject 로 제출해 **comment 호출용 토큰을 새로 발급받아** 전달 (RFC 8693.. authorization-server/grant/token-exchange 참고)

### relay 와 뭐가 달라지나
| | relay (원본) | token exchange (이 버전) |
|---|---|---|
| 사용자 토큰의 scope | content, comment 모두 필요 | **content 만** (comment 권한은 article 이 교환으로 획득) |
| comment 가 받는 토큰 | 사용자 원본 토큰 (aud/scope 과잉) | comment 호출용 발급 토큰 (aud=my-article-client, scope=comment) |
| 행위자 추적 | 불가 (article 이 대신 왔는지 토큰에 안 남음) | **act claim** 에 my-article-client 가 남는다 (delegation) |
| 사용자 토큰 유출 반경 | 여러 서버로 퍼진다 | article 까지만 |

### projects
- authorization-server (8091)
  - relay 버전 대비 client 가 하나 추가되었다.. **my-article-client** (TOKEN_EXCHANGE + CLIENT_CREDENTIALS, scope: comment 만)
  - 교환 요청 scope 의 상한은 "교환 client 의 등록 scope" 라서.. article 은 comment 를 넘는 권한을 구조적으로 못 받는다.
- client-server (8080)
  - scope: openid, profile, content.. **comment 가 없다** (relay 버전과의 가시적 차이)
- resource-server-article (8081)
  - SCOPE_content 요구. comment 호출 전 TokenExchangeClient 로 교환을 수행한다.
    - subject_token = 수신한 사용자 토큰, actor_token = 자신의 client_credentials 토큰 (act 를 남기는 delegation)
- resource-server-comment (8082)
  - SCOPE_comment 요구. 수신 토큰의 sub/act/aud/scope 를 감사 로그로 남긴다. (교환의 흔적이 여기서 보인다)

### user / client
- 로그인 사용자
  - user / 1111
- 등록된 클라이언트
  - my-spring-client / secret (사용자용 code grant.. scope: openid, profile, custom-scope, content)
  - my-article-client / article-secret (교환용.. scope: comment)

### 확인 포인트
1. 브라우저(http://127.0.0.1:8080)에서 로그인 -> article 조회가 원본과 동일하게 동작한다. (교환은 article 내부에서 투명하게 일어난다)
2. comment 서버의 [감사] 로그.. sub=user (사용자 신원 유지), act 에 my-article-client, aud=[my-article-client, http://localhost:8082], scope=[comment]
3. 사용자 access token(client 의 "/token" 으로 확인 가능)을 comment 로 직접 relay 해보면 **401** 이다..
   comment 의 aud 검증(resource-server-comment 필요)이 인증 단계에서 거른다. (aud 검증이 없었다면 scope 부족 403 이었을 것)
   **relay 방식이었다면 실패했을 호출이 exchange 로는 성공한다** 는 것이 이 프로젝트의 요지다.
4. **resource indicator (RFC 8707) 로 대상까지 못박는다**.. client 가 요청 시점에 대상을 지정하는 client 요청 주도 방식이다.
   - client-server 가 authorize 요청에 resource=http://localhost:8081 (article), article 이 교환 요청에 resource=http://localhost:8082 (comment) 를 싣는다.
   - authorization server 는 client 별 허용 자원 목록과 대조해 벗어나면 **invalid_target** 으로 거부하고(발급 측 통제),
     허용된 값은 customizer 가 access token 의 aud 에 반영한다. (refresh 재발급도 저장된 authorize 요청에서 같은 값을 읽는다)
   - article/comment 는 각자 자기 URI 가 aud 에 있어야만 토큰을 수락한다(JwtDecoder validator.. 수신 측 방어).
   - 기본 구현은 resource 파라미터를 발급에 반영하지 않고 aud 를 "요청 client 의 client_id" 로만 넣으며 resource server 도 aud 를 검증하지 않는다.. 전부 확장한 것이다.
   - 참고. 서버가 client 별 대상을 정책으로 고정 부여하는 "서버 정책 주도" 유파(keycloak audience mapper)도 정당하다.. 대상이 고정된 토폴로지에선 그쪽이 더 단순하다.

### 주의
- 브라우저는 http://127.0.0.1:8080 으로 접속해야한다.
  - spring authorization server 는 redirect uri 의 host 로 localhost 를 허용하지 않으므로 127.0.0.1 로 등록하였다.
- 기동 순서는 authorization-server -> resource-server-comment -> resource-server-article -> client-server 순이 자연스럽다.
- 그 외 api 구성(client-server, article, comment)은 원본 README 와 동일하다.
