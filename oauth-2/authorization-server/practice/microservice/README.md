# microservice authorization server (슬라이스 1 + 슬라이스 2: OIDC + 슬라이스 3: 토큰 수명 관리 + 슬라이스 4: client 능력 scope 와 client_credentials + 슬라이스 5: back-channel logout · typ 헤더)

- Spring Authorization Server starter **없이** OAuth/OIDC 로직을 직접 구현하고, 하나의 인가 서버를 **10개 독립 마이크로서비스**로 쪼갠 학습 프로젝트다.
- 슬라이스 1 의 목표: **authorization code + PKCE(S256) flow 하나**를 6개 서비스로 관통시키는 것. (완료)
- 슬라이스 2 의 목표: 그 위에 **OIDC(id token 발급 · userinfo · consent 화면/기록 분리)** 를 얹는 것. consent 를 7번째 서비스로 신설하고, token 이 openid scope 요청 시 id token 을 함께 발급하며 `/userinfo` 를 제공한다. (완료)
- 슬라이스 3 의 목표: **토큰 수명 관리(refresh token 회전 · 재사용 탐지 · introspection · revocation)**. refresh token 계열(family)과 폐기 상태를 소유하는 8번째 서비스 token-state 를 신설하고, token 이 `grant_type=refresh_token` / `POST /oauth2/introspect` / `POST /oauth2/revoke` 를 제공한다. (완료, 내부 서비스 간 인증/back-channel logout/admin 등록 API/Kafka 는 여전히 이후 과제)
- 슬라이스 4 의 목표: **client 자체 능력을 scope 로 표현**하는 것. `clients` 에 관리자가 부여하는 `client_scopes` 컬럼을 신설해 사용자 위임 scope(`scopes`)와 분리하고, 사용자 없이 client 가 자기 자신으로서 토큰을 받는 `client_credentials` grant(RFC 6749 4.4)를 추가한다. 그 grant 로 받은 토큰의 `introspect` scope 를 요구하도록 `/oauth2/introspect` 를 client 인증(Basic) 기반에서 **Bearer + scope 기반 protected resource** 로 전환한다. (완료)
- 슬라이스 5 의 목표: **RP-Initiated Logout 1.0** 과 **Back-Channel Logout 1.0**. 슬라이스 1~4는 토큰을 내주는 쪽만 다뤘고 세션이 끝나는 사건은 어디에도 없었다 — 이번 슬라이스가 그 반대쪽, OP 가 세션을 가진 각 RP 에게 logout token 을 보내 로그아웃을 전파하는 쪽을 채운다. `(sid, sub, client_id)` 레지스트리와 발송을 전담하는 9번째 서비스 session 을 신설하고, 검증자가 우리 코드가 아니라 실제 Spring Security 구현이도록 진짜 RP(demo-rp)를 10번째 서비스로 추가한다. 같은 키로 서명되던 access token · id token · logout token 세 타입을 `typ` 헤더로 구분해 강제하며, 그 결과 슬라이스 4까지 남아 있던 한계 "access token 과 id token 을 구분할 수 있는 표식이 없다"를 닫는다. (완료)
- "빅테크는 인가 서버를 내부적으로 여러 서비스로 분해한다"(토큰 발급/로그인 UX/디렉토리/키 관리/동의 기록/토큰 상태/세션 분리, KMS 키 격리)를 축소판으로 재현한다.

## 구도

```
브라우저/클라이언트
   │
   ▼
 gateway (nginx, :9000)  ── 경로로 라우팅, /internal/* 은 외부 비노출
   ├─ /oauth2/authorize, /login, /oauth2/consent, /oauth2/logout ─▶ auth (:8081)   front-channel
   └─ /oauth2/token, /oauth2/jwks, /.well-known, /userinfo,
      /oauth2/introspect, /oauth2/revoke ─▶ token (:8082)  back-channel
                                                 │
   auth ─▶ user-directory (:8084)  로그인 credential 검증 위임
   auth ─▶ client-registry (:8085) client 메타 조회 (+Caffeine 캐시)
   auth ─▶ consent (:8086)         동의 기록 조회/저장 (fail-closed)
   auth ─▶ Redis                   authorization code 저장 (auth:code:{code}, TTL 60s)
                                    + 동의 화면 대기 중 pending (auth:pending:{id}, TTL 300s)
   auth ─▶ Redis                   로그인 세션 (OP 세션 식별자 sid 를 세션 속성에 보관)
   auth ─▶ session (:8088)         GET /oauth2/logout(end_session_endpoint) 처리 중 로그아웃 통지 위임
                                                 │
   token ─▶ Redis                  code 원자적 1회 소비 (GETDEL)
   token ─▶ client-registry        client 인증(bcrypt) 검증
   token ─▶ signing (:8083)        JWT 서명 위임 (access/id/logout token, 개인키는 signing 만 보유, typ 지정)
   token ─▶ user-directory         id token/userinfo 의 profile·email claim 조회
   token ─▶ token-state (:8087)    refresh token 발급/회전/폐기/조회 (외부 비노출, token 만 호출)
   token ─▶ session (:8088)        openid scope 로 id token 발급 시 RP 세션 등록 (sid ↔ client_id, 외부 비노출)
   token, signing ─▶ jwks          공개키 노출
                                                 │
   session ─▶ client-registry      sid 로 걸린 RP 들의 backchannel_logout_uri 조회
   session ─▶ signing               logout token 서명 위임 (typ=logout+jwt)
   session ─▶ RP(들)                logout token form POST (gateway 를 거치지 않고 RP 로 직접, 외부 비노출)
   demo-rp (:8095, 외부 RP)         이 AS 에 실제로 로그인·로그아웃하는 Spring Security 검증자. gateway 라우팅 대상 아님

 MySQL : user-directory(users), client-registry(clients), consent(consents), token-state(refresh_tokens), session(oidc_sessions)
 Redis : authorization code, pending authorization(동의 대기), 로그인 세션(Spring Session)
```

### 아키텍처 모듈 다이어그램

```mermaid
flowchart TB
    browser["브라우저 / 클라이언트"]

    subgraph edge["에지"]
        gateway["gateway<br/>nginx :9000<br/>경로 라우팅"]
    end

    subgraph front["front-channel"]
        auth["auth :8081<br/>로그인 · authorize · code 발급"]
    end

    subgraph back["back-channel"]
        token["token :8082<br/>token · jwks 프록시 · discovery"]
    end

    subgraph internal["내부 서비스 (/internal/*, 외부 비노출)"]
        signing["signing :8083<br/>JWT 서명 전담 · 개인키 독점"]
        userdir["user-directory :8084<br/>사용자 · credential · profile"]
        clientreg["client-registry :8085<br/>client 메타 · Caffeine 캐시"]
        consent["consent :8086<br/>동의 기록 소유"]
        tokenstate["token-state :8087<br/>refresh token 계열 · 폐기 상태 소유"]
    end

    subgraph stores["저장소"]
        mysql[("MySQL<br/>users · clients · consents · refresh_tokens")]
        redis[("Redis<br/>auth code · pending · 세션")]
        keystore["keystore PKCS12<br/>(signing 만 보유)"]
    end

    browser -->|"/oauth2/authorize, /login, /oauth2/consent"| gateway
    browser -->|"/oauth2/token, /oauth2/jwks, /.well-known, /userinfo,<br/>/oauth2/introspect, /oauth2/revoke"| gateway
    gateway --> auth
    gateway --> token

    auth -->|"credential 검증 위임"| userdir
    auth -->|"client 조회"| clientreg
    auth -->|"동의 조회/저장 (fail-closed)"| consent
    auth -->|"code write (TTL 60s) / pending write (TTL 300s)"| redis
    auth -->|"로그인 세션"| redis

    token -->|"client 인증(bcrypt) 조회"| clientreg
    token -->|"code 원자 소비 GETDEL"| redis
    token -->|"서명 위임 (access token · id token) / jwks 프록시"| signing
    token -->|"profile·email claim 조회"| userdir
    token -->|"발급 · 회전 · 폐기 · 조회 (내부 API)"| tokenstate

    userdir --> mysql
    clientreg --> mysql
    consent --> mysql
    tokenstate --> mysql
    signing --- keystore
```

## 서비스별 책임과 소유 데이터

