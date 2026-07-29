# 마이크로서비스 인가 서버 — 슬라이스 4 설계: client_credentials + introspect scope

client 자체 능력을 scope 로 표현하고, introspection 엔드포인트의 인가를 좁힌다.

대상: `oauth-2/authorization-server/practice/microservice/`
선행: 슬라이스 1(authorization code + PKCE), 2(OIDC), 3(토큰 수명 관리) — 8개 바이너리

## 목표

슬라이스 3의 introspection 은 **인증만 하고 인가를 하지 않는다.** 등록된 client 면 누구나 아무 토큰이나 조회할 수 있고, 이는 그때 알려진 한계로 기록했다. 이번 슬라이스가 그것을 닫는다.

- **client_credentials grant** (RFC 6749 §4.4) — 사용자 없이 client 가 자기 자신으로서 토큰을 받는다
- **client 능력 scope** — 관리자가 client 에게 부여한 권한을 사용자 위임 scope 와 **스키마에서** 구분한다
- **introspection 을 protected resource 로** — client 인증 대상에서, access token 의 scope 를 확인하는 자원으로 바꾼다

주제는 하나다. **"사용자가 위임한 권한"과 "관리자가 부여한 능력"은 다른 것이고, 그 차이가 구조에 드러나야 한다.**

---

## 1. 무엇이 어디서 바뀌나

새 서비스는 없다. 슬라이스 3까지의 8개 위에 얹는다.

| 서비스 | 변경 |
|---|---|
| **client-registry**(8085) | `clients` 에 `client_scopes` 컬럼, `ClientResponse` 필드 추가, seed 갱신 |
| **token**(8082) | `client_credentials` grant, introspection 인가를 Bearer 로 전환, `ClientInfo` 필드 추가, discovery 갱신 |
| **auth**(8081) | **변경 없음** |
| signing · user-directory · consent · token-state · gateway | 변경 없음 |

### auth 를 건드리지 않는 것이 설계의 일부다

auth 는 authorization_code 만 다루고, 그 경로는 `scopes`(사용자 위임) 컬럼만 본다. auth 의 `ClientInfo` 에는 `@JsonIgnoreProperties(ignoreUnknown = true)` 가 붙어 있어 새 필드가 응답에 실려도 무시한다.

**client 능력 scope 가 auth 의 시야에 들어오지 않는다** — 동의 화면에 뜰 수 없다는 보장이 "거르는 코드"가 아니라 **구조**에서 나온다.

**주의.** auth 에 변경이 필요하다고 느껴지면 그것은 신호다. 어딘가에서 client 능력 scope 를 사용자 위임 경로로 흘려보내고 있다는 뜻이므로, 그 지점을 먼저 확인한다.

### token-state 는 관여하지 않는다

client_credentials 는 refresh token 을 발급하지 않으므로(§4.4.3) 저장할 상태가 없다.

---

## 2. 데이터 모델

### `clients` — 컬럼 하나 추가

| 컬럼 | 의미 |
|---|---|
| `scopes` (기존) | **사용자가 위임할 수 있는** scope. 동의 화면에 뜬다 |
| `client_scopes` (신규) | **관리자가 client 에게 부여한** 능력. 동의 화면에 뜨지 않는다 |

`client_scopes` 는 not null, comma 구분, length 500 — 기존 `scopes` 와 같은 형태다.

**grant 별로 보는 컬럼이 다르다.**

| grant | 보는 컬럼 |
|---|---|
| `authorization_code` | `scopes` |
| `refresh_token` | (발급 시점의 code scope 를 잇는다 — 컬럼을 다시 보지 않는다) |
| `client_credentials` | `client_scopes` |

### cross-service 계약 — 두 곳만 바뀐다

| record | 변경 | 이유 |
|---|---|---|
| `client-registry/dto/ClientResponse` | 필드 추가 | 소유자 |
| `token/client/ClientInfo` | 필드 추가 | client_credentials 를 처리한다 |
| `auth/client/ClientInfo` | **변경 없음** | 볼 이유가 없다 |

**주의.** 슬라이스 3 리뷰가 "두 record 중 한쪽만 바꾸면 조용히 기본값이 들어간다"를 지적했다. 이번에는 세 record 중 **둘만** 바꾸는 것이 의도이므로, auth 쪽을 바꾸지 않았다는 사실이 검토에서 누락으로 오해되지 않도록 계획서에 명시한다.

### seed 변경

```
my-client
  scopes        = "openid,profile,email,offline_access"
  client_scopes = ""                                      ← 사용자 앱이라 자체 능력 없음
  grant_types   = "authorization_code,refresh_token"

article-api
  scopes        = ""
  client_scopes = "introspect"                            ← 신규
  grant_types   = "client_credentials"                    ← 기존 "" 에서 변경
```

`article-api` 는 슬라이스 3에서 `grant_types` 가 비어 있어 토큰을 받을 수 없었다. 이제 자기 토큰을 받아야 하므로 grant 를 부여한다. 여전히 `redirect_uris` 와 `scopes` 는 비어 있어 인가 흐름에는 참여하지 않는다.

