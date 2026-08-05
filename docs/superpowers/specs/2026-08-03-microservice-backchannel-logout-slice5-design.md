# 마이크로서비스 인가 서버 — 슬라이스 5 설계: back-channel logout

로그아웃을 OP 가 RP 들에게 알린다. 그리고 토큰 타입을 헤더로 구분한다.

대상: `oauth-2/authorization-server/practice/microservice/`
선행: 슬라이스 1(authorization code + PKCE), 2(OIDC), 3(토큰 수명 관리), 4(client 능력 scope) — 8개 바이너리

## 목표

슬라이스 1~4는 **토큰을 내주는 쪽**만 다뤘다. 세션이 끝나는 사건은 어디에도 없다. auth 에 로그인하면 RP 는 id token 을 받아 자기 세션을 세우는데, 그 뒤로 OP 와 RP 의 세션은 서로를 모른 채 각자 산다.

- **RP-Initiated Logout 1.0** — RP 가 사용자를 OP 로 보내 로그아웃을 시작한다 (`end_session_endpoint`)
- **Back-Channel Logout 1.0** — OP 가 세션을 가진 각 RP 에게 logout token 을 POST 한다
- **`typ` 헤더 도입** — 같은 키로 서명되는 세 토큰 타입을 헤더로 구분한다

주제는 하나다. **세션은 여러 곳에 흩어져 있고, 그것을 한 번에 끝내려면 누가 무엇을 갖고 있는지 알아야 한다.**

### 이 슬라이스가 닫는 기존 항목

README 의 알려진 한계 중 하나를 직접 닫는다.

> **access token 과 id token 을 구분할 수 있는 표식이 없다** — … 그 방어는 우연에 가깝다. … 정석은 RFC 9068 의 `typ: at+jwt` 헤더로 access token 을 명시하고 검증 시 그 값을 강제하는 것이다. … **다음 슬라이스 대상.**

back-channel logout 이 **세 번째 토큰 타입**을 추가하므로 이번에 함께 닫는다.

---

## 1. 무엇이 어디서 바뀌나

새 바이너리가 둘 생겨 10개가 된다.

| 서비스 | 변경 |
|---|---|
| **session**(8088) | **신규.** `(sid, sub, client_id)` 레지스트리 소유 + logout token 발송 |
| **demo-rp**(8095) | **신규.** 진짜 Spring Security RP. e2e 검증자 |
| **auth**(8081) | 로그인 시 `sid` 생성·세션 저장 · authorize 에서 code 에 `sid` · `GET /oauth2/logout` 신설 |
| **token**(8082) | id token 에 `sid` claim · session 등록 호출 · `typ` 전달 · discovery 3항목 |
| **signing**(8083) | 서명 API 가 `typ` 을 받는다 (헤더 소유권 계약 변경) |
| **client-registry**(8085) | `backchannel_logout_uri` · `post_logout_redirect_uris` 컬럼 |
| **gateway**(9000) | `/oauth2/logout` → auth |
| user-directory · consent · token-state | 변경 없음 |

### token-state 는 관여하지 않는다 — 로그아웃은 refresh token 을 죽이지 않는다

이 서버는 `offline_access` 가 동의됐을 때만 refresh token 을 발급한다(`TokenEndpointController` 의 이중 조건). 그런데 OIDC Core §11 이 정의하는 `offline_access` 의 뜻이 **"사용자가 로그인해 있지 않아도 쓸 수 있는 refresh token 을 달라"** 다.

**주의.** 로그아웃 때 refresh 계열을 폐기하면 그 토큰을 만들어낸 유일한 scope 의 의미를 정면으로 부정하게 된다. 세션 종료와 위임 철회는 다른 사건이다. 후자는 `POST /oauth2/revoke`(슬라이스 3)의 몫이다.

Google 도 두 개념을 다른 엔드포인트로 갈라 둔다 — 세션은 `accounts.google.com`, 위임 철회는 `/revoke`(RFC 7009)다.