| 서비스 | 포트 | 책임 | 소유 데이터 |
|---|---|---|---|
| gateway | 9000 | nginx 경로 라우팅 (front/back-channel 분리, /internal/* 격리) | 없음 |
| auth | 8081 | front-channel: 로그인, `/oauth2/authorize`(동의 화면 렌더 포함), `/oauth2/consent`(제출), code 발급 | (Redis code, pending, 세션) |
| token | 8082 | back-channel: `/oauth2/token`(authorization_code + refresh_token + client_credentials grant, id token 포함), `/userinfo`, `/oauth2/introspect`(Bearer + `introspect` scope), `/oauth2/revoke`, jwks 프록시, discovery | (Redis code 소비) |
| signing | 8083 | JWT 서명 전담 + jwks 공개. **개인키 독점** | keystore(PKCS12) |
| user-directory | 8084 | 사용자 조회 + credential 검증(bcrypt 를 이 안에 가둠) + profile claim | users (MySQL) |
| client-registry | 8085 | client 조회 API + Caffeine 캐시(30s) | clients (MySQL) |
| consent | 8086 | 동의 기록 조회/저장 API (내부 전용, 화면은 auth 가 렌더) | consents (MySQL) |
| token-state | 8087 | refresh token 계열(family) 발급 · 회전 · 재사용 탐지 · 폐기 · introspection 조회 API (내부 전용, 외부 비노출) | refresh_tokens (MySQL) |
| session | 8088 | OP 세션 레지스트리 소유(`sid` ↔ RP) · 등록(token 이 호출) · 로그아웃 통지 시 각 RP 에 logout token 발송(auth 가 호출) — 내부 전용, gateway 라우팅 대상 아님 | oidc_sessions (MySQL) |
| demo-rp | 8095 | 검증용 실제 RP. `spring-boot-starter-oauth2-client` 로 이 AS 에 `oauth2Login` + RP-Initiated logout(`logout()`) + back-channel logout 수신(`oidcLogout().backChannel()`)을 실제로 구현한다 — 우리가 만든 스텁이 아니라 Spring Security 구현체가 검증자다. gateway 라우팅 대상 아님(브라우저가 8095 로 직접 접근) | 없음(자체 HTTP 세션) |

핵심 설계 원칙:
- **데이터 소유권 분리** — auth 는 사용자/client/동의 DB 를 직접 안 보고 user-directory/client-registry/consent 를 REST 로 호출한다. 마찬가지로 token 은 refresh token 의 상태를 직접 보지 않고 token-state 를 호출한다.
- **키 격리** — signing 만 개인키를 가진다. token 이 털려도 개인키는 안 나간다. (KMS 축소판)
- **상태 외부화** — auth 가 만든 code 를 token 이 Redis 로 넘겨받는다. 동의 화면 왕복 중인 인가 요청(pending)도 화면에는 불투명 id 만 내보내고 실제 값은 Redis 에 둔다. (서비스 경계를 넘는 flow)
- **동의는 fail-closed** — consent 조회가 실패하면(다운 등) "승인 여부 모름"을 "승인함"으로 취급하지 않고 예외를 그대로 전파한다. 동의 없이 토큰이 발급되는 것을 막기 위함이다.
- **회전은 한 번의 호출로 원자화** — token 이 refresh token 의 "유효한가"와 "다음 토큰으로 넘어간다"를 왕복 두 번으로 나누지 않고, token-state 의 `POST /internal/refresh-tokens/rotate` 하나로 위임한다. 판정과 전이를 쪼개면 그 사이 경쟁 창에서 재사용 탐지가 무력해진다.

## `scopes` vs `client_scopes` — client 의 두 scope 컬럼

`clients` 테이블은 이름이 비슷한 두 컬럼을 갖는다. 의미가 다르고, grant 별로 보는 컬럼도 다르다.

| | `scopes` | `client_scopes` |
|---|---|---|
| 누가 부여하나 | **사용자**가 동의 화면에서 위임 | **관리자**가 client 에게 등록 |
| 동의 화면 노출 | 뜬다 (미승인분만 `consent.html` 렌더) | **절대 뜨지 않는다** — 사용자가 위임하는 값이 아니다 |
| 보는 grant | `authorization_code`, `refresh_token` | `client_credentials` |
| 검증 주체 | auth(`authorize`) 가 요청 scope ⊆ `scopes` 확인, consent 가 승인 이력 소유 | token 의 `ClientCredentialsGrantService` 가 요청 scope ⊆ `client_scopes` 확인 |
| seed 예시(`my-client`) | `openid,profile,email,offline_access` | `""` (client_credentials 미등록이라 비어 있어도 무방) |
| seed 예시(`article-api`) | `""` (인가 흐름 자체에 참여하지 않는다) | `introspect` (딱 introspection 호출 능력만) |

**주의.** 두 컬럼이 같은 문자열(`introspect` 등)을 담을 수 있다는 사실이 "값이 같으니 아무 grant 에나 통과시켜도 된다"는 뜻은 아니다. `ClientCredentialsGrantService` 는 오직 `client_scopes` 만 보고 `scopes` 는 쳐다보지 않는다 — 사용자가 없는 grant 에서 "사용자가 위임한 권한"을 내줄 방법이 없기 때문이다. 그래서 `my-client` 처럼 `scopes` 에 `openid` 등이 있어도 `client_credentials` 로는 절대 그 scope 가 나오지 않고, 반대로 `article-api` 의 `client_scopes=introspect` 는 `authorization_code` 로 요청해도(`scope=openid introspect`) `scopes` 에 없으므로 `invalid_scope` 로 거절된다.

## 기동 방법

1. 인프라(gateway nginx + mysql + redis)
   ```bash
   cd docker-compose && docker compose -p microservice-as up -d
   ```
2. 7개 서비스 빌드 (java 21)
   ```bash
   for s in signing user-directory client-registry consent token-state token auth; do (cd $s && ./gradlew bootJar --no-daemon -q); done
   ```
3. **의존성 순서로** 기동 (signing → user-directory → client-registry → consent → token-state → token → auth)
   ```bash
   java -jar signing/build/libs/*.jar          # 8083
   java -jar user-directory/build/libs/*.jar    # 8084
   java -jar client-registry/build/libs/*.jar   # 8085
   java -jar consent/build/libs/*.jar           # 8086
   java -jar token-state/build/libs/*.jar       # 8087
   java -jar token/build/libs/*.jar             # 8082
   java -jar auth/build/libs/*.jar              # 8081
   ```
   - seed: user / 1111 (user-directory, profile 포함: name `Star Rye`, email `starryeye@example.com` 등), client `my-client` / `secret` (client-registry, redirectUri `http://127.0.0.1:8080/callback`, scope `openid profile email offline_access`, grantTypes `authorization_code refresh_token`, clientScopes `""` — client_credentials 미등록이라 이 grant 는 `unauthorized_client` 로 거절된다), client `article-api` / `secret`(client-registry, resource server 역할 — scopes/redirectUris 가 비어 있어 인가 흐름(authorization_code)에는 참여할 수 없고, grantTypes `client_credentials` · clientScopes `introspect` 로 **자기 자신 앞으로 `introspect` scope 의 access token 만 받을 수 있다**. 그 토큰을 Bearer 로 실어야 `/oauth2/introspect` 를 호출할 수 있다)
   - 모든 요청은 gateway(http://localhost:9000) 로 보낸다.
   - 주의. `ddl-auto: update` 라 컨테이너/DB 를 재사용하면 seed 는 "이미 행이 있으면 스킵"으로 동작해 **이전 seed 스키마의 값이 남을 수 있다**(예: client 의 `email` scope, user 의 profile 컬럼이 나중에 추가된 경우). seed 코드와 실제 DB 값이 다르면 `UPDATE` 로 맞추거나 볼륨을 새로 만든다.
   - 주의. 슬라이스 1·2 때부터 존재하던 `my-client` 행이 그 예다. 슬라이스 3 seed 는 `scopes` 에 `offline_access`, `grant_types` 에 `refresh_token` 을 기대하지만, 기존 행은 `ddl-auto: update` 때문에 갱신되지 않는다. `scopes='openid,profile,email,offline_access'`, `grant_types='authorization_code,refresh_token'` 로 `UPDATE` 하고(seed 코드가 신규 client 에 실제로 심는 값과 동일하다 — 임의 값이 아니다) client-registry 를 재기동해 Caffeine 캐시(30s)를 비워야 한다.
   - 주의. `ddl-auto: update` 로 **기존 행이 있는 테이블에 not null 컬럼을 추가**하면(Hibernate 가 `alter table clients add column client_scopes varchar(500) not null` 을 낸다) MySQL 은 명시적 `DEFAULT` 절이 없어도 실패하지 않고 컬럼 타입의 암묵적 기본값(문자열이면 빈 문자열)으로 기존 행을 채운 뒤 성공시킨다. 슬라이스 4 의 `client_scopes` 추가가 그 예다 — 이미 있던 `my-client`·`article-api` 행 모두 `client_scopes=''` 로 채워졌다(`my-client` 는 seed 가 원래 `""` 를 의도하므로 문제 없지만, `article-api` 는 `introspect` 를 기대하므로 값이 어긋난다). 게다가 `article-api` 행은 이번 슬라이스 이전부터 `grant_types` 도 빈 문자열이었다(이전 슬라이스에서는 client 인증만 되면 됐으므로) — 이번 slice 의 seed 는 `client_credentials` 를 기대하므로 이 값도 함께 어긋난다. client-registry 로그에서 컬럼 추가 자체가 실패했는지(구버전 MySQL 등에서는 not null 추가가 에러로 끝날 수 있다) 먼저 확인하고, 성공했다면 `UPDATE` 로 seed 가 의도하는 값(`article-api`: `client_scopes='introspect', grant_types='client_credentials'`)으로 보정한 뒤 client-registry 를 재기동해 Caffeine 캐시(30s)를 비워야 한다.
   - 주의. 위 기동 순서에서 token-state 를 token 보다 먼저 올려야 하는 이유가 하나 더 있다. token 이 보내는 rotate 요청에는 `requestedScope` 필드가 있는데, 구버전 token-state(2필드 계약, `requestedScope` 를 모름)는 이 필드를 역직렬화 시 조용히 무시하고(Spring 기본값 `FAIL_ON_UNKNOWN_PROPERTIES=false`) 축소 없는 평범한 회전으로 `ROTATED` + 저장 scope 전체를 돌려줄 수 있다. token 은 발급 직전에 `effectiveScope` 가 이 저장 scope 의 부분집합인지 방어적으로 재확인하므로 부여된 적 없는 scope 로 access token 이 나가는 일은 없지만(위반 시 `server_error`), refresh grant 자체는 실패한다. token-state 를 먼저 올려야 이 창을 열지 않는다.
   - 주의. client-registry 를 token 보다 먼저 올려야 하는 이유도 같은 종류다. `token/client/ClientInfo` 는 client-registry 응답을 `body(ClientInfo.class)` 로 역직렬화하는데, 구버전 client-registry(`clientScopes` 없는 5필드 계약)가 응답하면 그 필드가 조용히 `null` 로 채워진다. `client_credentials` 요청이 이 `null` 인 `clientScopes` 로 `String.join`/`containsAll` 을 호출하는 순간 `NullPointerException` 이 터져 `500`으로 끝난다(token-state 스큐 때와 달리 이쪽은 아직 코드에 방어가 없다 — fail-closed 라 보안 결함은 아니지만, `grantTypes` 필드도 같은 구조라 한쪽만 방어하면 비대칭이 생긴다. 코드 수정은 이후 과제). 롤링 배포 창에서 client-registry 가 token 보다 늦게 올라오면 이 창이 열린다.

## 관통 flow (http/ 참고)

1. `GET /oauth2/authorize?...&code_challenge=...&code_challenge_method=S256` → 미인증이면 로그인으로 redirect
2. `POST /login` (user/1111) → auth 가 user-directory 에 credential 검증 위임, 성공 시 세션 확립(principal = sub)
3. authorize 재요청 → auth 가 client/redirect_uri/PKCE/scope 검증 후, **consent 에 이미 승인된 scope 를 조회한다**. 미승인 scope 가 있으면 pending 을 Redis 에 저장하고 동의 화면(`consent.html`)을 그대로 렌더한다(별도 GET 핸들러가 아니다 — 동의 화면은 `GET /oauth2/authorize` 자신이 그린다).
4. `POST /oauth2/consent` (pending_id, 체크된 scope) → auth 가 pending 을 소비(1회성)하고, 제출값과 pending 의 교집합만 승인 처리 → consent 에 저장(합집합 병합) → code 발급(Redis 저장, nonce·authTime 포함) → redirect_uri 로 302. 승인 scope 가 하나도 없으면 `error=access_denied` 로 302.
5. 이미 승인된 scope 의 부분집합만 요청하면 3번에서 미승인 scope 가 없으므로 **동의 화면 없이 바로 code** 가 발급된다.
6. `POST /oauth2/token` (Basic my-client:secret, code, code_verifier) → token 이 client 인증 → code 원자 소비 → 바인딩/PKCE 검증 → claim 구성 → signing 에 access token 서명 위임 → **scope 에 `openid` 가 있으면** id token 도 함께 발급한다(nonce·auth_time·at_hash, profile/email scope 면 user-directory 조회 후 name/email 등 claim 추가) → **scope 에 `offline_access` 가 있고 client 의 grantTypes 에 `refresh_token` 도 등록돼 있으면** token-state 에 refresh token 발급을 위임한다 → `{access_token, id_token, refresh_token, ...}`
7. `GET` 또는 `POST /userinfo` (Bearer access token) → token 이 jwks 로 access token 자체 검증 후 scope 에 대응하는 claim 만 돌려준다 (`openid` 없으면 403). `profile`/`email` scope 가 없으면 user-directory 를 조회하지 않는다.
8. client 의 등록 grantTypes 를 authorize/token 양쪽에서 강제한다 — 등록된 grantTypes 에 `authorization_code` 가 없으면 authorize 는 `unauthorized_client` 로 redirect, token 은 `unauthorized_client`(400) 로 거부한다.
9. `POST /oauth2/token` (grant_type=refresh_token, refresh_token) → token 이 client 인증 후 **판정과 전이를 한 번에 묶어** token-state 의 `POST /internal/refresh-tokens/rotate` 를 호출한다. 성공하면 이전 refresh token 은 CONSUMED 로 바뀌고 같은 계열(family)에 새 refresh token 이 발급되며, 새 access token(및 openid scope 면 id token — nonce 없이, auth_time 은 최초 인증 시각 유지)이 함께 나간다. `scope` 파라미터를 함께 보내면 이번 access token 에만 좁혀지고 저장된 refresh 의 scope 는 그대로다. 좁힌 scope 가 저장된 scope 를 벗어나면 token-state 가 회전과 같은 트랜잭션 안에서 이를 검사해 **회전 자체를 하지 않고** `invalid_scope` 를 돌려준다 — 이전 refresh token 이 그대로 살아 있으므로 client 는 같은 토큰으로 scope 를 고쳐 재시도할 수 있다. 회전 후 id token 발급 중 사용자가 삭제된 것으로 확인되면(user-directory 404) `invalid_grant`(500 아니다) — code 교환 경로와 같은 판단이다. token-state 가 빈 본문을 주면(역직렬화 결과 null) 실패로 뭉개지 않고 예외를 던져 `server_error` 로 끝낸다.
10. 이미 소진(CONSUMED)된 refresh token 이 다시 오면 **재사용으로 간주해 계열 전체를 REVOKED 로 폐기**한다 — 공격자가 훔쳐 먼저 회전했든 정상 client 가 나중에 재시도했든, 늦게 온 쪽이 CONSUMED 를 만나 계열이 죽으므로 양쪽 다 재인증으로 떨어진다.
11. `POST /oauth2/revoke` (Basic client:secret, token=refresh_token) → 해당 토큰이 속한 계열 전체를 폐기한다. 존재하지 않는 토큰·다른 client 의 토큰·이미 폐기된 토큰 모두 `200` 으로 동일하게 응답한다(RFC 7009 2.2, 탐색 방지). access token 은 폐기 대상이 아니다.
12. `POST /oauth2/introspect` (Bearer {client_credentials 로 받은 access token}, token) → 이 엔드포인트는 client 인증 대상이 아니라 protected resource 다. `Authorization` 이 `Bearer` 가 아니면(Basic 포함) `401`+`WWW-Authenticate: Bearer`, Bearer 토큰이 무효면 `401 invalid_token`, 유효해도 `introspect` scope 가 없으면 `403 insufficient_scope`. 통과하면 검사 대상 토큰의 JWT 로컬 검증을 먼저 시도하고(서명·`exp`), 성공하면 access token 으로 응답한다. 실패하면(형식이 다르거나 서명 불일치) refresh token 일 수 있으므로 token-state 에 조회를 위임한다. token-state 가 빈 본문을 주면(역직렬화 결과 null) `{"active": false}` 로 내리지 않고 `server_error` 로 끝낸다 — "확인하지 못했다"를 "비활성"으로 말하면 살아있는 토큰을 죽었다고 알리는 셈이다.
13. `POST /oauth2/token` (Basic client:secret, `grant_type=client_credentials`, `scope` 선택) → client 인증 후 `client_credentials` grant 등록 여부만 확인한다(미등록이면 `unauthorized_client`). `scope` 를 생략하면 client 의 `client_scopes` 전부가 기본값이 되고(RFC 6749 3.3), 지정하면 `client_scopes` 의 부분집합인지만 검사한다 — 사용자 위임 `scopes` 는 보지 않는다. 통과하면 access token 만 나간다. refresh token 과 id token 은 만들지 않는다(RFC 6749 4.4.3 SHOULD NOT, 인증한 사용자가 없다). `sub` 는 client_id 이고 `aud` 도 client_id 라 `sub == aud` 다(resource indicator 미사용 — 알려진 한계 참고).
14. **`sid` 관통 경로** — `POST /login` 성공 순간 auth 가 `SessionIdIssuer` 로 OP 세션 식별자 `sid` 를 만들어 HTTP 세션 속성에 저장한다(로그인 한 번에 `sid` 하나, 그 세션에서 authorize 하는 **모든 RP 가 같은 `sid`** 를 공유한다 — `sid` 는 HTTP 세션 id 자체가 아니라 별도 `SecureRandom` 난수다, 실제 세션 id 를 그대로 실으면 세션 탈취 표면이 된다). `GET /oauth2/authorize` 가 code 발급 시(동의 화면을 거쳤든 아니든) 이 `sid` 를 `AuthorizationCodeData` 에 함께 실어 Redis 에 둔다. `POST /oauth2/token` 이 code 를 교환하며 **`openid` scope 로 id token 을 발급하는 순간에만** 그 `sid` 를 id token 의 `sid` claim 에 실어 내보내고, 동시에 session 에 `POST /internal/sessions {sid, sub, clientId}` 로 등록한다 — RP 세션은 auth 가 로그인시킨 순간이 아니라 **token 이 id token 을 내준 순간** 선다. `openid` 없는 교환은 RP 세션을 만들지 않으므로 등록도 하지 않는다.
    - 주의. session 등록 실패는 이 저장소에서 로그인/토큰 발급 경로에 걸리는 유일한 fail-closed 다. 등록이 안 되면 그 RP 는 영원히 로그아웃 통지를 받지 못하고 스스로 알 방법도 없으므로, token 은 실패를 삼키지 않고 `server_error` 로 토큰 발급 전체를 실패시킨다 — discovery 가 `backchannel_logout_supported: true` 를 광고하는 이상 조용히 약속을 깨면 안 되기 때문이다.
    - 주의. refresh 회전(`grant_type=refresh_token`)으로 재발급한 id token 에도 `sid` 가 실린다(슬라이스 7부터 refresh 레코드가 `sid` 를 보관한다) — 다만 그 값은 최초 code 교환 때 등록된 세션을 그대로 승계할 뿐, refresh 경로가 `oidc_sessions` 에 새로 등록하지는 않는다(알려진 한계 참고).
    - 주의. session 의 `register()` 는 메서드 레벨 `@Transactional` 을 두지 않는다. `IDENTITY` 채번에서는 `save` 가 즉시 flush 하므로, 그 메서드를 `@Transactional` 로 감싸면 동시 등록 경쟁으로 `DataIntegrityViolationException` 이 나는 순간 그 바깥 트랜잭션 전체가 rollback-only 로 오염된다 — `catch` 로 예외를 잡아 흡수해도 이미 죽은 트랜잭션이라 메서드가 정상 반환된 뒤 commit 시점에 `UnexpectedRollbackException` 이 새로 터진다. 호출자 관점의 멱등(같은 `(sid, client_id)` 로 여러 번 불러도 항상 성공)을 원하면 그 메서드에서 `@Transactional` 을 빼야 한다.
15. **로그아웃 역추적 경로** — `GET /oauth2/logout`(auth, RP-Initiated Logout 1.0 의 `end_session_endpoint`)가 호출되면 **사용자 식별은 `id_token_hint` 가 아니라 auth 세션 쿠키로 한다**(브라우저가 이미 누구인지 알려주므로). auth 세션에서 `sid` 를 읽어 session 에 `POST /internal/sessions/logout {sid}` 로 통지를 위임한 뒤, 응답을 기다리지 않고 **곧바로** 자신의 세션을 `session.invalidate()` 로 끊는다(Spring Session 이 Redis 에 저장한 세션 행이 실제로 삭제된다 — e2e 로 직접 확인했다, 아래 성공 기준 참고). session 은 `sid` 로 걸린 RP(`client_id`) 목록을 `oidc_sessions` 에서 조회하며 **그 자리에서 행을 지우고**(통지 성공 여부와 무관하게 즉시 — 남기면 다음 로그아웃 때 이미 끝난 세션으로 다시 보낸다), 비동기(`@Async`)로 각 RP 의 `backchannel_logout_uri` 에 logout token 을 form POST 한다. `id_token_hint` 는 오직 `post_logout_redirect_uri` 를 어느 client 기준으로 검증할지 정하는 데만 쓰이고(`exp` 는 검사하지 않는다 — 로그아웃 시점에 id token 이 만료돼 있는 것은 정상이다, 이 저장소에서 만료를 일부러 무시하는 유일한 검증), 힌트가 없거나 위조됐거나 `post_logout_redirect_uri` 가 미등록이면 **리다이렉트만 포기하고 세션 종료·통지는 그대로 수행한다**(fail-open 이 이 슬라이스의 원칙이다 — 슬라이스 2·3의 fail-closed 와 정반대다. 로그아웃을 실패시키면 세션이 살아남는 쪽이 더 위험하기 때문이다).

## API 별 시퀀스 다이어그램

### `GET /oauth2/authorize` + `POST /login` — front-channel (로그인 → code 발급)

```mermaid
sequenceDiagram
    autonumber
    actor B as 브라우저
    participant G as gateway · nginx
    participant A as auth
    participant U as user-directory
    participant C as client-registry
    participant CS as consent
    participant R as Redis

    B->>G: GET /oauth2/authorize?client_id&redirect_uri&scope&state<br/>&code_challenge&code_challenge_method=S256&nonce
    G->>A: proxy
    A-->>B: 302 /login (미인증 — Spring Security)

    B->>G: GET /login
    G->>A: proxy
    A-->>B: 로그인 폼

    B->>G: POST /login (username=user, password=1111)
    G->>A: proxy
    A->>U: POST /internal/users/authenticate {username, password}
    Note over U: bcrypt 검증(해시는 이 안에 가둠)
    U-->>A: 200 {sub, authorities}
    Note over A: 세션 확립 (principal = sub), Redis 세션 저장
    A-->>B: 302 → /oauth2/authorize (saved request)

    B->>G: GET /oauth2/authorize (인증됨)
    G->>A: proxy
    A->>C: GET /internal/clients/{client_id}
    C-->>A: 200 {redirectUris, scopes, grantTypes, ...}
    Note over A: 검증 순서<br/>1. redirect_uri 정확 일치 (실패 → 400, redirect 안 함 = open redirect 방지)<br/>2. grantTypes 에 authorization_code (실패 → unauthorized_client)<br/>3. response_type=code, PKCE(S256) 필수, scope ⊆ 등록 scope
    A->>CS: GET /internal/consents/{sub}/{clientId}
    CS-->>A: 200 {scopes: 이미 승인된 scope}
    Note over A: missing = 요청 scope - 승인 scope.<br/>missing 이 있으면 동의 화면으로 분기 (아래 "동의 흐름" 다이어그램 참고).<br/>이 다이어그램은 missing 이 없는(전부 이미 동의된) 경우를 그린다.
    A->>R: SET auth:code:{code} {clientId, redirectUri, scope, sub, codeChallenge, nonce, authTime} EX 60
    A-->>B: 302 {redirect_uri}?code={code}&state={state}
```

### `GET /oauth2/authorize` (렌더) + `POST /oauth2/consent` (제출) — 동의 흐름

```mermaid
sequenceDiagram
    autonumber
    actor B as 브라우저
    participant G as gateway · nginx
    participant A as auth
    participant CS as consent
    participant R as Redis

    Note over B,A: 로그인은 이미 돼 있고, client/PKCE/scope 검증도 통과한 상태에서 이어진다.
    B->>G: GET /oauth2/authorize (인증됨, scope=openid profile email)
    G->>A: proxy
    A->>CS: GET /internal/consents/{sub}/{clientId}
    CS-->>A: 200 {scopes: 이미 승인된 scope}
    Note over A: missing = 요청 scope - 승인 scope. missing 이 있다.
    A->>R: SET auth:pending:{pendingId} {clientId, redirectUri, scope, sub,<br/>codeChallenge, state, nonce, authTime} EX 300
    Note over A: 동의 화면은 별도 GET 핸들러가 아니라<br/>GET /oauth2/authorize 자신이 렌더한다 (모델에 pendingId·missing scope 담아 consent.html 반환)
    A-->>B: 200 consent.html (pending_id, requestedScopes=missing, grantedScopes)

    B->>G: POST /oauth2/consent (pending_id, scope=선택한 것들, _csrf)
    G->>A: proxy
    A->>R: GETDEL auth:pending:{pendingId} (원자적 1회 소비)
    R-->>A: pending 데이터
    Note over A: approved = 제출 scope ∩ pending.scope (폼 조작으로 상위 scope 승인 불가)<br/>approved 가 비어 있으면 error=access_denied 로 302 하고 종료
    A->>CS: POST /internal/consents {sub, clientId, scopes=approved}
    Note over CS: 기존 기록과 합집합으로 병합 (추가 동의가 이전 동의를 지우지 않는다)
    CS-->>A: 200 {scopes: 병합된 전체 승인 scope}
    A->>CS: GET /internal/consents/{sub}/{clientId} (재조회)
    CS-->>A: 200 {scopes: 병합된 전체 승인 scope}
    Note over A: finalScopes = approved ∪ (재조회 granted ∩ pending.scope)<br/>(이번에 새로 승인한 것만으로 code 를 만들면 기승인 scope 가 code 에서 누락된다)
    A->>R: SET auth:code:{code} {..., scope=finalScopes, nonce, authTime} EX 60
    A-->>B: 302 {redirect_uri}?code={code}&state={state}
```

### `POST /oauth2/token` — back-channel (code → access token, openid scope 면 id token 도 함께)

```mermaid
sequenceDiagram
    autonumber
    actor CL as 클라이언트
    participant G as gateway · nginx
    participant T as token
    participant C as client-registry
    participant R as Redis
    participant S as signing
    participant U as user-directory

    CL->>G: POST /oauth2/token<br/>Basic(client_id:secret), grant_type=authorization_code,<br/>code, redirect_uri, code_verifier
    G->>T: proxy
    Note over T: Basic 파싱 (잘못된 base64 → invalid_client)
    T->>C: GET /internal/clients/{client_id}
    C-->>T: 200 {clientSecretHash, grantTypes, ...}
    Note over T: client 인증 bcrypt.matches (실패 → invalid_client 401)<br/>grantTypes 에 authorization_code (실패 → unauthorized_client 400)
    T->>R: GETDEL auth:code:{code}  (원자적 1회 소비)
    R-->>T: {clientId, redirectUri, scope, sub, codeChallenge, nonce, authTime}
    Note over T: code 없음/재사용 → invalid_grant 400<br/>바인딩 검증(clientId, redirect_uri 일치) → 불일치 시 invalid_grant<br/>PKCE: S256(code_verifier) == codeChallenge → 불일치 시 invalid_grant<br/>claim 구성 iss/sub/aud/iat/exp/scope
    T->>S: POST /internal/sign {claims} (access token claims)
    Note over S: 개인키로 RS256 서명, kid = 키 alias<br/>(signing 이 헤더 전적 소유)
    S-->>T: {jwt} (access token)

    opt scope 에 openid 포함
        opt scope 에 profile 또는 email 포함
            T->>U: GET /internal/users/{sub}
            alt 조회 성공
                U-->>T: 200 {name, nickname, preferredUsername, email, emailVerified}
            else 404 (사용자 삭제 — 확정된 부재)
                Note over T: id token 발급 중단 → invalid_grant 400.<br/>존재하지 않는 주체에 대한 인증 주장을 서명할 수 없다.<br/>code 는 이미 소비돼 재시도로 우회되지 않는다
            else 일시 장애 (연결 실패·5xx — 존재 여부 미확정)
                Note over T: 프로필 claim 없이 id token 발급 계속 (필수 claim 만으로도 유효)
            end
        end
        Note over T: id token claim 구성<br/>iss/sub/aud/iat/exp/auth_time/at_hash(access token SHA-256 좌측 절반)<br/>+ nonce(요청에 있었으면) + profile/email claim
        T->>S: POST /internal/sign {claims} (id token claims)
        S-->>T: {jwt} (id token)
    end

    T-->>CL: 200 {access_token, token_type=Bearer, expires_in, scope, id_token}
```

### `GET /oauth2/jwks` — 공개키 노출 (프록시)

```mermaid
sequenceDiagram
    autonumber
    actor V as 검증자(resource server 등)
    participant G as gateway · nginx
    participant T as token
    participant S as signing

    V->>G: GET /oauth2/jwks
    G->>T: proxy
    T->>S: GET /oauth2/jwks
    Note over S: 공개키만 노출(개인키 d 없음)
    S-->>T: JWKSet {keys:[{kid, kty, n, e}]}
    T-->>V: JWKSet
    Note over V: 캐시해두면 signing 이 다운돼도<br/>기존 JWT 검증은 계속된다 (graceful degradation)
    Note over T: 주의. 이 degradation 은 jwks 를 캐시하는 검증자에게만 성립한다.<br/>이 서버의 /userinfo 는 캐시가 없어 signing 이 죽으면 500 이다 (알려진 한계 참고)
```

### `GET`/`POST /userinfo` — access token 으로 scope 에 대응하는 claim 조회

OIDC Core 5.3.1 이 GET·POST 를 모두 요구하므로(MUST) 둘 다 받는다. RFC 6750 §3.1 은 토큰 전달 방식으로
Authorization 헤더 / form-encoded body / URI 쿼리 셋을 정의하는데, 이 서버는 그 중 **헤더**와 **form-encoded
POST 의 `access_token` 파라미터**만 받는다. **URI 쿼리의 `access_token` 은 GET/POST 를 가리지 않고 받지 않는다**
(쿼리스트링은 프록시·서버 접근 로그와 Referer 헤더에 그대로 남는다) — 쿼리에 실려 있으면 헤더 유무와 무관하게
유효한 전달로 인정하지 않는다. 헤더+폼 또는 헤더+쿼리를 동시에 쓰면 400 `invalid_request`, 쿼리만 있으면(폼
여부 무관) 401, 아무 것도 없으면 401 이다.
주의. form-encoded POST 라도 `access_token` 이 쿼리스트링에도 실려 있으면 서블릿이 쿼리·폼 파라미터를 이름으로
병합하므로 `@RequestParam` 값만으로는 폼에서 온 값인지 쿼리에서 온 값인지 구분할 수 없다. `getQueryString()`
의 raw 쿼리를 직접 파싱해 쿼리에 `access_token` 이 있는지부터 판정해야 한다.

```mermaid
sequenceDiagram
    autonumber
    actor CL as 클라이언트
    participant G as gateway · nginx
    participant T as token
    participant S as signing
    participant U as user-directory

    CL->>G: GET /userinfo (Authorization: Bearer {access_token})<br/>또는 POST /userinfo (form: access_token={access_token})
    G->>T: proxy
    Note over T: 토큰 추출: Authorization 헤더 / form-encoded POST 의 access_token 만 받는다<br/>쿼리의 access_token 은 메서드 무관 불허 — 헤더와 동시면 400 invalid_request, 쿼리만이면 401<br/>헤더+폼 동시도 400 invalid_request / 아무 것도 없으면 401 (WWW-Authenticate: Bearer)
    T->>S: GET /oauth2/jwks
    Note over T: 매 요청마다 조회한다 (캐시 없음 — 알려진 한계 참고)
    alt jwks 확보 성공
        S-->>T: JWKSet
    else signing 장애 (연결 실패·5xx)
        Note over T: 500 server_error 로 끝낸다.<br/>"키를 못 구했다"를 401 invalid_token 으로 내면<br/>RP 가 멀쩡한 토큰을 폐기하고 재인증을 돌린다
    end
    Note over T: 서명 검증(jwks) + exp + iss 확인. 실패(미공개 kid 포함)<br/>→ 401 (WWW-Authenticate: Bearer error="invalid_token")
    Note over T: scope 에 openid 없으면 → 403 (WWW-Authenticate: Bearer error="insufficient_scope")

    opt scope 에 profile 또는 email 포함
        T->>U: GET /internal/users/{sub}
        alt 조회 성공
            U-->>T: 200 {name, nickname, preferredUsername, email, emailVerified}
        else 404 (사용자 삭제 — 확정된 부재)
            Note over T: 401 invalid_token. 주체가 사라진 토큰은 실효다
        else 일시 장애 (연결 실패·5xx — 존재 여부 미확정)
            Note over T: 프로필 없이 sub 만으로 200 응답 (표준 필수 claim 인 sub 까지 막지 않는다)
        end
    end
    Note over T: scope 에 profile 있으면 name/nickname/preferred_username<br/>scope 에 email 있으면 email/email_verified (email 값이 없으면 둘 다 생략)<br/>openid 만 있으면 sub 뿐이고 user-directory 를 호출하지도 않는다<br/>매핑은 id token 과 같은 ProfileClaimMapper 를 쓴다
    T-->>CL: 200 {sub, [name, nickname, preferred_username], [email, email_verified]}
```

### `POST /oauth2/token` (`grant_type=refresh_token`) — refresh 회전

```mermaid
sequenceDiagram
    autonumber
    actor CL as 클라이언트
    participant G as gateway · nginx
    participant T as token
    participant C as client-registry
    participant TS as token-state
    participant S as signing
    participant U as user-directory

    CL->>G: POST /oauth2/token<br/>Basic(client_id:secret), grant_type=refresh_token, refresh_token, [scope]
    G->>T: proxy
    T->>C: GET /internal/clients/{client_id}
    C-->>T: 200 {clientSecretHash, grantTypes, ...}
    Note over T: client 인증(bcrypt) → 실패 시 invalid_client 401<br/>grantTypes 에 refresh_token 없으면 → unauthorized_client
    T->>TS: POST /internal/refresh-tokens/rotate {refreshToken, clientId, requestedScope}
    Note over TS: 판정과 전이를 한 트랜잭션 · 한 번의 호출로 끝낸다(왕복을 쪼개면 경쟁 창이 생긴다).<br/>계열 전체를 먼저 잠그고(findByFamilyIdForUpdate) 그 안에서 대상 행을 찾아 판정한다.<br/>requestedScope 가 저장 scope 를 벗어나는지도 다른 거절 사유 뒤, 전이 직전에 같은 트랜잭션 안에서 확인한다(RFC 6749 6).<br/>ACTIVE 고 scope 도 범위 안이면 CONSUMED 로 전이하고 같은 family_id 로 새 행을 발급한다.
    alt 회전 성공 (ROTATED)
        TS-->>T: 200 {status=ROTATED, sub, scope, authTime, refreshToken(새 값), expiresAt}
        Note over T: 요청 scope 는 이번 access token 에만 좁혀지고 저장된 refresh 의 scope 는 그대로 유지된다.
        T->>S: POST /internal/sign {claims} (access token)
        S-->>T: {jwt}
        opt scope 에 openid 포함
            Note over T: nonce 는 싣지 않는다(재발급 토큰에 실으면 리플레이 방어가 깨진다).<br/>auth_time 은 최초 인증 시각을 그대로 유지한다(OIDC Core 12.2).
            opt profile 또는 email scope 포함
                T->>U: GET /internal/users/{sub}
                alt 조회 성공
                    U-->>T: 200 {name, nickname, preferredUsername, email, emailVerified}
                else 404 (사용자 삭제 — 확정된 부재)
                    Note over T: id token 발급 중단 → invalid_grant 400 (500 아니다).<br/>code 교환 경로와 같은 판단 — 존재하지 않는 주체에 대한 인증 주장을 만들 수 없다.<br/>회전 자체는 이미 끝나 새 refresh token 이 나갔지만, 이 grant 응답은 무효로 본다.
                else 일시 장애 (연결 실패·5xx — 존재 여부 미확정)
                    Note over T: 프로필 claim 없이 id token 발급 계속
                end
            end
            T->>S: POST /internal/sign {claims} (id token)
            S-->>T: {jwt}
        end
        T-->>CL: 200 {access_token, [id_token], refresh_token(새 값), ...}
    else scope 초과 (SCOPE_EXCEEDED)
        TS-->>T: 200 {status=SCOPE_EXCEEDED}
        Note over TS,T: 토큰은 멀쩡하고 요청이 잘못된 경우라 아무 상태도 바꾸지 않았다 — 회전이 일어나지 않았다.<br/>이전 refresh token 이 그대로 살아 있으므로 client 는 같은 토큰으로 scope 를 고쳐 재시도할 수 있다.
        T-->>CL: 400 invalid_scope
    else 회전 거부 (NOT_FOUND · CLIENT_MISMATCH · REVOKED · EXPIRED · REUSE_DETECTED)
        TS-->>T: 200 {status=그 사유}
        Note over T: 사유를 그대로 노출하지 않고 전부 invalid_grant 로 뭉갠다.<br/>"이미 소진됐다"와 "그런 토큰 없다"를 구분해 주면 탐색을 돕는다. 사유는 로그에만 남긴다.
        T-->>CL: 400 invalid_grant
    end
    Note over T: token-state 가 빈 본문을 주면(역직렬화 결과 null) "비활성/거절"이 아니라 "확인하지 못했다"다.<br/>invalid_grant 로 내리면 하위 서비스 장애를 client 잘못으로 돌리게 되므로, 예외를 던져 500 server_error 로 끝낸다.
```

### 소진된 refresh token 재사용 — 재사용 탐지 → 계열 폐기

```mermaid
sequenceDiagram
    autonumber
    actor A as 정상 client (또는 훔친 토큰의 공격자)
    participant T as token
    participant TS as token-state

    Note over A,TS: 앞의 회전 다이어그램으로 refresh token 하나가 이미 CONSUMED 로 전이됐다고 가정한다.<br/>(정상 client 의 응답 유실 후 재시도든, 탈취한 토큰으로 뒤늦게 시도한 공격자든 이 시점부터는 구분되지 않는다)

    A->>T: POST /oauth2/token grant_type=refresh_token<br/>refresh_token={이미 CONSUMED 인 토큰}
    T->>TS: POST /internal/refresh-tokens/rotate
    Note over TS: familyId 로 계열 전체를 findByFamilyIdForUpdate 로 잠근 뒤(잠금 순서는 항상<br/>"계열 → 대상 행" 하나만 쓴다 — 반대 순서가 하나라도 있으면 revoke 와 교착 가능),<br/>대상 행이 CONSUMED 임을 확인한다.
    Note over TS: 재사용으로 판정 → 방금 잠근 계열의 모든 행을 REVOKED / REUSE_DETECTED 로 전이한다.<br/>정상 client 와 공격자 중 누가 먼저 회전했든, 다른 쪽이 이 경로를 타므로<br/>계열에 남아있던 "현재 유효한" 토큰까지 포함해 계열 전체가 죽는다.
    TS-->>T: 200 {status=REUSE_DETECTED}
    T-->>A: 400 invalid_grant

    Note over A,TS: 계열의 다른 행(정상 client 가 직전 회전으로 받아 아직 쓰지 않은 최신 refresh token)으로<br/>다시 회전을 시도해도 이미 REVOKED 이므로 마찬가지로 invalid_grant 다. 재인증(authorization_code)부터 다시 해야 한다.
```

### `POST /oauth2/token`(`grant_type=client_credentials`) → `POST /oauth2/introspect` — client 능력 토큰 획득 후 introspection (슬라이스 4)

```mermaid
sequenceDiagram
    autonumber
    actor RS as article-api (resource server)
    participant G as gateway · nginx
    participant T as token
    participant C as client-registry
    participant S as signing
    participant TS as token-state

    RS->>G: POST /oauth2/token<br/>Basic(article-api:secret), grant_type=client_credentials, scope=introspect
    G->>T: proxy
    T->>C: GET /internal/clients/article-api
    C-->>T: 200 {clientSecretHash, grantTypes=[client_credentials], clientScopes=[introspect]}
    Note over T: client 인증(bcrypt) 실패 → invalid_client 401<br/>ClientCredentialsGrantService.grant — grantTypes 에 client_credentials 없으면 unauthorized_client<br/>scope 생략 시 clientScopes 전부가 기본값(RFC 6749 3.3), 지정 시 clientScopes 의 부분집합인지만 검사(벗어나면 invalid_scope) — scopes(사용자 위임)는 보지 않는다
    T->>S: POST /internal/sign {iss, sub=client_id, aud=client_id, iat, exp, scope=[introspect]}
    S-->>T: {jwt}
    Note over T: refresh_token · id_token 은 만들지 않는다(RFC 6749 4.4.3 SHOULD NOT — 인증한 사용자가 없다)
    T-->>RS: 200 {access_token, token_type=Bearer, expires_in, scope=introspect}
    Note over RS: sub == aud == article-api (RFC 9068). resource indicator(RFC 8707) 미사용 — 알려진 한계 참고

    RS->>G: POST /oauth2/introspect<br/>Bearer {방금 받은 access token}, token={검사할 토큰}
    G->>T: proxy
    Note over T: Authorization 이 Bearer 가 아니면(Basic 포함) → 401 WWW-Authenticate: Bearer
    T->>T: AccessTokenVerifier.verify(호출자 토큰) — jwks 서명 · exp 검증
    alt 호출자 토큰 무효
        T-->>RS: 401 WWW-Authenticate: Bearer error="invalid_token"
    else 유효하지만 introspect scope 없음
        T-->>RS: 403 WWW-Authenticate: Bearer error="insufficient_scope"
    else 유효 + introspect scope 보유
        T->>T: AccessTokenVerifier.verify(검사 대상 토큰) 시도<br/>(JWT 파싱 → jwks 서명 검증 → exp/iss 확인)
        alt JWT 로 유효 (access token)
            Note over T: access token 은 여기서 끝난다 — token-state 를 조회하지 않는다.<br/>이 서버는 폐기를 refresh 한정으로 정했으므로 활성 여부는 서명·exp 만으로 결정된다.
            T-->>RS: 200 {active:true, sub, client_id, scope, exp, iat, iss, token_type=Bearer}
        else JWT 파싱/검증 실패 (형식이 다르거나 signing 이 모르는 kid 등)
            Note over T: refresh token 일 수 있으므로 token-state 에 묻는다.
            T->>TS: POST /internal/refresh-tokens/introspect {refreshToken}
            alt 존재 + ACTIVE + 미만료
                TS-->>T: 200 {active:true, sub, clientId, scope, exp, iat}
                Note over T: token_type 은 넣지 않는다 — refresh token 은 리소스 접근에 쓰이지 않는다.
                T-->>RS: 200 {active:true, sub, client_id, scope, exp, iat, iss}
            else 없음 · REVOKED · CONSUMED · 만료
                TS-->>T: 200 {active:false}
                Note over T: 사유(만료 · 폐기 · 애초에 존재하지 않음)를 구분하지 않는다(RFC 7662 2.2).<br/>구분해 주면 토큰을 쥔 쪽이 그 토큰의 내력을 알아낼 수 있다.
                T-->>RS: 200 {active:false}
            else 빈 본문(역직렬화 결과 null)
                TS-->>T: 200 (본문 없음)
                Note over T: "비활성" 이 아니라 "확인하지 못했다" 다. {active:false} 로 내리면<br/>살아있는 토큰을 죽었다고 말하는 셈이라 resource server 가 멀쩡한 요청을 거절한다.<br/>예외를 던져 500 server_error 로 끝낸다.
                T-->>RS: 500 server_error
            end
        end
    end
```

주의. `POST /oauth2/revoke` 는 여전히 Basic client 인증이다(비대칭이 의도적이다) — revoke 는 "자기 토큰을 폐기"라 소유자 확인(client 인증)이 맞고, introspect 는 "남의 토큰을 검사"라 별도로 부여된 능력(scope)이 맞다.

### 로그인 시 `sid` 등록 — id token 발급 순간 RP 세션이 선다 (슬라이스 5)

```mermaid
sequenceDiagram
    autonumber
    actor B as 브라우저
    participant G as gateway · nginx
    participant A as auth
    participant T as token
    participant S as signing
    participant SS as session

    Note over B,A: 로그인(POST /login) 성공 순간 auth 가 SessionIdIssuer 로 sid 를 만들어 세션 속성에 저장한다.<br/>(이미 있으면 재사용 — 로그인 한 번에 sid 하나, 그 세션의 모든 RP 가 공유한다)
    B->>G: GET /oauth2/authorize?...&scope=openid ...
    G->>A: proxy
    Note over A: code 발급 시 AuthorizationCodeData 에 sid 를 함께 싣는다(Redis)
    A-->>B: 302 redirect_uri?code=...

    B->>G: POST /oauth2/token (code, code_verifier)
    G->>T: proxy
    Note over T: code 소비 → sid 포함 데이터 회수
    T->>S: POST /internal/sign {claims, typ=at+jwt} (access token)
    S-->>T: {jwt}
    opt scope 에 openid 포함
        Note over T: id token claim 에 sid 를 싣는다 (openid 요청일 때만 발급되는 경로)
        T->>S: POST /internal/sign {claims+sid, typ=JWT} (id token)
        S-->>T: {jwt}
        T->>SS: POST /internal/sessions {sid, sub, clientId}
        Note over SS: 멱등 등록(같은 (sid, clientId) 로 이미 있으면 스킵).<br/>주의. existsBySidAndClientId 선검사만으로는 동시 등록 경쟁(TOCTOU)을 막지 못한다 —<br/>uk_sid_client 유니크 제약이 최종 방어선이고, 진 쪽 트랜잭션의 DataIntegrityViolationException 을<br/>흡수해야 호출자 관점에서도 멱등이 된다.
        SS-->>T: 200
        Note over T: 등록 실패는 fail-closed다 — 삼키지 않고 server_error 로 토큰 발급 전체를 실패시킨다.
    end
    T-->>B: 200 {access_token, id_token(sid 포함), ...}
```

### `GET /oauth2/logout` — OP 세션 종료와 logout token 발송 (슬라이스 5)

```mermaid
sequenceDiagram
    autonumber
    actor B as 브라우저
    participant G as gateway · nginx
    participant A as auth
    participant SS as session
    participant CR as client-registry
    participant S as signing
    participant RP as RP (예: demo-rp)

    Note over B,A: auth 세션 쿠키만으로 호출한다(OP 를 직접 로그아웃).<br/>RP 가 시작하는 로그아웃과 섞으면 이 경로 자체가 검증되지 않는다 — RP 의 LogoutFilter 가<br/>자기 세션을 먼저 로컬에서 지우기 때문에, back-channel 이 죽어 있어도 통과처럼 보인다.
    B->>G: GET /oauth2/logout [+id_token_hint, +post_logout_redirect_uri]
    G->>A: proxy
    Note over A: id_token_hint 는 사용자 식별에 쓰지 않는다(auth 세션 쿠키로 이미 안다).<br/>post_logout_redirect_uri 를 어느 client 기준으로 검증할지 정하는 데만 쓴다. exp 는 무시한다.
    A->>SS: POST /internal/sessions/logout {sid}  (auth 세션에서 읽은 sid)
    Note over SS: consumeForLogout — 조회 후 즉시 삭제(@Transactional). 통지 성공 여부와 무관하게<br/>oidc_sessions 행을 먼저 지운다. 남기면 다음 로그아웃 때 이미 끝난 세션으로 다시 보낸다.
    SS-->>A: 200 (즉시 응답. 발송은 이 응답 이후 비동기로 이어진다)
    Note over A: session.invalidate() 로 auth 자신의 세션을 끊는다 (Redis 에서 실제 삭제 — e2e 로 확인)
    A-->>B: 302 post_logout_redirect_uri(검증 통과 시) 또는 200 자체 완료 페이지

    Note over SS,RP: 여기부터 비동기(@Async). RP 마다 독립 시도 — 한 RP 의 실패가 나머지를 막지 않는다.
    SS->>CR: GET /internal/clients/{clientId}  (sid 로 걸린 RP 각각)
    CR-->>SS: 200 {backchannelLogoutUri, ...}
    Note over SS: backchannelLogoutUri 가 없는 client(예: article-api)는 건너뛴다
    SS->>S: POST /internal/sign {claims, typ=logout+jwt} (logout token)
    S-->>SS: {jwt}
    SS->>RP: POST {backchannel_logout_uri} (form, logout_token=...)
    Note over RP: Spring Security 의 OidcBackChannelLogoutFilter 가 검증<br/>(iss 정확 일치·aud·iat·jti·events 키·sub 또는 sid·nonce 없음)한 뒤<br/>그 sid 로 등록된 자신의 세션을 SecurityContextLogoutHandler 로 무효화한다.
    RP-->>SS: 200 (실패해도 재시도하지 않는다 — best-effort, 로그만 남긴다)
```

### logout token claim 계약 (Spring Security `OidcBackChannelLogoutTokenValidator` 기준)

Spring Security 6.4.5 의 `OidcBackChannelLogoutTokenValidator` 바이트코드로 확인한, RP 가 실제로 거부하는 조건이다.

| claim | 검증기가 요구하는 것 | 이 서버가 보내는 값 |
|---|---|---|
| `iss` | null 금지 + RP 가 discovery 로 알아낸 issuer 와 **문자열 정확 일치** | `http://localhost:9000` |
| `aud` | null 금지 + 그 RP 의 `client_id` 포함 | 대상 RP 의 `client_id` |
| `iat` | null 금지 | 발송 시각 |
| `jti` | null 금지 | `UUID.randomUUID()` |
| `events` | null 금지 + `http://schemas.openid.net/event/backchannel-logout` 키 포함 | `{"http://schemas.openid.net/event/backchannel-logout": {}}` |
| `sub` / `sid` | **둘 다 null 이면 거부** | 둘 다 싣는다 |
| `nonce` | **있으면 거부** (id token 검증 경로로 치환되는 것을 막는다) | 싣지 않는다 |
| `exp` | 검증하지 않는다 | 싣지 않는다 |

전송 형식: `POST {backchannel_logout_uri}`, `Content-Type: application/x-www-form-urlencoded`, 파라미터 이름 `logout_token`. Spring Security RP 의 기본 수신 경로는 `/logout/connect/back-channel/{registrationId}` 다.

주의. `iss` 정확 일치가 이 저장소의 기존 한계(ForwardedHeaderFilter 미적용)와 정면으로 만난다. auth 의 리다이렉트가 게이트웨이 포트(:9000)를 잃는 현상 자체는 logout token 발송에 관여하지 않지만(발송은 signing 이 `my.issuer` 설정값을 그대로 서명하므로), 그 설정값이 한 글자라도 discovery 의 `issuer` 와 어긋나면 RP 는 logout token 을 통째로 거부한다. e2e 로 `http://localhost:9000` 문자열 일치를 직접 확인했다(아래 성공 기준 9·10번).

## 검증된 성공 기준 (e2e, 게이트웨이 경유)

### 슬라이스 1 (authorization code + PKCE)

1. 로그인 → code → token → JWT 발급 완주. access token: `iss=http://localhost:9000`, `sub=user-sub-0001`, `aud=my-client`, header `kid=signing-key-2026`, `alg=RS256`.
2. 발급 JWT 를 `/oauth2/jwks`(signing 공개키)로 RS256 서명 검증 통과. jwks 에 개인키(d) 없음.
3. **graceful degradation** — signing 을 내리면 신규 토큰 발급은 `server_error`(OAuth2 포맷)로 실패하지만, **이미 발급된 JWT 는 캐시된 공개키로 계속 검증**된다. (키 격리 + JWT 자가검증의 운영 가치)
   - 주의. 이것은 공개키를 미리 받아둔 **외부 검증자** 기준이다. 이 서버의 `/userinfo` 는 jwks 캐시가 없어 signing 장애 시 500 `server_error` 다 — e2e 로 확인된 내용은 아래 슬라이스 2 항목 16 참고(알려진 한계이기도 하다).
4. code 재사용 → `invalid_grant` (Redis GETDEL 원자 소비).
5. PKCE verifier 변조 → `invalid_grant`.
6. 보안 경계: 미등록 redirect_uri / unknown client → **400, redirect 하지 않음(open redirect 방지)**. PKCE 누락 → `invalid_request`. 틀린 client secret → `invalid_client`(401).

### 슬라이스 2 (OIDC: id token · userinfo · consent)

7. **동의 화면 노출** — 미승인 scope(`openid profile email`) 로 authorize 하면 `GET /oauth2/authorize` 가 `consent.html`(pending_id 히든 필드 포함)을 그대로 렌더한다. 제출(`POST /oauth2/consent`)하면 code 가 발급된다.
8. **id token** — 발급된 id token 에 `iss=http://localhost:9000`, `sub=user-sub-0001`, `aud=my-client`, 요청한 `nonce` 그대로 반영, `at_hash`(access token SHA-256 좌측 절반의 BASE64URL)가 실측 재계산값과 일치. jwks 공개키로 RS256 서명 검증 PASS(수동 modpow 재계산으로 확인).
9. **재인가 시 동의 생략** — 이미 전부 승인된 scope 의 부분집합(또는 동일 집합)으로 재요청하면 동의 화면 없이 바로 `302 Location: ...?code=...`.
10. **userinfo scope 필터링** — `openid profile email` 토큰은 `sub/name/nickname/preferred_username/email/email_verified` 전부 응답. `openid` 만 있는 토큰은 `sub` 만 응답(profile/email claim 없음).
11. **동의 거부** — 동의 화면에서 scope 를 하나도 체크하지 않고 제출하면 redirect 에 `error=access_denied`.
12. **무효 토큰** — `userinfo` 에 형식이 깨진 토큰(`not.a.token`)을 보내면 `401` + `WWW-Authenticate: Bearer error="invalid_token"` (RFC 6750).
13. **회귀(code 재사용)** — 슬라이스 2 변경 이후에도 authorization code 재사용은 여전히 `invalid_grant`.
14. **`POST /userinfo` (OIDC Core 5.3.1 MUST) + RFC 6750 §3.1 토큰 전달 방식** — (a) `GET` + Authorization 헤더 → `200`. (b) `POST` + Authorization 헤더 → `200`(이전에는 `405`). (c) `POST` + form-encoded body 의 `access_token` → `200` + 전체 claim. (d) 헤더+폼 동시 → `400 invalid_request`. (e) URI 쿼리의 `access_token` — 쿼리만 있으면(단독이든 form-encoded POST 와 병행이든) `401`, 쿼리+헤더 동시면 `400 invalid_request`. 쿼리는 GET/POST 를 가리지 않고 유효한 전달로 인정하지 않는다.
    - 파라미터 이름 경계 판정(`my_access_token=` 을 `access_token` 으로 오탐하지 않는다)은 단위 테스트로 고정한다.
15. **user-directory 사용자 부재 → 401** — `users` 테이블에서 발급받은 access token 의 sub 에 해당하는 행을 삭제한 뒤 같은 토큰으로 `/userinfo` 를 호출하면 `401` + `WWW-Authenticate: Bearer error="invalid_token"` (404 는 "확정된 부재"이므로 degrade 가 아니라 실효 처리). 확인 후 행을 원복.
16. **signing 장애 시 `/userinfo` → 500 `server_error`** — signing 프로세스를 내린 채 `/userinfo` 를 호출하면 `401` 이 아니라 `500` + `{"error":"server_error"}`. "키를 못 구했다"를 401 로 내면 RP 가 멀쩡한 토큰을 폐기하고 재인증을 돌리므로 신중히 구분해야 한다는 설계 의도가 실제로 지켜짐을 확인. signing 재기동 후 `/userinfo` 가 다시 `200` 으로 복귀하는 것도 함께 확인.

### 슬라이스 3 (토큰 수명 관리: refresh 회전 · 재사용 탐지 · introspection · revocation)

17. **offline_access 동의 → refresh 발급** — `scope=openid profile offline_access` 로 동의 화면을 거치면 화면에 `offline_access` 체크박스가 렌더되고, 승인 후 토큰 응답에 `refresh_token` 이 포함된다(`scope: "openid profile offline_access"`).
18. **offline_access 미요청 시 refresh 없음** — 같은 client 로 `scope=openid profile` 만 요청하면(이미 승인된 scope 의 부분집합이라 동의 화면 생략) 토큰 응답에 `refresh_token` 이 없다.
19. **refresh grant 회전 + id token 재발급 규칙** — `grant_type=refresh_token` 으로 교환하면 새 `access_token`·새 `refresh_token` 이 나오고, 새 id token 은 `nonce` 를 싣지 않으며, `auth_time` 은 최초 발급분과 동일하고, `at_hash` 는 새 access token 을 SHA-256 좌측 절반으로 재계산한 값과 일치한다.
20. **scope 축소·초과 요청**(rotate wire 계약에 `requestedScope`/`SCOPE_EXCEEDED` 가 추가된 뒤 재검증) — 저장 scope 의 부분집합으로 `scope` 파라미터를 보내면 그 access token 에만 좁혀지고(`scope` 응답 필드가 요청값과 일치), 다음 회전에서 `scope` 없이 요청하면 저장된 원래 scope 전체로 복귀한다(저장 scope 불변). 저장 범위를 벗어나는 `scope`(예: 승인되지 않은 `admin`)를 보내면 `400 invalid_scope` 이고 회전이 일어나지 않으며, **같은 refresh token 으로 올바른 scope 를 재시도하면 성공한다**(이전 토큰이 소진되지 않았다는 뜻).
21. **재사용 탐지 → 계열 폐기** — 이미 소진(회전에 쓰인) refresh token 을 다시 제출하면 `invalid_grant`. 이 시점까지 발급된 계열의 행(초기 발급분 + 그때까지의 회전분) 전부가 DB 상 `REVOKED`/`REUSE_DETECTED` 로 바뀌고, 재사용 탐지 직전에 정상 발급됐던 최신 refresh token 으로도 회전이 `invalid_grant` 로 거부된다(계열 전체가 죽었기 때문).
22. **revocation** — 살아있는 refresh token 을 `POST /oauth2/revoke` 하면 `200`, 존재하지 않는 토큰을 revoke 해도 `200`(RFC 7009 2.2, 응답으로 존재 여부를 노출하지 않는다). 폐기 후 그 토큰으로 회전을 시도하면 `invalid_grant`, introspect 하면 `{"active":false}`.
23. **introspection 분기** — access token 을 다른 client(`article-api`)가 조회하면 `active:true` + `client_id` + `token_type:"Bearer"`. 살아있는 refresh token 을 조회하면 `active:true` 지만 `token_type` 은 없다. 폐기된 refresh token 은 `{"active":false}` 하나뿐(사유 미노출). 잘못된 client secret 으로 호출하면 `401`. (슬라이스 4에서 호출자 인증이 client 인증(Basic)에서 Bearer + `introspect` scope 로 바뀌었다 — 이 항목은 그 이전 시점에 검증한 사실 그대로 남기고, 바뀐 뒤의 계약은 27번을 본다)
24. **회귀** — `/userinfo` 는 여전히 `200` + claim, authorization code 재사용은 여전히 `invalid_grant`, discovery(`/.well-known/openid-configuration`) 에 `introspection_endpoint`·`revocation_endpoint`·`refresh_token`(grant_types_supported)·`offline_access`(scopes_supported) 가 모두 노출된다.
25. **token-state 외부 비노출** — gateway 를 통해 `/internal/refresh-tokens/introspect` 를 호출하면 `404`. nginx 가 `/internal/*` 를 라우팅하지 않는다(gateway/nginx.conf 에 해당 location 자체가 없다).

### 슬라이스 4 (client 능력 scope 와 client_credentials)

26. **client_credentials 토큰 획득** — `article-api:secret` 로 `grant_type=client_credentials&scope=introspect` 요청하면 응답 `scope: introspect`, `refresh_token`·`id_token` 모두 부재, access token 의 `sub`·`aud` 모두 `article-api`.
27. **introspection 이 protected resource 로 전환** — `article-api` 의 client_credentials 토큰을 **Bearer** 로 실어 `my-client` 의 access token 을 조회하면 `active:true`+`client_id:my-client`. **Basic**(`article-api:secret`) 으로 같은 엔드포인트를 호출하면 더 이상 client 인증으로 받아주지 않고 `401`+`WWW-Authenticate: Bearer`. `introspect` scope 가 없는(`my-client` 발급) access token 을 Bearer 로 실어 호출하면 `403`+`WWW-Authenticate: Bearer error="insufficient_scope"`.
28. **관문** — `client_credentials` grant 가 등록되지 않은 `my-client` 로 `grant_type=client_credentials` 요청 시 `unauthorized_client`. `my-client` 가 `authorization_code` 로 `scope=openid introspect` 요청하면(`introspect` 가 `my-client` 의 `scopes` 에 없다) redirect 에 `error=invalid_scope`.
29. **동의 화면 비노출** — `client_scopes` 는 관리자가 부여하는 값이라 동의 화면에 절대 뜨지 않는다. `openid profile` 재동의 화면에서 `introspect` 문자열 등장 `0`회.
30. **discovery** — `grant_types_supported` 에 `client_credentials`, `scopes_supported` 에 `introspect` 가 노출된다. `introspection_endpoint_auth_methods_supported` 필드 자체가 없다(Bearer+scope 요구를 표현할 표준 필드가 없어 아예 생략한다). `revocation_endpoint_auth_methods_supported` 는 그대로 존재.
31. **회귀** — client_credentials 토큰(`openid` scope 없음)으로 `/userinfo` 를 호출하면 `403`+`WWW-Authenticate: Bearer error="insufficient_scope"`. `POST /oauth2/revoke` 는 여전히 Basic 인증으로 `200`.
32. **회귀(슬라이스 3)** — `offline_access` 동의 → refresh 발급, 정상 회전(새 access/refresh token), 이미 소진된 refresh token 재사용 시 `invalid_grant`+계열 전체가 `REVOKED`/`REUSE_DETECTED`, authorization code 재사용 시 `invalid_grant`. 모두 이번 e2e 에서 재확인했다.

### 슬라이스 5 (back-channel logout · typ 헤더)

10개 서비스(signing·user-directory·client-registry·consent·token-state·session·token·auth·demo-rp, +gateway/mysql/redis 인프라)를 실제로 기동해 검증했다. **판정 원칙**: 4번은 반드시 **OP(auth)를 직접 로그아웃**시켜 판정했다 — RP 가 시작하는 `POST demo-rp/logout` 으로는 Spring Security 의 `LogoutFilter` 가 demo-rp 세션을 back-channel 과 무관하게 먼저 로컬에서 지우므로, 그 경로로는 back-channel 이 죽어 있어도 통과처럼 보인다.

검증 과정에서 demo-rp(`SecurityConfig.java`)가 confidential client(client-secret 보유)라 Spring Security 가 PKCE 를 자동으로 붙이지 않는다는 것을 발견했다 — 이 AS 의 `AuthorizeController` 는 client 종류와 무관하게 PKCE(S256) 를 항상 강제하므로, `authorizationRequestResolver` 에 `OAuth2AuthorizationRequestCustomizers.withPkce()` 를 명시적으로 걸어야 로그인 자체가 성립한다. 이 e2e 를 완주하기 위한 필수 수정이라 함께 반영했다.

1. **로그인 후 id token 에 `sid` claim 이 있다**
   ```
   $ curl -s -b $C http://localhost:8095/me
   {"sub":"user-sub-0001","sid":"Km4frKBhRWWVeVfgCEVhKw"}
   ```
   my-client curl 흐름으로 받은 id token 을 직접 디코드해도 동일하게 확인된다: `{"sub":"user-sub-0001", ..., "sid":"Wkvm NLve0WJjfYxHO2U4sQ"(공백은 표기용 아님, 원문은 붙어 있다), ...}`.

2. **`oidc_sessions` 에 `(sid, sub, demo-rp)` 행이 생긴다**
   ```
   $ docker exec -i microservice-as-mysql-1 mysql -uroot -p1111 microservice_as -e "select sid, sub, client_id from oidc_sessions;"
   sid                      sub             client_id
   Km4frKBhRWWVeVfgCEVhKw   user-sub-0001   demo-rp
   ```
   Step 1 의 `sid` 와 정확히 일치.

3. **demo-rp 의 보호 페이지가 200 이다**
   ```
   $ curl -s -b $C -w "\nstatus=%{http_code}\n" http://localhost:8095/me
   {"sub":"user-sub-0001","sid":"Km4frKBhRWWVeVfgCEVhKw"}
   status=200
   ```

4. **핵심 판정 — `end_session_endpoint` 를 OP 세션 쿠키만으로 직접 호출한 뒤, demo-rp 쿠키는 손대지 않고 재요청하면 302**
   ```
   $ curl -s -o /dev/null -w "protected(pre-check)=%{http_code}\n" -b $C http://localhost:8095/me
   protected(pre-check)=200

   $ curl -s -i -b $C http://localhost:9000/oauth2/logout   # demo-rp 는 이 요청에 전혀 관여하지 않는다
   HTTP/1.1 200
   logged out

   $ sleep 2   # 비동기 발송 대기
   $ curl -s -o /dev/null -w "protected(after OP logout)=%{http_code}\n" -b $C http://localhost:8095/me
   protected(after OP logout)=302
   ```
   demo-rp 의 로그를 직접 대조해 "발송했다"가 아니라 "RP 가 받아서 세션을 끊었다"를 확인했다:
   ```
   [session.log]  logout token 발송 완료. sid=Km4frKBhRWWVeVfgCEVhKw clientId=demo-rp
   [demo-rp.log]  Securing POST /logout/connect/back-channel/microservice
   [demo-rp.log]  Invalidated session E5FD2D5AEA24E3A4411300F1A21B5934
   ```
   같은 시각(같은 로그 타임스탬프) demo-rp 의 `OidcBackChannelLogoutFilter`(경유 필터)가 실제로 세션 하나를
   `SecurityContextLogoutHandler` 로 무효화한 로그가 남는다 — 로그인 시점 쿠키의 JSESSIONID 값과 나란히
   대조하지는 않았으므로(로그인 단계에서 그 값을 별도로 뽑아 두지 않았다), "그 세션과 동일한 값이다"까지는
   주장하지 않는다. 이 항목의 판정 근거는 그 위의 200→302(demo-rp 쿠키를 건드리지 않은 상태에서의 상태 전이)
   이고, 이 로그는 그 전이가 실제 세션 무효화 이벤트로 일어났다는 보조 정황이다.

5. **`post_logout_redirect_uri` 로 `state` 와 함께 돌아온다 (RP-Initiated, 4번과 별개 세션으로 재검증)**
   ```
   $ curl -s -i -b $C2 -c $C2 -X POST http://localhost:8095/logout -d "_csrf=$LOGOUT_CSRF"
   HTTP/1.1 302
   Location: http://localhost:9000/oauth2/logout?id_token_hint=eyJ...&post_logout_redirect_uri=http://localhost:8095/

   $ curl -s -i -b $C2 "http://localhost:9000/oauth2/logout?id_token_hint=<id_token>&post_logout_redirect_uri=http://localhost:8095/&state=xyz"
   HTTP/1.1 302
   Location: http://localhost:8095/?state=xyz
   ```

6. **레지스트리 행이 사라진다**
   ```
   $ docker exec -i microservice-as-mysql-1 mysql -uroot -p1111 microservice_as -e "select count(*) from oidc_sessions;"
   count(*)
   0
   ```
   4번(OP-직접)과 5번(RP-initiated) 양쪽 로그아웃 후 각각 확인했다 — 두 경로 모두 통지 시점에 즉시 행을 지운다.

7. **미등록 `post_logout_redirect_uri` → 302 가 아니라 200, `evil.example` 로 가지 않는다**
   ```
   $ curl -s -i "http://localhost:9000/oauth2/logout?id_token_hint=<유효한 id_token>&post_logout_redirect_uri=http://evil.example/"
   HTTP/1.1 200
   logged out
   ```

8. **위조 `id_token_hint` → 302 가 아니라 200**
   ```
   $ curl -s -i "http://localhost:9000/oauth2/logout?id_token_hint=aaa.bbb.ccc&post_logout_redirect_uri=http://localhost:8095/"
   HTTP/1.1 200
   logged out
   ```

9. **discovery 에 3항목이 있다**
   ```
   $ curl -s http://localhost:9000/.well-known/openid-configuration | python3 -m json.tool
   "end_session_endpoint": "http://localhost:9000/oauth2/logout",
   "backchannel_logout_supported": true,
   "backchannel_logout_session_supported": true,
   ```

10. **`typ` 확인 — access token 은 `at+jwt`, logout token 은 `logout+jwt`**
    access token 헤더(my-client 로 받은 실제 토큰을 디코드):
    ```json
    {"kid": "signing-key-2026", "typ": "at+jwt", "alg": "RS256"}
    ```
    id token 헤더(대조군, `typ` 요구가 없으므로 그냥 `JWT`):
    ```json
    {"kid": "signing-key-2026", "typ": "JWT", "alg": "RS256"}
    ```
    logout token 헤더 — demo-rp 의 `backchannel_logout_uri` 를 임시로 캡처 서버로 돌려 실제 발송된 원문을 가로채 디코드했다:
    ```json
    {"kid": "signing-key-2026", "typ": "logout+jwt", "alg": "RS256"}
    ```
    같은 캡처로 얻은 payload 도 claim 계약표와 정확히 일치함을 확인했다: `{"sub":"user-sub-0001","aud":"demo-rp","iss":"http://localhost:9000","iat":...,"jti":"96eda29e-...","events":{"http://schemas.openid.net/event/backchannel-logout":{}},"sid":"XJqS660MxwL689DiJGdXBw"}` — `nonce`·`exp` 없음.

11. **회귀(슬라이스 1~4)**
    ```
    코드 재사용:
    $ curl ... grant_type=authorization_code&code={이미_소비된_code}...
    {"error":"invalid_grant","error_description":"code invalid or expired"}

    PKCE 변조:
    $ curl ... code_verifier=WRONG_VERIFIER_VALUE_TAMPERED_1234567890
    {"error":"invalid_grant","error_description":"PKCE verification failed"}

    refresh 회전 (1차):
    $ curl ... grant_type=refresh_token&refresh_token={t1}
    {"access_token":"...", "refresh_token":"4fTt7NH6d4k4r6w0NHMrySh5GIgc3rR8Ae4kV-VLG4M", "scope":"openid profile offline_access", ...}

    refresh 재사용 탐지 (소진된 t1 재사용):
    $ curl ... grant_type=refresh_token&refresh_token={t1}
    {"error":"invalid_grant","error_description":"refresh token is not valid"}

    같은 계열의 최신(방금 회전으로 받은) 토큰도 함께 죽는다:
    $ curl ... grant_type=refresh_token&refresh_token={방금_회전으로_받은_새_토큰}
    {"error":"invalid_grant","error_description":"refresh token is not valid"}

    $ docker exec -i microservice-as-mysql-1 mysql ... -e "select id, status, revoked_reason from refresh_tokens where client_id='my-client' order by id desc limit 2;"
    id   status    revoked_reason
    21   REVOKED   REUSE_DETECTED
    20   REVOKED   REUSE_DETECTED

    introspection — Basic → 401:
    $ curl -i -X POST .../oauth2/introspect -H "Authorization: Basic ..." -d "token=..."
    HTTP/1.1 401
    WWW-Authenticate: Bearer

    introspection — introspect scope 없는 Bearer → 403:
    HTTP/1.1 403
    WWW-Authenticate: Bearer error="insufficient_scope"

    introspection — introspect scope 있는 Bearer → 200:
    {"active":true,"sub":"user-sub-0001","client_id":"my-client","scope":"openid profile offline_access", ...}

    client_credentials:
    $ curl ... grant_type=client_credentials&scope=introspect  (article-api:secret)
    {"access_token":"...", "token_type":"Bearer", "expires_in":300, "scope":"introspect"}

    client_credentials + openid 요청 → invalid_scope:
    {"error":"invalid_scope","error_description":"user-delegated scope is not available from client_credentials"}
    ```

**e2e 로 직접 확인한 T9 관련 사실 — `session.invalidate()` 가 Redis 세션에서 실제로 동작한다.** `LogoutControllerTest`(단위 테스트)는 Redis 세션 자동설정을 끈 채 돌기 때문에 이 경로를 전혀 보지 못한다. e2e 로 Redis 키를 직접 대조했다.
```
$ SESSION_ID=$(echo $SESSION_COOKIE | base64 -d)   # 2c90e52a-0a99-4e33-9157-7f37ca27dd09
$ docker exec -i microservice-as-redis-1 redis-cli exists "spring:session:sessions:${SESSION_ID}"
(integer) 1        # 로그아웃 전 — 세션 존재

$ curl -s -o /dev/null -w "status=%{http_code}\n" -b $C5 http://localhost:9000/oauth2/logout
status=200

$ docker exec -i microservice-as-redis-1 redis-cli exists "spring:session:sessions:${SESSION_ID}"
(integer) 0        # 로그아웃 후 — Redis 에서 실제로 삭제됨

$ curl -s -i -b $C5 "http://localhost:9000/oauth2/authorize?...same session cookie..."
HTTP/1.1 302
Location: http://localhost/login   # 같은 쿠키인데도 다시 로그인을 요구한다 — 세션이 진짜로 죽었다
```

## 슬라이스 5에서도 제외 (이후 sub-project)

admin 등록 API(현재 seed), **내부 서비스 간 인증**(현재 신뢰 네트워크 가정), jwks 캐시, access token deny-list, Kafka 인증 이벤트 스트림, 관측성/서킷브레이커, resource indicator(RFC 8707, client_credentials 의 `aud` 를 자원별로 좁히는 것).

## 알려진 한계 / 추후 개선

- **프록시 헤더(ForwardedHeaderFilter) 미적용** — auth 의 로그인 redirect Location 이 게이트웨이 포트(:9000)를 잃고 `http://localhost/...` 로 나온다. curl e2e 는 절대 URL 로 우회해 통과하지만, 실제 브라우저 flow 는 auth 에 ForwardedHeaderFilter 를 추가해 X-Forwarded-Host(포트 포함)를 반영해야 한다. (production-ready-authorization-server 의 방식 참고)
- **내부 REST 호출 무인증** — /internal/* 은 gateway 라우팅에서만 제외될 뿐 네트워크로 접근 가능하면 무방비다. 서비스 간 인증이 추후 개선 1순위다. 슬라이스 6에서 Istio mTLS + `AuthorizationPolicy` 로 이 구멍을 닫는 k8s 트랙을 만들었으나, 그 영역은 플랫폼(k8s/EKS)이 더 깔끔하게 푸는 쪽이라 이 프로젝트에서 걷어내고 **별도 인프라 프로젝트로 이관**했다(아래 "추후 별도 인프라 프로젝트로 이관" 절). 그래서 이 구멍은 지금도 열려 있다 — 특히 `session:8088`(아래 항목)은 슬라이스 6 트랙에서도 배포된 적이 없어 애초에 닫힌 적이 없다.
- **jwks 캐시 부재는 성능이 아니라 가용성 한계다** — `AccessTokenVerifier` 가 access token 검증마다 signing 의 `/oauth2/jwks` 를 호출한다(캐시 없음). 부하가 signing 에 집중되는 것도 문제지만, 더 큰 문제는 **signing 이 죽으면 `/userinfo` 가 통째로 500 `server_error` 가 된다**는 점이다. 슬라이스 1 의 graceful degradation("이미 발급된 JWT 는 캐시된 공개키로 계속 검증된다")은 jwks 를 캐시하는 외부 검증자에게만 성립하고 이 서버의 `/userinfo` 에는 성립하지 않는다. jwks 를 kid 기준으로 캐시(TTL)하면 성능과 가용성이 함께 해결된다. 다음 개선.
  - 주의. 이때 500 대신 401 `invalid_token` 을 주면 안 된다. RP 는 그것을 "토큰이 죽었다"로 읽고 멀쩡한 토큰을 폐기한 뒤 재인증을 돌리므로, signing 장애 한 번이 전 RP 의 동시 재인증 폭풍으로 증폭된다. 그래서 `AccessTokenVerifier` 는 "키 확보 실패"와 "토큰 무효"를 다른 예외로 갈라 던진다.
- **`auth_time` 이 로그인 시각이 아니다** — id token 의 `auth_time` 은 `AuthorizeController` 가 authorize 요청을 처리한 시각(`Instant.now()`)이며, 실제 `POST /login` 이 성공한 시각이 아니다. 세션에 로그인 시각을 저장해두지 않기 때문. 표준의 `auth_time` 은 최종 인증 시각이므로 RP 가 `max_age` 로 재인증을 강제할 때 이 값으로는 판단할 수 없다. SSO 재사용 시나리오(로그인은 예전에 했고 이번엔 세션만 재사용)에서 `auth_time` 이 매 authorize 마다 갱신되는 것으로 보인다.
- **access token 의 `scope` claim 이 JSON 배열이다** — RFC 9068 2.2.3 은 `scope` 를 공백 구분 **문자열**로 규정하지만 이 구현은 `["openid","profile"]` 배열로 낸다(슬라이스 1 부터의 선택). `AccessTokenVerifier` 가 같은 형식을 읽으므로 내부적으로는 일관되지만, RFC 9068 을 기대하는 외부 resource server 는 scope 를 파싱하지 못한다.
- **회전은 정상 client 의 재시도도 계열을 죽인다** — 회전은 이전 refresh token 을 즉시 CONSUMED 로 만들므로, 응답을 못 받은 client 가 같은 토큰으로 재시도하면 재사용 탐지에 걸려 계열 전체가 폐기된다. client 는 refresh 요청을 직렬화해야 한다(동시에 두 번 보내지 않는다). 유예 기간(짧은 시간 안의 재사용은 허용) 없이 즉시 폐기하는 쪽을 택했다.
- **client 가 새 refresh token 을 저장하기 전에 죽으면 그 계열을 잃는다** — 회전 응답으로 새 refresh token 을 받았지만 디스크에 쓰기 전에 client 프로세스가 죽으면, 다음 기동 때는 이미 CONSUMED 된 이전 토큰만 남아 있어 그걸로 회전을 시도하는 순간 재사용 탐지로 계열이 죽는다. 유예 기간을 두지 않았기 때문이며, 이 경우 재인증(authorization_code)부터 다시 해야 한다.
- **client_credentials 토큰의 `sub == aud`** — 둘 다 client_id 다(RFC 9068). 이 서버가 resource indicator(RFC 8707)를 쓰지 않아 발급 대상 자원(audience)을 표현할 방법이 없기 때문이다. 여러 resource server 를 구분해 발급하려면 `resource` 파라미터를 받아 `aud` 를 좁히는 확장이 필요하다.
- **`aud` 를 검증하지 않는다 — 구조적으로 검증할 수 없다** — `AccessTokenVerifier.verify()` 는 서명·`exp`·`iss` 만 확인한다. `aud` 는 읽어서 `clientId` 로 돌려줄 뿐, 그 값이 호출자 자신을 가리키는지는 검증하지 않는다. 이번 슬라이스는 introspection 을 "`/userinfo` 와 같은 성격의 protected resource" 로 규정했는데(위 "관통 flow" 12번), RFC 9068 은 resource server 가 자신을 가리키지 않는 `aud` 의 access token 을 거부해야 한다고 본다. 그런데 이 검증은 구현이 빠진 것이 아니라 **애초에 성립할 방법이 없다** — 바로 위 항목(`sub == aud`)과 이어지는 지점이다. 호출자가 Bearer 로 들고 온 토큰의 `aud` 는 호출자 자신(그 토큰이 발급된 client)이지 introspection 엔드포인트가 아니므로, "이 토큰이 나를 위해 발급됐다"를 표현할 방법 자체가 resource indicator(RFC 8707) 없이는 없다. 같은 결손이 `/userinfo` 에도 있다 — client A 앞으로 발급된 access token 을 client B 가 Bearer 로 제시해도 통과한다. resource indicator 도입이 이번 슬라이스의 제외 목록에 있으므로(위 "슬라이스 5에서도 제외" 참고) 코드는 그대로 두고 한계로만 기록한다.
- **`aud`·`iat` 가 없는 JWT 가 검증을 통과하고, 그 결손이 introspection 응답에 그대로 실린다** — `AccessTokenVerifier` 는 필수로 보는 claim 이 `exp`·`iss` 뿐이라, `aud` 가 없으면 `clientId` 를, `iat` 가 없으면 `issuedAt` 을 `null` 로 채운 `VerifiedToken` 을 그대로 돌려준다. introspection 응답은 `LinkedHashMap` 이고 `@JsonInclude(NON_NULL)` 이 붙어 있지 않으므로(그 설정은 `TokenResponse` 에만 있다) `{"active": true, "client_id": null, "iat": null, ...}` 이 나간다. RFC 7662 는 두 필드를 OPTIONAL 로 두므로 표준 위반은 아니지만, 값을 담을 수 없을 때 필드를 빼는 것과 `null` 을 싣는 것은 다르다. 이 서버가 스스로 발급한 토큰에는 둘 다 항상 들어가므로 실제로 도달하려면 같은 키로 서명된 외부 JWT 가 필요하고, 그 경로를 덮는 테스트는 없다. 위 항목(`aud` 미검증)과 함께 다뤄야 하는 지점이라 resource indicator 도입 때로 미룬다.
- **client_credentials 토큰의 `sub` 가 사용자 `sub` 와 네임스페이스를 공유한다** — `users.sub`(user-directory)와 `clients.client_id`(client-registry)는 서로 다른 서비스·다른 테이블이라 두 값이 겹치는 것을 막는 제약이 하나도 없다. `client_id = "user-sub-0001"` 처럼 실제 사용자 sub 와 같은 문자열로 client 를 등록하면, 그 client 의 client_credentials 토큰의 `sub` 가 그 사용자의 sub 와 완전히 같아진다. `introspection.sub == currentUserId` 로 사용자를 판정하는 resource server 는 그 client 를 그 사용자로 오인한다. RFC 7662 의 introspection 응답에는 "이 주체가 사용자가 아니라 client 다"를 표현할 필드가 없고, 이 서버도 별도 표식을 넣지 않는다. RFC 9068 이 `sub = client_id` 를 권장하는 것은 두 네임스페이스가 분리돼 있다는 전제 위에서인데, 이 서버는 그 전제가 성립하지 않는다. 다만 오늘은 client 등록 API 가 없고 seed 로만 client 가 생기므로 실제로 도달 가능하지는 않다.
- **discovery 에 "Bearer 토큰 + 특정 scope 요구"를 표현할 표준 필드가 없다** — RFC 8414 의 `introspection_endpoint_auth_methods_supported` 는 client 인증 방식(`client_secret_basic` 등)을 담는 필드인데, 이 서버의 `/oauth2/introspect` 는 client 인증이 아니라 Bearer 토큰과 `introspect` scope 를 요구한다. 담을 값이 없다고 `"none"` 을 내보내면 "인증이 필요 없다"는 거짓이 되므로, 이 필드 자체를 아예 내보내지 않는다.
- **`scopes_supported` 의 `introspect` 가 사용자 위임 가능 여부를 구분해 주지 않는다** — discovery 는 `introspect` 가 `client_scopes`(관리자 부여) 전용이고 `authorization_code` 로는 절대 요청할 수 없다는 것을 표현할 필드가 없다. client 가 이를 사용자 위임 가능한 scope 로 오해하고 authorize 요청에 넣었다가 `invalid_scope` 로 거절당하는 시행착오를 거쳐야 알 수 있다.
- **access token 은 폐기하지 않는다** — RFC 7009 §2 는 access token 폐기를 MAY 로 두므로 표준 위반은 아니다. 이 서버는 access token 을 짧은 TTL 로 자연 만료시키는 쪽을 택했다(JWT 자가검증의 이점을 지키기 위함). `POST /oauth2/revoke` 에 access token 을 담고 `token_type_hint=access_token` 을 함께 보내면 `200` 이 오지만 실제로는 아무것도 하지 않는다. 반대로 refresh token 에 `token_type_hint=access_token` 이 잘못 붙어 오면 힌트를 그대로 믿어 폐기를 건너뛴다 — token_type_hint 를 "access_token 인지" 판정에만 쓰고 그 외에는 신뢰하지 않는 introspection 과 달리, revoke 는 이 한 갈래에서만 힌트를 신뢰한다는 뜻이라 알려진 한계로 남긴다.
- **auth 세션이 자연 만료하면 logout token 이 나가지 않는다** — Redis 세션 TTL 로 auth 세션이 조용히 사라지면 `GET /oauth2/logout` 자체가 호출될 일이 없으므로 로그아웃 통지 경로를 타지 않는다. RP 세션은 살아남는다. OP 세션 만료 자체가 스펙상 로그아웃 사건은 아니라 위반은 아니지만, "로그아웃했다고 생각했는데 앱은 살아 있는" 상황이 될 수 있다.
- **발송에 재시도가 없다** — `LogoutTokenSender`/`LogoutTokenDelivery` 는 best-effort 다. RP 가 통지 시점에 죽어 있으면 그 통지는 영구히 유실되고 로그만 남는다. 전달 보장이 필요하면 지속 큐가 필요하다.
- **`jti` 재생 방지는 RP 몫이다** — session 은 logout token 마다 `UUID.randomUUID()` 로 고유한 `jti` 를 채우기만 할 뿐, 같은 `jti` 가 재사용됐는지 자기 쪽에서 추적하지 않는다. 스펙도 재생 방지를 RP 측 선택 사항으로 둔다.
- **refresh 만으로는 RP 세션이 새로 등록되지 않는다** — 슬라이스 7부터 refresh token 레코드가 `sid` 를 보관하므로 `grant_type=refresh_token` 으로 재발급되는 id token 에도 `sid` claim 이 실린다. 다만 `oidc_sessions` 등록은 여전히 code 교환 경로에서만 일어난다 — 그 세션이 이미 등록돼 있다는 전제 위에서만 성립한다.
- **슬라이스 5 이전에 발급된 access token 은 전부 무효가 된다** — `AccessTokenVerifier` 가 이번 슬라이스부터 `typ` 헤더가 `at+jwt` 가 아니면 거부한다. 슬라이스 5 배포 이전에 발급된 access token 은 애초에 `typ` 헤더가 없으므로(signing 이 헤더를 전적으로 소유하던 예전 계약) 전부 `invalid_token` 으로 거부된다. 짧은 TTL(300초)로 자연스럽게 걸러지긴 하지만, 배포 순간 살아있던 토큰은 만료 전이라도 즉시 무효가 된다.
- **session 서비스가 다운돼 있으면 통지 시도 직후 `sid` 가 영구히 고아가 된다** — auth 의 `SessionClient.logout` 은 통지 실패를 로그로 흡수하는 fail-open 이고(이 슬라이스에서 유일하게 실패를 삼키는 지점), `LogoutController` 는 그 호출 직후 통지 성공 여부와 무관하게 곧바로 `session.invalidate()` 로 자신의 세션을 끊어 `sid` 를 파괴한다. session 서비스가 그 순간 응답하지 못했다면, 그 `sid` 로 걸린 `oidc_sessions` 행은 그 뒤 어떤 경로로도 다시 조회·소비될 수 없다 — RP 세션은 살아남고 행은 레지스트리에 영구히 남는다. fail-open 을 선택한 대가다.
- **consent 대기(pending) 중 세션이 바뀌면 승인된 code 에 stale `sid` 가 실릴 수 있다** — `ConsentPageController` 는 `pending.sub()` 와 현재 principal 만 대조하고 `sid` 는 대조하지 않는다. `GET /oauth2/authorize`(sid A)로 pending 이 만들어진 뒤 TTL(300초) 안에 로그아웃하거나 세션이 만료되고 같은 사용자로 재로그인해(sid B) 그 pending 을 승인하면, 발급되는 code·id token·`oidc_sessions` 행은 전부 sid A 인데 브라우저의 실제 OP 세션은 sid B 다. 이후 로그아웃은 sid B 를 통지하므로 sid A 행은 조회되지 않고 남는다.
- **`oidc_sessions` 에 정리 수단이 없다** — `createdAt` 은 기록만 되고 그 값을 읽는 코드도 오래된 행을 지우는 purge 작업도 없다. 통지 실패(위 session 다운 항목)나 자연 만료(auth 세션 만료 항목)로 소비되지 못한 행은 무한히 쌓인다. 위 항목들은 사용자 관점("logout token 이 나가지 않는다")까지만 다루지만, 이 항목은 운영 관점이다 — 레지스트리 테이블 자체가 무한정 커진다.
- **consent 저장이 동시 요청에서 500 을 내거나 갱신을 잃는다** — `ConsentController.saveConsent` 는 `findBySubAndClientId` 로 읽어 scope 를 병합한 뒤 저장하는 check-then-act 다. `consents` 에는 `(sub, client_id)` unique 제약이 있는데 예외를 흡수하지 않으므로, 같은 사용자·client 로 동의가 동시에 두 번 제출되면(동의 버튼 더블 클릭, 탭 두 개) 둘 다 "없다"를 보고 둘 다 INSERT 를 시도해 진 쪽이 `DataIntegrityViolationException` 으로 500 을 받는다. 기존 행이 있을 때도 둘 다 같은 기준값을 읽어 병합하므로 나중 저장이 앞선 병합을 덮는다(lost update). session 의 `SessionService.register` 는 같은 패턴을 유니크 위반 재확인으로 닫았지만, consent 는 슬라이스 2 코드라 이번 슬라이스에서 손대지 않았다.
  - 주의. check-then-act 에 unique 제약을 얹으면 데이터 무결성은 지켜지지만 호출자 관점의 멱등은 성립하지 않는다. 진 쪽은 예외를 받으므로, 멱등을 원하면 그 예외를 잡고 **행이 실제로 생겼는지 재확인**해 흡수해야 한다. 그리고 `@Transactional` 메서드 안에서는 그 흡수가 통하지 않는다 — `IDENTITY` 채번은 `save` 시점에 즉시 flush 하고, 그 실패가 트랜잭션을 rollback-only 로 오염시켜 commit 에서 `UnexpectedRollbackException` 이 난다.
- **session(8088) 에 네트워크로 도달하면 `sid` 하나로 남의 세션을 강제 로그아웃시킬 수 있다** — `sid` 의 엔트로피(128비트)는 충분하지만 설계상 비밀은 아니다. id token 에 실려 그 세션의 모든 RP 에게 배포되고 로그에도 남는다. 그리고 `POST /internal/sessions/logout` 은 인증이 없다(gateway 가 라우팅하지 않는다는 사실은 위 서비스 표에 있지만, 그것이 네트워크 도달 자체를 막지는 않는다). 따라서 그 `sid` 를 아는 누구든(같은 세션의 임의의 RP, 로그 접근자) session 서비스에 네트워크로 도달하기만 하면 그 사용자를 다른 모든 RP 에서 강제 로그아웃시킬 수 있다. `POST /internal/sessions` 로 가짜 `(sid, sub, clientId)` 행을 심는 것도 마찬가지로 인증이 없다. 다만 실제 피해는 가용성(원치 않는 강제 로그아웃)에 국한된다 — 개인키는 signing 만 보유하므로 이 경로로 다른 사용자를 사칭하는 access token·id token 서명 위조는 불가능하다.
- HA(다중 인스턴스), 키 로테이션, purge 등은 production-ready-authorization-server 에서 다룬 주제.

## 추후 별도 인프라 프로젝트로 이관

이 프로젝트는 **인가 서버를 코드로 어떻게 만드는가**에 집중한다. mTLS·서비스 메시·ingress·네트워크
인가 정책·배포 같은 영역은 플랫폼(k8s/EKS/Istio)이 더 깔끔하게 풀기 때문에 여기서 손으로 재현하지
않는다. 반대로 MySQL·Redis·Kafka 처럼 **앱이 직접 코드로 통합하는 미들웨어**는 이 프로젝트 안쪽이다 —
붙이는 방식에 따라 정합성·순서·중복·트랜잭션 경계가 실제로 갈리기 때문이다.

슬라이스 6에서 만들었던 `k8s/` 트랙(kind + Istio mTLS + `AuthorizationPolicy`)은 이 기준의 바깥이라
걷어냈다. 설계·구현 계획 문서는 `docs/superpowers/` 에 그대로 남아 있고, 이관 경위와 백로그는 여기에
있다 — **[docs/infra-project-backlog.md](docs/infra-project-backlog.md)**.

그래서 **서비스 간 인증은 이 프로젝트에서 여전히 미해결**이다. 위 "알려진 한계"의 내부 무인증
항목과 `session:8088` 항목이 그 결손을 구체적으로 짚는다.

## `custom-oidc-logout` 의 TODO 를 이 AS 로 닫는 법

`oauth-2/client/custom-oidc-logout` 의 `OAuth2ClientConfig.java` 에는 `.oidcLogout().backChannel()` 이 주석 처리된 채 TODO 로 남아 있다 — "provider 에서 로그아웃을 해도 client 로그아웃이 안됨.. provider 에 셋팅을 해줘야 할 것 같음". 그 프로젝트는 Keycloak 을 대상으로 하고 여기서는 수정하지 않는다(Keycloak 예시로 그대로 둔다). 다만 그 TODO 가 짚은 원인은 정확했다 — **client 쪽 설정만으로는 back-channel logout 이 성립하지 않는다. provider(OP) 가 세션을 추적하고 logout token 을 실제로 보내야** 비로소 `.oidcLogout()` 이 받을 것이 생긴다. 이 AS 로 그 TODO 를 닫으려면: (1) client 를 이 AS 에 등록하며 `backchannel_logout_uri` 를 그 client 의 `/logout/connect/back-channel/{registrationId}` 로 채우고 `post_logout_redirect_uris` 도 등록한다(client-registry, 두 컬럼 모두 `redirect_uris` 와 별개다), (2) client 의 `issuer-uri` 를 `http://localhost:9000` 으로 두어 discovery 로 `end_session_endpoint`·`backchannel_logout_supported` 를 끌어오게 한다, (3) 주석을 풀고 `demo-rp/src/main/java/dev/starryeye/demo_rp/SecurityConfig.java` 와 똑같이 `.oidcLogout(oidc -> oidc.backChannel(Customizer.withDefaults()))` 를 필터체인에 추가하며 back-channel 수신 경로(`/logout/connect/back-channel/**`)를 `permitAll()` + CSRF 예외로 열어 둔다(OP 가 사용자 세션 없이 서버 대 서버로 POST 하기 때문). demo-rp 가 정확히 이 세 단계를 거쳐 만든 검증용 RP 다.

## 설계/계획 문서

- 슬라이스 1 설계: [docs/superpowers/specs/2026-07-18-microservice-authorization-server-slice1-design.md](docs/superpowers/specs/2026-07-18-microservice-authorization-server-slice1-design.md)
- 슬라이스 1 구현 계획: [docs/superpowers/plans/2026-07-18-microservice-authorization-server-slice1.md](docs/superpowers/plans/2026-07-18-microservice-authorization-server-slice1.md)
- 슬라이스 2(OIDC) 설계: [docs/superpowers/specs/2026-07-25-microservice-oidc-slice2-design.md](docs/superpowers/specs/2026-07-25-microservice-oidc-slice2-design.md)
- 슬라이스 2(OIDC) 구현 계획: [docs/superpowers/plans/2026-07-25-microservice-oidc-slice2.md](docs/superpowers/plans/2026-07-25-microservice-oidc-slice2.md)
- 슬라이스 3(토큰 수명 관리) 설계: [docs/superpowers/specs/2026-07-25-microservice-token-lifecycle-slice3-design.md](docs/superpowers/specs/2026-07-25-microservice-token-lifecycle-slice3-design.md)
- 슬라이스 3(토큰 수명 관리) 구현 계획: [docs/superpowers/plans/2026-07-25-microservice-token-lifecycle-slice3.md](docs/superpowers/plans/2026-07-25-microservice-token-lifecycle-slice3.md)
- 슬라이스 4(client 능력 scope · client_credentials) 설계: [docs/superpowers/specs/2026-07-28-microservice-client-credentials-slice4-design.md](docs/superpowers/specs/2026-07-28-microservice-client-credentials-slice4-design.md)
- 슬라이스 4(client 능력 scope · client_credentials) 구현 계획: [docs/superpowers/plans/2026-07-28-microservice-client-credentials-slice4.md](docs/superpowers/plans/2026-07-28-microservice-client-credentials-slice4.md)
- 슬라이스 5(back-channel logout) 설계: [docs/superpowers/specs/2026-08-03-microservice-backchannel-logout-slice5-design.md](docs/superpowers/specs/2026-08-03-microservice-backchannel-logout-slice5-design.md)
- 슬라이스 5(back-channel logout) 구현 계획: [docs/superpowers/plans/2026-08-03-microservice-backchannel-logout-slice5.md](docs/superpowers/plans/2026-08-03-microservice-backchannel-logout-slice5.md)
- 슬라이스 6(Istio mTLS) 설계: [docs/superpowers/specs/2026-08-07-microservice-istio-mtls-slice6-design.md](docs/superpowers/specs/2026-08-07-microservice-istio-mtls-slice6-design.md)
- 슬라이스 6(Istio mTLS) 구현 계획: [docs/superpowers/plans/2026-08-07-microservice-istio-mtls-slice6.md](docs/superpowers/plans/2026-08-07-microservice-istio-mtls-slice6.md)
