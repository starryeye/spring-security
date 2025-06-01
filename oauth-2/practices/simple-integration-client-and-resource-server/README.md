## simple-integration-client-and-resource-server
- client, resource server 를 spring 으로 만들어본다.
- authorization server 는 keycloak 을 사용한다.

### projects
- oauth2-client
  - client 역할 
  - spring-boot-starter-oauth2-client
- oauth2-resource-server
  - resource server 역할
  - spring-boot-starter-oauth2-resource-server

### architecture
<img width="811" alt="Image" src="https://github.com/user-attachments/assets/33e2bd29-557c-486b-9dfa-94a26c011c16" />


### client api
- "/", permitAll
  - 인증 전
    - login 버튼
  - 인증 완료 후
    - photo viewer 페이지로 가는 버튼
    - access token 보는 버튼
- "/photo-viewer, authenticated
  - photos 버튼
    - access token 으로 resource server 로 요청
  - logout 버튼
    - RP 만 logout 됨 (OP 는 logout X) 

### resource server api
- "/photos", authenticated and SCOPE_photos
  - access token 검증 filter 로 인증
  - SCOPE_photos 권한 있어야 api 호출 가능
  - Photo List 응답