---

## 2. `sid` — OP 세션 식별자

`sid` 는 **auth 의 HTTP 세션 id 가 아니다.** 별도 불투명 난수를 쓴다.

**주의.** `sid` 는 id token 에 실려 RP 로 나가고 로그에도 남는다. 거기에 실제 세션 id 를 노출하면 세션 탈취 표면이 된다.

로그인 한 번에 `sid` 하나가 나오고, 그 세션에서 authorize 하는 **모든 RP 가 같은 `sid`** 를 받는다. 로그아웃 후 재로그인하면 새 `sid` 다(다른 OP 세션이므로).

**주의.** 이 문장이 성립하려면 로그인 성공 핸들러가 `sid` 를 **무조건** 새로 만들어야 한다. `SessionIdIssuer` 가 `issue`(있으면 재사용, 없으면 새로 만드는 멱등 버전)와 `renew`(항상 새로 만드는 버전)로 나뉘는 이유가 그것이다 — authorize 경로는 같은 세션의 여러 RP 가 같은 `sid` 를 봐야 하므로 `issue` 를 쓰고, 로그인 성공 핸들러는 `renew` 를 쓴다. Spring Security 의 세션 고정 방어(`changeSessionId`)는 세션 id 만 바꾸고 세션 속성은 그대로 옮기므로, 로그인 성공 시점에도 `issue` 를 쓰면 로그아웃 없이 다른 사용자로 재로그인했을 때 이전 사용자의 `sid` 를 그대로 물려받아 이 문장이 깨진다.

`sid` 가 관통하는 경로:

```
로그인(auth)          → sid 생성, 세션 속성에 저장
  authorize(auth)     → AuthorizationCodeData 에 sid 를 실어 Redis 에 저장
    code 교환(token)  → id token 에 sid claim
                      → session 에 (sid, sub, client_id) 등록   ← RP 세션이 실제로 서는 순간
```

**주의.** RP 세션은 auth 가 로그인시킨 순간이 아니라 **token 이 id token 을 내준 순간** 선다. code 를 받고 교환하지 않은 RP 는 세션이 없으므로 통지 대상이 아니다. 그래서 등록을 token 이 한다.

따라서 **등록은 id token 을 발급할 때만 한다.** `openid` scope 가 없는 교환은 RP 세션을 만들지 않으므로 등록하지 않는다.

`sid` 는 `SecureRandom` 기반 URL-safe base64 문자열로 만든다(엔트로피는 authorization code 와 같은 등급). 추측 가능한 값이면 남의 세션을 지목하는 logout token 을 위조할 근거가 된다.

`AuthorizationCodeData` 는 auth 와 token 이 공유하는 Redis 계약이므로 양쪽을 함께 바꾼다(슬라이스 2에서 `nonce`·`authTime` 을 넣은 것과 같은 방식).

---

## 3. logout token 계약

Spring Security 6.4.5 의 `OidcBackChannelLogoutTokenValidator` 바이트코드에서 실제 검증 항목을 확인했다. 아래는 **RP 가 실제로 거부하는 조건**이다.

| claim | 검증기가 요구하는 것 | 우리가 보내는 값 |
|---|---|---|
| `iss` | null 금지 + RP 설정 issuer 와 **정확히 일치** | `http://localhost:9000` |
| `aud` | null 금지 + 그 RP 의 `client_id` 포함 | 대상 RP 의 client_id |
| `iat` | null 금지 | 발급 시각 |
| `jti` | null 금지 | 난수 |
| `events` | null 금지 + `http://schemas.openid.net/event/backchannel-logout` **키 포함** | `{"http://schemas.openid.net/event/backchannel-logout": {}}` |
| `sub` / `sid` | **둘 다 null 이면 거부** | 둘 다 싣는다 |
| `nonce` | **있으면 거부** | 싣지 않는다 |
| `exp` | 검증하지 않는다 | 싣지 않는다 |

