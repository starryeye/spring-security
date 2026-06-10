## simple-integration-client-and-resource-server-and-authorization-server
- client, resource server, authorization server 를 모두 spring 으로 만들어본다.

### projects
- authorization-server
  - authorization server 역할 (port 8091)
  - spring-boot-starter-oauth2-authorization-server
- client-server
  - client 역할 (port 8080)
  - spring-boot-starter-oauth2-client
- resource-server-article
  - resource server 역할 (port 8081)
  - spring-boot-starter-oauth2-resource-server
  - content 와 comment 를 조합하여 article 로 응답
  - comment 는 resource-server-comment 로 access token 을 그대로 relay 하여 조회
- resource-server-comment
  - resource server 역할 (port 8082)
  - spring-boot-starter-oauth2-resource-server

### user / client
- 로그인 사용자
  - user / 1111
- 등록된 클라이언트
  - my-spring-client / secret
  - scope: openid, profile, custom-scope, content, comment
  - access token 만료 60초, refresh token 재사용 X
  - consent(동의) 필요

### 주의
- 브라우저는 http://127.0.0.1:8080 으로 접속해야한다.
  - spring authorization server 는 redirect uri 의 host 로 localhost 를 허용하지 않으므로 127.0.0.1 로 등록하였다.

### client-server api
- "/", permitAll
  - 인증 전
    - login 버튼
  - 인증 완료 후
    - article 조회 버튼 (단건, 전체)
    - access token 보는 버튼
    - logout 버튼
      - RP 만 logout 됨 (OP 는 logout X)
- "/article/{articleId}", authenticated
  - access token 으로 resource-server-article 로 요청
  - access token 이 만료되면 OAuth2AuthorizedClientManager 가 refresh token 으로 갱신
- "/articles", authenticated
- "/token", authenticated
  - 현재 access token 응답

### resource-server-article api
- "/articles/{id}", authenticated and SCOPE_content
  - access token 검증 filter 로 인증
  - content 조회 후 resource-server-comment 로 comment 조회 (수신한 access token 을 그대로 relay)
  - Article(content + comment list) 응답
- "/articles", authenticated and SCOPE_content
  - 전체 Article List 응답

### resource-server-comment api
- "/comments" (POST), authenticated and SCOPE_comment
  - access token 검증 filter 로 인증
  - contentId 에 해당하는 Comment List 응답
  - 댓글이 없으면 기본 Comment 응답 (fallback)
