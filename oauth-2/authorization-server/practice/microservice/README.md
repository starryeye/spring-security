# microservice authorization server (첫 관통 슬라이스)

- Spring Authorization Server starter **없이** OAuth 로직을 직접 구현하고, 하나의 인가 서버를 **6개 독립 마이크로서비스**로 쪼갠 학습 프로젝트다.
- 첫 슬라이스의 목표: **authorization code + PKCE(S256) flow 하나**를 6개 서비스로 관통시키는 것. (consent/id token/refresh/introspection 등은 이후 슬라이스)
- "빅테크는 인가 서버를 내부적으로 여러 서비스로 분해한다"(토큰 발급/로그인 UX/디렉토리/키 관리 분리, KMS 키 격리)를 축소판으로 재현한다.

## 구도

```
브라우저/클라이언트
   │
   ▼
 gateway (nginx, :9000)  ── 경로로 라우팅, /internal/* 은 외부 비노출
   ├─ /oauth2/authorize, /login          ─▶ auth (:8081)   front-channel
   └─ /oauth2/token, /oauth2/jwks, /.well-known ─▶ token (:8082)  back-channel
                                                 │
   auth ─▶ user-directory (:8084)  로그인 credential 검증 위임
   auth ─▶ client-registry (:8085) client 메타 조회 (+Caffeine 캐시)
   auth ─▶ Redis                   authorization code 저장 (auth:code:{code}, TTL 60s)
                                                 │
   token ─▶ Redis                  code 원자적 1회 소비 (GETDEL)
   token ─▶ client-registry        client 인증(bcrypt) 검증
   token ─▶ signing (:8083)        JWT 서명 위임 (개인키는 signing 만 보유)
   token, signing ─▶ jwks          공개키 노출

 MySQL : user-directory(users), client-registry(clients)
 Redis : authorization code, 로그인 세션(Spring Session)
```

## 서비스별 책임과 소유 데이터