전송 형식: `POST {backchannel_logout_uri}`, `application/x-www-form-urlencoded`, 파라미터 이름 `logout_token`.

Spring Security RP 의 기본 수신 경로는 `/logout/connect/back-channel/{registrationId}` 다.

### `nonce` 를 금지하는 이유

**주의.** 스펙이 `nonce` 를 금지하는 것은 **토큰 치환**을 막기 위해서다. logout token 에 `nonce` 가 있으면 id token 검증 경로에 밀어넣었을 때 통과할 여지가 생긴다. `events` 를 필수로 두고 `nonce` 를 금지해서 두 토큰 타입이 구조적으로 호환되지 않게 만든다.

### `iss` 정확 일치가 기존 결함과 만난다

RP 는 자기가 discovery 로 알아낸 issuer 와 logout token 의 `iss` 를 문자열 정확 일치로 본다.

**주의.** 이 저장소에는 "ForwardedHeaderFilter 미적용 — auth 의 redirect 가 게이트웨이 포트(:9000)를 잃는다"가 알려진 한계로 있다. issuer 가 한 글자라도 어긋나면 logout token 이 통째로 거부된다. 발송·검증 경로에서 issuer 값이 항상 `http://localhost:9000` 인지 확인한다.

---

## 4. `typ` 헤더

signing 이 헤더를 전적으로 소유한다는 기존 계약을 바꿔, **서명 요청이 `typ` 을 지정**하게 한다.

| 토큰 | `typ` | 근거 |
|---|---|---|
| access token | `at+jwt` | RFC 9068 §2.1 |
| logout token | `logout+jwt` | Back-Channel Logout 1.0 §2.4 |
| id token | `JWT` | OIDC Core 는 별도 `typ` 을 요구하지 않는다 |

`AccessTokenVerifier` 는 `typ` 이 `at+jwt` 가 아니면 거부한다.

**주의.** 이 검증이 없으면 `typ` 은 장식이다. 지금 id token·logout token 이 access token 으로 통하지 않는 이유는 각각 `scope` claim 부재와 `exp` 부재라는 **우연한 결손** 때문이고, 그 결손이 메워지는 순간 방어가 사라진다. 검증을 강제해야 구조적 방어가 된다.

---

## 5. 서비스 계약

### client-registry — 컬럼 2개

```
backchannel_logout_uri      varchar(500) null                 -- 없으면 통지 대상 아님
post_logout_redirect_uris   varchar(500) not null default ''  -- comma 구분
```

**주의.** `post_logout_redirect_uris` 는 `redirect_uris` 와 **반드시 별도 컬럼**이다. 목적이 다르다. 섞으면 로그인 콜백 주소로 로그아웃 리다이렉트가 되거나 그 반대가 된다.

`ClientResponse`·`ClientInfo` 에는 **맨 뒤에** 추가한다.

**주의.** record 중간에 끼우면 기존 위치 기반 생성자 호출에서 `List<String>` 타입 필드끼리 순서가 바뀌어도 컴파일이 통과해 조용히 어긋난다(슬라이스 4에서 같은 이유로 `clientScopes` 를 맨 뒤에 넣었다).

### session(8088) — 신규 서비스

```sql
oidc_sessions
  id          bigint       pk auto_increment
  sid         varchar(64)  not null
  sub         varchar(64)  not null
  client_id   varchar(100) not null
  created_at  datetime(6)  not null
  unique key uk_sid_client (sid, client_id)
```

| 엔드포인트 | 호출자 | 계약 |
|---|---|---|
| `POST /internal/sessions` | token | `{sid, sub, clientId}` → 200. **멱등** (같은 RP 가 여러 번 교환할 수 있다). id token 을 발급하는 교환에서만 호출한다 |
| `POST /internal/sessions/logout` | auth | `{sid}` → 200 **즉시**. 발송은 비동기 |

