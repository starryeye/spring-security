## production-ready-authorization-server
- authorization-server 폴더에서 학습한 조각들을 전부 조합하여 운영급 authorization server 를 만들어본다.
- 로드밸런서(nginx) 뒤에 인스턴스 2개를 두는 구도로, 수평 확장(scale-out) 조건인 "상태 외부화" 를 총증명한다.

### 구도
```
client ──> nginx LB (localhost:9000, round robin)
              ├──> instance 1 (8091) ──┐
              └──> instance 2 (8092) ──┤
                                       ├──> MySQL (3306) : client 등록, 동의 기록, 사용자
                                       └──> Redis (6379) : 토큰 상태(OAuth2Authorization), 로그인 세션
```

### 조합 요소와 출처 프로젝트
| 요소 | 저장소/방식 | 출처 |
|---|---|---|
| RegisteredClientRepository | JPA + MySQL | jpa/custom-registered-client-repository |
| OAuth2AuthorizationConsentService | JPA + MySQL | jpa/custom-oauth2-authorization-consent-service |
| OAuth2AuthorizationService | Redis (TTL 자동 만료) | jpa/advance/custom-redis-oauth2-authorization-service |
| 로그인 세션 | Spring Session + Redis | jpa/advance/spring-session |
| 서명 키 | keystore(PKCS12) + 로테이션(이전 키 공개키 노출) | jpa/advance/custom-jwk-source |
| 토큰 claim | kid 지정 + authorities/nickname claim | custom-oauth2-token-customizer |
| 로그인/동의 페이지 | thymeleaf 커스텀 | custom-login-and-consent-page |
| client 등록 | admin API (clientType 프리셋, bcrypt) | jpa/custom-registered-client-repository |
| 사용자 저장소 | JPA + MySQL (UserDetailsService 직접 구현) + 등록 admin API | 이 프로젝트에서 추가 |
| admin API 보호 | ROLE_ADMIN + http basic, 최초 관리자 부팅 시 부트스트랩 | 이 프로젝트에서 추가 |
| 프록시 대응 | ForwardedHeaderFilter + issuer 고정 | etc/forwarded-header-filter |
| 감사 로그 | 인증 이벤트 리스너 | etc/authentication-events |

- 저장소 선택 기준.. 내구성 데이터(client, 동의)는 MySQL, 토큰 수명만큼만 살면 되는 회전 데이터(토큰 상태, 세션)는 Redis
  - Redis 채택으로 만료 데이터 purge 배치(jpa/advance/oauth2-authorization-purge)가 불필요해진다.

### 실행 방법
1. 인프라 기동 (nginx, mysql, redis)
   - `cd docker-compose && docker-compose -p production-ready-authorization-server up -d`
2. 빌드 후 인스턴스 2개 기동 (java 21)
   - `./gradlew bootJar`
   - `java -jar build/libs/production-ready-authorization-server-0.0.1-SNAPSHOT.jar`
   - `java -jar build/libs/production-ready-authorization-server-0.0.1-SNAPSHOT.jar --server.port=8092`
3. 관리자 인증(basic)으로 사용자·client 등록 (http/api.http) -> 응답의 clientId/clientSecret 로 http/ 의 grant 수행
   - 모든 요청은 LB(http://localhost:9000) 로 보낸다. 인스턴스에 직접 보내면 LB 구도의 의미가 없다.

### user / client
- 관리자
  - admin / 1234 (ROLE_ADMIN).. 부팅 시 부트스트랩 생성 (AdminAccountInitializer, 설정은 application.yml)
- 로그인 사용자
  - 없음. admin API(POST /users)로 등록한다. 예시는 user / 1111 (ROLE_USER, ROLE_CUSTOMER.. authorities claim 관찰용)
- 등록된 클라이언트
  - 없음. admin API(POST /registered-clients)로 등록한다. (clientId/secret 은 서버가 생성하여 응답, secret 은 등록 응답에서 한번만 노출)

### 확인 포인트
1. `/whoami` 연속 호출 -> instancePort 가 8091/8092 번갈아 응답 (round robin)
2. authorization code grant 전체 흐름(로그인 -> 동의 -> code -> token)이 LB 로만 수행해도 끊기지 않는다.
   - 매 단계를 다른 인스턴스가 처리하는데도 세션(redis), 진행 중 인가 state(redis), code(redis), 동의(mysql)가 공유되기 때문
3. 발급된 JWT.. iss = http://localhost:9000 (LB 주소), kid = keystore 의 현재 키 alias, authorities claim 포함
4. 두 인스턴스 모두 재기동해도.. 세션 유지, refresh token grant 성공, 재인가 시 기승인 동의 표시, 기존 JWT 검증 유지
5. 감사 로그([인증 성공] 등)가 처리 인스턴스의 로그에만 찍힌다 -> 두 로그를 대조하면 분배가 보인다.
6. admin API(/users, /registered-clients)는 관리자 basic 인증으로만 성공한다. (일반 사용자는 403, 틀린 비밀번호는 401 + 감사 로그)

### OpenID conformance 검증
- OpenID Foundation 의 공식 적합성 테스트(conformance suite, OIDCC Basic OP 플랜 35개 모듈)를 self-host 로 돌려 표준 적합성을 검증했다.
- 이 과정에서 **다중 인스턴스 결함을 실제로 발견**했다.. id token 의 auth_time 이 인스턴스별 InMemory SessionRegistry 에서 나와
  두 인스턴스가 서로 다른 값을 발급 (3개 모듈 실패) -> redis(spring session) 기반 공유 SessionRegistry 로 수정 후 통과.
- 실행 방법, 전체 결과표, 발견/수정 상세: [openid-conformance/README.md](openid-conformance/README.md)

### 주의
- redirect uri 의 host 로 localhost 는 허용되지 않으므로 127.0.0.1 로 등록해야 한다.
- 브라우저 흐름과 .http 파일 모두 http://localhost:9000 (LB) 기준이다.
- nginx upstream 이름에 underscore 를 쓰면 Host 헤더로 전달되어 tomcat 이 400 을 반환한다. (hyphen 사용)