**주의.** `ddl-auto: update` 라 기존 행은 seed 가 갱신하지 않는다. 슬라이스 3 e2e 에서 같은 함정을 겪었으므로 e2e 절차에 확인·보정 단계를 넣는다.

---

## 3. client_credentials grant

```
POST /oauth2/token
Authorization: Basic <client credentials>
grant_type=client_credentials&scope=introspect
```

절차: client 인증 → `grant_types` 에 `client_credentials` 확인 → 요청 scope 검증 → access token 발급.

**scope 규칙**
- 요청 scope 는 `client_scopes` 의 부분집합이어야 한다. 벗어나면 `invalid_scope`
- 생략하면 `client_scopes` 전부를 요청한 것으로 본다 (RFC 6749 §3.3 이 "사전 정의된 기본값" 을 허용하고, authorization_code 경로가 이미 같은 규칙을 쓴다)
- `client_scopes` 가 비어 있는 client 가 이 grant 를 쓰면 `invalid_scope`

**응답에 없는 것 둘**
- **refresh token 없음** (§4.4.3 이 SHOULD NOT). 갱신이 필요하면 자격증명으로 다시 받는다. 사용자가 없으므로 "재로그인 없이 연장"이라는 refresh 의 존재 이유가 성립하지 않는다
- **id token 없음** — 인증한 사용자가 없다

**claim** (RFC 9068)
- `sub` = client_id
- `iss` · `iat` · `exp` · `scope` 는 기존과 동일
- `aud` = client_id — 이 서버가 resource indicator(RFC 8707)를 쓰지 않아 `sub == aud` 가 된다. 알려진 한계로 기록한다

**`AccessTokenIssuer` 를 그대로 쓴다.** 슬라이스 3에서 두 grant 가 공유하도록 추출한 클래스가 세 번째 소비자를 얻는다 — `issue(client.clientId(), client.clientId(), scope)`.

---

## 4. introspection 을 protected resource 로

### 개념 전환

지금 introspection 은 "client 를 인증하는 엔드포인트"다. 이번에 **`/userinfo` 와 같은 성격**이 된다 — access token 을 제시받아 그 권한을 확인하는 protected resource.

```
POST /oauth2/introspect
Authorization: Bearer <client_credentials 로 받은 access token>
token=<검사할 토큰>
```

### 오류는 RFC 6750 을 따른다

| 상황 | 응답 |
|---|---|
| Authorization 헤더 없음, 또는 Basic 제시 | 401 + `WWW-Authenticate: Bearer` |
| 무효 · 만료 토큰 | 401 + `WWW-Authenticate: Bearer error="invalid_token"` |
| 유효하지만 `introspect` scope 없음 | **403 + `WWW-Authenticate: Bearer error="insufficient_scope"`** |
| `token` 파라미터 없음 | 400 `invalid_request` |

`AccessTokenVerifier` 가 이미 있으므로 검증 능력은 재사용한다. **jwks 조회 실패는 `InvalidTokenException` 이 아닌 예외로 전파돼 500 `server_error` 가 된다** — 슬라이스 2·3에서 두 번 다룬 오분류이므로 이번에도 catch 범위를 `InvalidTokenException` 으로 좁게 유지한다.

### revoke 는 Basic 을 유지한다

비대칭이 의도적이다.

- **revoke** — "**자기** 토큰을 폐기한다". 행위 주체가 토큰의 소유자이므로 client 인증이 맞다
- **introspect** — "**남의** 토큰을 검사한다". 소유자가 아닌 자가 하는 일이므로 별도로 부여된 능력(scope)이 맞다

### discovery 의 표현 한계

`introspection_endpoint_auth_methods_supported` 는 RFC 8414 상 **client 인증 방식**(`client_secret_basic` 등)을 담는 필드다. 이제 client 인증을 쓰지 않으므로 담을 값이 없다. `["none"]` 은 "인증 불필요"라는 거짓이 된다.

**그 필드를 제거한다.** 그리고 "Bearer 토큰 + 특정 scope 요구"를 표현할 표준 필드가 없다는 것을 알려진 한계로 기록한다.

`scopes_supported` 에는 `introspect` 를 넣는다. 다만 discovery 메타데이터에는 **사용자 위임 가능 여부를 구분할 방법이 없어**, client 가 이것을 authorization_code 로 요청할 수 있다고 오해할 여지가 있다 — 이것도 알려진 한계다.

`grant_types_supported` 에 `client_credentials` 를 추가한다.
`revocation_endpoint_auth_methods_supported` 는 `["client_secret_basic"]` 그대로 둔다.

**주의.** 기존 discovery 테스트가 `introspection_endpoint_auth_methods_supported[0]` 을 단언하고 있다. 필드를 제거하면 그 단언이 깨지므로, **부재를 단언하도록 바꾼다**(`doesNotExist()`). 단언을 지우는 것이 아니라 새 계약으로 뒤집는 것이다.

### client_credentials 토큰으로 다른 엔드포인트를 부르면