발송 경로: `oidc_sessions` 에서 `sid` 로 client 목록 조회 → client-registry 에서 각 `backchannel_logout_uri` 조회 → signing 에 서명 요청 → 각 RP 로 form POST. `backchannel_logout_uri` 가 비어 있는 client 는 건너뛴다.

**저장소는 MySQL 이다.** 세션은 TTL 성격이라 Redis 가 편하지만, 슬라이스 3·4의 e2e 가 `SELECT` 로 상태를 덤프해 주장을 증명한 방식이 이 저장소의 검증 방법이다. 레지스트리도 같은 방식으로 들여다볼 수 있어야 한다.

### auth — `GET /oauth2/logout`

RP-Initiated Logout 1.0 의 `end_session_endpoint` 다. 처리 순서:

1. `id_token_hint` 서명 검증 — **`exp` 는 무시한다**
2. `aud` 에서 client_id 를 얻어, `post_logout_redirect_uri` 를 **그 client 의 등록 목록과 정확 일치** 대조
3. 세션에서 `sid` 를 읽어 session 에 로그아웃 통지 → 세션 무효화
4. 검증을 통과한 `post_logout_redirect_uri` 로 302 (`state` 를 그대로 되돌려 준다). 없으면 자체 완료 페이지

**주의.** `id_token_hint` 는 **사용자 식별에 쓰지 않는다.** 사용자는 브라우저로 오므로 auth 세션 쿠키로 이미 누구인지 안다 — `sid` 는 세션에서 읽는다. `id_token_hint` 는 오직 `post_logout_redirect_uri` 를 어느 client 기준으로 검증할지 정하는 데만 쓴다.

**주의.** 로그아웃 시점에 id token 이 만료돼 있는 것은 정상이다. 그래서 `exp` 를 검사하지 않는다. 이 저장소에서 만료를 일부러 무시하는 유일한 검증이다.

**주의.** `SecurityConfig` 는 `.logout(...)` 을 배선하지 않는다 — 위 `GET /oauth2/logout` 이 이 서비스의 유일한 로그아웃 경로다. 그 결과 Spring Boot 기본 `LogoutFilter` 가 그대로 살아 있어, `POST /logout` 을 호출하면 session 서비스에 통지하지 않고 auth 자신의 세션만 끊는다(RP 세션은 살아남는다). gateway 도 `/logout` 을 라우팅하지 않으므로(라우팅 대상은 `/oauth2/logout` 뿐이다) 이 경로에 외부에서 도달할 방법도 없다.

### discovery(token) — 3항목 추가

```
end_session_endpoint                  http://localhost:9000/oauth2/logout
backchannel_logout_supported          true
backchannel_logout_session_supported  true
```

### demo-rp(8095) — 검증자

`spring-boot-starter-oauth2-client` 로 `issuer-uri: http://localhost:9000` 을 두고 discovery 로 설정을 끌어온다. `oauth2Login` + `oidcLogout().backChannel()` + `OidcClientInitiatedLogoutSuccessHandler`.

registrationId 를 `microservice` 로 두면 URI 가 정해진다.

```
redirect_uri            http://localhost:8095/login/oauth2/code/microservice
backchannel_logout_uri  http://localhost:8095/logout/connect/back-channel/microservice
post_logout_redirect    http://localhost:8095/
```

`ClientSeedInitializer` 에 `demo-rp` client 를 추가한다 — 위 세 URI, `scopes` 는 `openid,profile,email`, `grant_types` 는 `authorization_code,refresh_token`, `client_scopes` 는 빈 문자열이다. 기존 `my-client`(curl 용)와 `article-api`(client_credentials 용)는 그대로 둔다.

**주의.** 포트는 8095 다. `my-client` 의 `redirect_uri` 가 `http://127.0.0.1:8080/callback` 이라 8080 을 쓰면 문서에서 두 client 가 같은 포트로 보인다(실제로 8080 에 뜨는 것은 없고 curl 은 Location 헤더만 읽으므로 충돌은 없지만, 읽는 사람이 헷갈린다).