| 서비스 | 포트 | 책임 | 소유 데이터 |
|---|---|---|---|
| gateway | 9000 | nginx 경로 라우팅 (front/back-channel 분리, /internal/* 격리) | 없음 |
| auth | 8081 | front-channel: 로그인, `/oauth2/authorize`, code 발급 | (Redis code, 세션) |
| token | 8082 | back-channel: `/oauth2/token`, jwks 프록시, discovery | (Redis code 소비) |
| signing | 8083 | JWT 서명 전담 + jwks 공개. **개인키 독점** | keystore(PKCS12) |
| user-directory | 8084 | 사용자 조회 + credential 검증(bcrypt 를 이 안에 가둠) | users (MySQL) |
| client-registry | 8085 | client 조회 API + Caffeine 캐시(30s) | clients (MySQL) |

핵심 설계 원칙:
- **데이터 소유권 분리** — auth 는 사용자/client DB 를 직접 안 보고 user-directory/client-registry 를 REST 로 호출한다.
- **키 격리** — signing 만 개인키를 가진다. token 이 털려도 개인키는 안 나간다. (KMS 축소판)
- **상태 외부화** — auth 가 만든 code 를 token 이 Redis 로 넘겨받는다. (서비스 경계를 넘는 flow)

## 기동 방법

1. 인프라(gateway nginx + mysql + redis)
   ```bash
   cd docker-compose && docker compose -p microservice-as up -d
   ```
2. 5개 서비스 빌드 (java 21)
   ```bash
   for s in signing user-directory client-registry token auth; do (cd $s && ./gradlew bootJar --no-daemon -q); done
   ```
3. **의존성 순서로** 기동 (signing → user-directory → client-registry → token → auth)
   ```bash
   java -jar signing/build/libs/*.jar          # 8083
   java -jar user-directory/build/libs/*.jar    # 8084
   java -jar client-registry/build/libs/*.jar   # 8085
   java -jar token/build/libs/*.jar             # 8082
   java -jar auth/build/libs/*.jar              # 8081
   ```
   - seed: user / 1111 (user-directory), client `my-client` / `secret` (client-registry, redirectUri `http://127.0.0.1:8080/callback`, scope `openid profile`)
   - 모든 요청은 gateway(http://localhost:9000) 로 보낸다.

## 관통 flow (http/ 참고)

1. `GET /oauth2/authorize?...&code_challenge=...&code_challenge_method=S256` → 미인증이면 로그인으로 redirect
2. `POST /login` (user/1111) → auth 가 user-directory 에 credential 검증 위임, 성공 시 세션 확립(principal = sub)
3. authorize 재요청 → auth 가 client/redirect_uri/PKCE/scope 검증 후 code 발급(Redis 저장) → redirect_uri 로 302
4. `POST /oauth2/token` (Basic my-client:secret, code, code_verifier) → token 이 client 인증 → code 원자 소비 → 바인딩/PKCE 검증 → claim 구성 → signing 에 서명 위임 → access token(JWT)

## 검증된 성공 기준 (e2e, 게이트웨이 경유)

1. 로그인 → code → token → JWT 발급 완주. access token: `iss=http://localhost:9000`, `sub=user-sub-0001`, `aud=my-client`, header `kid=signing-key-2026`, `alg=RS256`.
2. 발급 JWT 를 `/oauth2/jwks`(signing 공개키)로 RS256 서명 검증 통과. jwks 에 개인키(d) 없음.
3. **graceful degradation** — signing 을 내리면 신규 토큰 발급은 `server_error`(OAuth2 포맷)로 실패하지만, **이미 발급된 JWT 는 캐시된 공개키로 계속 검증**된다. (키 격리 + JWT 자가검증의 운영 가치)
4. code 재사용 → `invalid_grant` (Redis GETDEL 원자 소비).
5. PKCE verifier 변조 → `invalid_grant`.
6. 보안 경계: 미등록 redirect_uri / unknown client → **400, redirect 하지 않음(open redirect 방지)**. PKCE 누락 → `invalid_request`. 틀린 client secret → `invalid_client`(401).

## 첫 슬라이스에서 제외 (이후 sub-project)

consent 화면, id token/OIDC userinfo, refresh, introspection, admin 등록 API(현재 seed), **내부 서비스 간 인증**(현재 신뢰 네트워크 가정), Kafka 인증 이벤트 스트림, 관측성/서킷브레이커.

## 알려진 한계 / 추후 개선

- **프록시 헤더(ForwardedHeaderFilter) 미적용** — auth 의 로그인 redirect Location 이 게이트웨이 포트(:9000)를 잃고 `http://localhost/...` 로 나온다. curl e2e 는 절대 URL 로 우회해 통과하지만, 실제 브라우저 flow 는 auth 에 ForwardedHeaderFilter 를 추가해 X-Forwarded-Host(포트 포함)를 반영해야 한다. (production-ready-authorization-server 의 방식 참고)
- **내부 REST 호출 무인증** — /internal/* 은 gateway 라우팅에서만 제외될 뿐 네트워크로 접근 가능하면 무방비다. 서비스 간 인증(API 키/mTLS)이 추후 개선 1순위.
- **AuthorizeController 자동화 테스트 부재** — open redirect 방지/PKCE 강제는 위 e2e(curl)로 검증했으나 MockMvc 통합 테스트로 고정하는 것이 좋다.
- HA(다중 인스턴스), 키 로테이션, purge 등은 production-ready-authorization-server 에서 다룬 주제.

## 설계/계획 문서

- 설계: [docs/superpowers/specs/2026-07-18-microservice-authorization-server-slice1-design.md](../../../../docs/superpowers/specs/2026-07-18-microservice-authorization-server-slice1-design.md)
- 구현 계획: [docs/superpowers/plans/2026-07-18-microservice-authorization-server-slice1.md](../../../../docs/superpowers/plans/2026-07-18-microservice-authorization-server-slice1.md)