`/userinfo` 는 `openid` scope 를 요구하는데, `openid` 는 `scopes`(사용자 위임) 컬럼에 있으므로 client_credentials 로는 받을 수 없다. 따라서 이 토큰으로 `/userinfo` 를 부르면 403 `insufficient_scope` 다 — 사용자가 없는 토큰으로 사용자 정보를 조회할 수 없다는 것이 자연히 성립한다. 별도 방어 코드가 필요 없다.

---

## 5. 실패 모드

새 원격 호출이 없다. 기존 정책을 그대로 유지한다.

| 호출 | 실패 시 | 방향 |
|---|---|---|
| token → client-registry | 404 → `invalid_client`(401) / 그 외 → `server_error`(500) | fail-closed |
| introspection 의 `AccessTokenVerifier` | jwks 확보 실패 → `server_error`(500), 토큰 문제 → 401 | fail-closed |

---

## 6. 이월 항목 정리 (슬라이스 3에서 미룬 것)

별도 슬라이스감이 아니라 이번에 함께 턴다.

1. `TokenGenerator` 의 `catch (Exception)` 을 `NoSuchAlgorithmException` 으로 좁힌다 — 현재는 null 입력의 NPE 까지 삼켜 `"SHA-256 unavailable"` 로 오도한다
2. 회전으로 만드는 새 행의 `expiresAt` 에 `min(now + ttl, familyExpiresAt)` 를 적용한다 — 지금은 계열 상한을 넘는 값이 응답에 실릴 수 있다
3. `revokeFamily` 에 이미 REVOKED 인 계열의 `revokedReason` 을 덮어쓰지 않는 가드를 넣는다 — `REUSE_DETECTED` 뒤 `CLIENT_REVOKED` 가 오면 탈취 탐지 흔적이 사라진다
4. discovery 테스트의 단일 원소 배열에 `length()` 단언을 넣는다
5. `ClientSeedInitializer` 의 client 별 독립 삽입을 검증하는 테스트를 client-registry 에 추가한다

---

## 7. 테스트 전략

### 단위 테스트 초점

**client_credentials**
- `grant_types` 에 없는 client → `unauthorized_client`
- 요청 scope 가 `client_scopes` 를 벗어남 → `invalid_scope`
- scope 생략 → `client_scopes` 전부가 토큰에 실린다
- `client_scopes` 가 빈 client → `invalid_scope`
- **응답에 `refresh_token` 부재**, **`id_token` 부재**
- `sub` == client_id

**introspection**
- `introspect` scope 를 가진 Bearer → 200 + claim
- **`introspect` 없는 Bearer → 403 `insufficient_scope`**
- Basic 제시 → 401 + `WWW-Authenticate: Bearer`
- 무효 토큰 → 401 `invalid_token`
- jwks 장애 → 500 `server_error` (기존 회귀 테스트 유지)

**회귀**
- `my-client` 가 authorization_code 로 `introspect` 요청 → `invalid_scope`
- client_credentials 토큰으로 `/userinfo` 호출 → 403 `insufficient_scope`
- discovery 에 `introspection_endpoint_auth_methods_supported` **부재**, `grant_types_supported` 에 `client_credentials` 존재
- 슬라이스 4 착수 전 token 96개 → 완료 후 109개, token-state 39개 → 완료 후 42개, auth 테스트 전부 통과

**주의.** 1절은 "새 서비스는 없다" 를 전제했지만, 실제 구현에서는 client-registry 에 **테스트 전용 인프라**가 새로 생겼다 — `build.gradle` 에 `com.h2database:h2` 를 추가하고(최종 리뷰 이후 `testRuntimeOnly` 로 좁혔다 — `runtimeOnly` 는 운영 bootJar 에도 h2 를 실어 보낸다), `src/test/resources/application.yml` 을 신설해 `ddl-auto: create-drop` 의 h2 in-memory DB 를 붙였다. 새 서비스를 추가한 것은 아니므로 1절의 전제와 정면으로 모순되지는 않지만, 계획에는 없던 변경이므로 기록해 둔다.

### e2e 성공 기준

1. `article-api` 가 client_credentials 로 토큰 획득, 응답 `scope` 가 `introspect`
2. 그 토큰으로 **my-client 에게 발급된 access token** 조회 → `active: true` (호출자 ≠ 토큰 주인)
3. Basic 으로 introspect → 401
4. `my-client` 가 client_credentials 시도 → `unauthorized_client`
5. `my-client` 가 authorization_code 로 `introspect` 요청 → `invalid_scope`
6. client_credentials 응답에 `refresh_token` · `id_token` 없음
7. **동의 화면에 `introspect` 가 뜨지 않는다**
8. 회귀 — refresh 회전, 재사용 탐지, revoke, userinfo, code 재사용

---

## 8. 이번 슬라이스에서 제외

- **내부 서비스 인증**(API 키 · mTLS) — 슬라이스 5
- private_key_jwt · mTLS client 인증
- resource indicators (RFC 8707) — `aud` 가 client_id 로 고정되는 원인
- `introspect` 외의 client 능력 scope
- back-channel logout, Kafka 인증 이벤트 스트림