demo-rp 에는 로그인이 필요한 보호 페이지를 하나 둔다. e2e 4번 기준이 이 페이지의 상태 코드로 판정된다.

**주의.** 검증자가 우리 코드가 아니라 Spring Security 구현이어야 한다. 우리가 만든 스텁은 우리가 스펙을 잘못 읽어도 그 오해에 그대로 동의한다. 기존 `oauth-2/client/custom-oidc-logout` 은 Keycloak 예시로 그대로 두고, 이 AS 로 돌리는 법은 README 에 기록한다.

---

## 6. 오류 처리 — 로그아웃은 fail-open 이다

이 저장소는 슬라이스 2·3에서 fail-closed 를 반복해서 강조했다. **로그아웃에서는 방향이 반대다.** 로그아웃을 실패시키면 세션이 살아남고, 그것이 더 위험하다.

| 상황 | 세션 무효화 + 통지 | `post_logout_redirect_uri` 리다이렉트 |
|---|:--:|:--:|
| 정상 | O | O |
| `id_token_hint` 없음 | O | X |
| `id_token_hint` 서명 검증 실패 | O | X |
| `post_logout_redirect_uri` 미등록 | O | X |
| signing 장애로 힌트 검증 불가 | O | X |
| 세션이 이미 없음 | — | O (오류 아님) |

**주의.** 왼쪽 열은 어떤 경우에도 수행한다. 세션을 끊는 것은 auth 안에서 끝나는 로컬 작업이라 외부 의존성이 없다. 검증은 오직 "어디로 돌려보낼지"를 정할 때만 필요하다.

**주의.** 미등록 `post_logout_redirect_uri` 로 리다이렉트하지 않는 것은 open redirect 방지다. 슬라이스 1의 authorize 가 `redirect_uri` 를 정확 일치로 검증하는 것과 같은 원칙이다.

### 그 외

| 상황 | 처리 |
|---|---|
| RP 가 5xx·타임아웃·404 | 로그만. 재시도 없음 |
| signing 장애로 logout token 생성 불가 | 그 발송 라운드 실패. 로그. 세션은 이미 끊긴 상태 |
| client-registry 장애 | 그 client 만 건너뛴다 |
| `oidc_sessions` 행 삭제 | 발송 성공 여부와 무관하게 **통지 시 즉시** |

### 예외: session 등록 실패는 fail-closed

token 이 code 를 교환할 때 session 등록에 실패하면 **토큰 발급 전체를 실패시킨다**(`server_error`).

**주의.** 등록이 안 되면 그 RP 는 영원히 로그아웃 통지를 받지 못하고, RP 는 그 사실을 알 방법이 없다. discovery 의 `backchannel_logout_supported: true` 는 RP 가 자기 세션 관리를 설계하는 근거이므로, 등록 실패를 삼키는 것은 그 세션에 대해 조용히 약속을 깨는 것이다.

가용성 등급은 바뀌지 않는다. token 은 이미 signing 에 의존하고, signing 이 죽으면 어차피 토큰이 나가지 않는다. 로그인과 authorize 는 막히지 않는다 — code 교환 한 지점에만 걸린다.

---

## 7. 테스트 전략

**단위** — logout token 의 claim 을 **하나씩 격리해** 단언한다. 특히 `nonce` 부재와 `events` 키는 각각 별도 테스트로 둔다. 둘 다 RP 가 거부 사유로 삼는 항목이다.

`id_token_hint` 검증의 "만료 무시"는 **의도한 동작이므로 반드시 테스트가 있어야 한다.** 없으면 나중에 누군가 `exp` 검사를 "빠뜨린 것"으로 보고 추가해 로그아웃을 깨뜨린다.

`typ` 강제는 세 방향을 모두 단언한다 — access token 이 `at+jwt` 로 서명되는가, `AccessTokenVerifier` 가 다른 `typ` 을 거부하는가, logout token 이 `logout+jwt` 인가.

**e2e 성공 기준**

1. 로그인 후 id token 에 `sid` claim 이 있다
2. `oidc_sessions` 에 `(sid, sub, demo-rp)` 행이 생긴다
3. demo-rp 의 보호 페이지가 200 이다
4. `end_session_endpoint` 호출 후 **같은 쿠키로 보호 페이지를 재요청하면 302**(로그인으로) 다
5. `post_logout_redirect_uri` 로 `state` 와 함께 돌아온다
6. `oidc_sessions` 행이 사라진다
7. 미등록 `post_logout_redirect_uri` → 리다이렉트하지 않고 세션은 끊긴다
8. 위조 `id_token_hint` → 리다이렉트하지 않고 세션은 끊긴다
9. discovery 에 3항목이 있다
10. access token 헤더가 `typ: at+jwt` 이고, logout token 은 `logout+jwt` 다
11. 회귀(슬라이스 1~4): code 재사용·PKCE 변조 `invalid_grant`, refresh 회전·재사용 탐지, introspection Bearer 인가, client_credentials

**주의.** 4번이 이 슬라이스의 핵심 기준이다. logout token 을 "보냈다"가 아니라 **RP 가 받아서 세션을 끊었다**를 증명해야 한다. 발송 로그만 보고 통과로 판정하지 않는다.

**주의.** 4번은 **OP 를 직접 로그아웃시켜** 판정한다. RP 가 시작하는 로그아웃(RP 의 `/logout`)으로 판정하면 안 된다. Spring Security 의 `LogoutFilter` 가 RP 세션을 **먼저 로컬에서 무효화**한 뒤 사용자를 `end_session_endpoint` 로 보내기 때문에, back-channel 이 전혀 동작하지 않아도 보호 페이지가 302 가 되어 통과처럼 보인다. RP 를 건드리지 않고 OP 세션 쿠키만으로 로그아웃해야 RP 세션을 끊을 수 있는 원인이 logout token 하나로 좁혀진다.

---

## 8. 알려진 한계로 기록할 것

- **auth 세션이 자연 만료하면 logout token 이 나가지 않는다.** RP 세션은 살아남는다. 스펙상 OP 세션 만료가 로그아웃 사건은 아니라 위반은 아니지만, "로그아웃했다고 생각했는데 앱은 살아 있는" 상황이 된다.
- **발송에 재시도가 없다.** RP 가 잠깐 죽어 있으면 그 통지는 영구히 유실된다. 전달 보장이 필요하면 지속 큐가 필요하다.
- **`jti` 재생 방지는 RP 몫이다.** OP 는 고유하게 만들 뿐, 같은 `jti` 가 재사용됐는지 추적하지 않는다(스펙도 RP 측 선택 사항으로 둔다).
- **client 별 `backchannel_logout_session_required` 를 지원하지 않는다.** 항상 `sid` 를 싣는다.

## 9. 이번 슬라이스에서 제외

- **Front-Channel Logout 1.0** — 브라우저 서드파티 쿠키 차단으로 사장되는 추세라 투자 대비 학습 가치가 낮다
- **OP 세션 만료 시 통지** — 만료 감지에 세션 이벤트 배선이 필요하다
- **발송 재시도·큐** — 슬라이스 6(Kafka 인증 이벤트 스트림)에서 이 내부 leg 를 대체하며 다룬다. logout token 자체가 Security Event Token(RFC 8417)이므로 그 슬라이스와 자연스럽게 이어진다
- **`sub` 기준 로그아웃**(모든 기기에서 로그아웃) — logout token 은 `sub` 를 싣지만, 트리거는 `sid` 기준만 만든다
- **RFC 8707 resource indicator** — 슬라이스 4에서 제외한 그대로. `aud` 검증 불가 한계도 그대로 남는다
