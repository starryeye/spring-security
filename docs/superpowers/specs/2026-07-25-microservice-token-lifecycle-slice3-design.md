# 마이크로서비스 인가 서버 — 슬라이스 3 설계: 토큰 수명 관리

refresh token 회전 · 재사용 탐지 · introspection · revocation.

대상: `oauth-2/authorization-server/practice/microservice/`
선행: 슬라이스 1(authorization code + PKCE, 6개 바이너리), 슬라이스 2(OIDC — id token · userinfo · consent 분리, 7개 바이너리)

## 목표

지금 이 인가 서버에는 토큰 수명 관리가 없다. access token 5분이 전부이고, 만료되면 사용자가 다시 로그인해야 하며, 발급된 권한을 되돌릴 방법이 없다. 이 슬라이스가 채우는 것:

- **refresh token** — 재로그인 없이 access token 재발급 (RFC 6749 §6)
- **회전 + 재사용 탐지** — 탈취된 refresh token 으로 조용히 지속 접근하는 것을 끊는다 (OAuth 2.1 · Security BCP)
- **introspection** — 불투명한 refresh token 의 상태를 물어볼 곳 (RFC 7662)
- **revocation** — 부여한 권한을 명시적으로 끝내는 수단 (RFC 7009)

분해 관점의 주제는 하나다. **상태 소유권을 지키면서 원자성을 잃지 않는 법.**

---

## 1. 아키텍처 · 서비스 경계

### token-state (8087) 신설

8번째 바이너리. `refresh_tokens` 테이블 하나를 소유하고 refresh token 의 계열(family)과 폐기 상태를 관리한다. 외부 노출 없음 — `/internal/**` 전용이고 gateway 에 등장하지 않는다(consent 와 동일한 격리).

token-state 는 **아무도 부르지 않는 순수 데이터 소유자**다. client 검증도 서명 위임도 하지 않는다. user-directory · consent 와 같은 성격이다.

```
auth(8081)  ─ 동의된 scope 를 code 에 실어줄 뿐, refresh token 을 모른다
token(8082) ─REST─▶ token-state(8087)   발급 · 회전 · 폐기 · 조회
token(8082) ─REST─▶ client-registry(8085)   client 인증 (기존 경로 그대로)
```

### gateway 외부 경로 추가

| 경로 | RFC | 인증 |
|---|---|---|
| `POST /oauth2/introspect` | 7662 | Basic(client) — 인증된 등록 client 면 허용 |
| `POST /oauth2/revoke` | 7009 | Basic(client), 자기 토큰만 실제 폐기 |

`/oauth2/token` 은 기존 경로에 `grant_type=refresh_token` 이 추가되는 것이라 라우팅 변경이 없다.

### 서비스별 책임

- **auth** — `offline_access` 를 다른 scope 와 똑같이 취급한다. client 허용 목록에 있어야 하고, 동의 화면에 체크박스로 뜨고, 승인되면 code 의 scope 에 실린다. refresh token 자체는 모른다.
- **token** — 발급 판단, 회전 요청, introspect · revoke 엔드포인트 호스팅.
- **token-state** — 상태 소유와 모든 상태 전이 판단.

---

## 2. 데이터 모델

### `refresh_tokens` (token-state 소유, MySQL)

| 컬럼 | 제약 | 설명 |
|---|---|---|
| `id` | PK | |
| `token_hash` | unique, indexed | SHA-256. **원문을 저장하지 않는다** |
| `family_id` | indexed | 회전 사슬 전체가 공유하는 UUID |
| `client_id` | not null | |
| `sub` | not null | |
| `scope` | not null | comma 구분 |
| `auth_time` | not null | 원래 인증 시각 (epoch second) |
| `status` | not null | `ACTIVE` / `CONSUMED` / `REVOKED` |
| `issued_at` | not null | |
| `expires_at` | not null | 회전마다 새로 계산 (sliding) |
| `family_expires_at` | not null | 계열 절대 상한. 회전 시 그대로 복사 |
| `consumed_at` | nullable | 회전으로 소진된 시각 |
| `revoked_at` | nullable | |
| `revoked_reason` | nullable | `REUSE_DETECTED` / `CLIENT_REVOKED` |

**주의.** `token_hash` 는 SHA-256 이고 salt 가 없다. 조회가 결정적이어야 하므로 bcrypt 계열은 쓸 수 없다. 원문이 128비트 이상의 난수라 사전 공격 대상이 아니어서 성립하는 선택이며, 사용자 비밀번호에는 같은 논리를 적용할 수 없다.

**주의.** DB 에는 comma 로 저장하고 OAuth 와이어 포맷은 공백 구분이다. 변환은 경계(컨트롤러)에서 한 번만 한다. 기존 서비스(client-registry · consent)의 저장 관례를 따른 것이다.

**설계 판단 — 테이블 하나로 간다.** 별도 `refresh_token_families` 테이블이 더 정규화되지만, 계열에 필요한 연산이 `WHERE family_id = ?` 하나뿐이라 값을 하지 않는다. `family_expires_at` 만 회전 시 복사하는 비정규화로 끝낸다.

**설계 판단 — `parent_id` 를 두지 않는다.** 폐기에 필요한 것은 계열 식별자뿐이고, 사슬 순서는 `issued_at` 으로 나온다.

### `clients` — 스키마 변경 없음

introspection · revocation 모두 **client 인증만 요구하고 별도 권한 컬럼을 두지 않는다.** Spring Authorization Server 와 Keycloak 이 실제로 그렇게 동작한다 — RFC 7662 는 "엔드포인트를 보호하라"고만 하고 인가 방식은 정하지 않는다.

**주의.** 인증된 client 면 **누구의 토큰이든** introspect 할 수 있다는 뜻이다. 큰 시스템에서는 이 권한을 좁히는데, 정석은 client_credentials grant 로 받은 access token 에 `introspect` scope 를 실어 확인하는 방식이다. 이 시스템에는 client_credentials grant 가 아직 없어 다음 슬라이스로 미뤘다.

revoke 는 자기 토큰만 실제로 폐기한다(§5.3).

### 설정값 (token-state)

```
my.refresh-token-ttl-seconds: 1209600    # 14일, 회전마다 갱신
my.refresh-family-max-seconds: 2592000   # 30일, 절대 상한
```

설정으로 빼서 단위 테스트가 짧은 값으로 만료를 검증할 수 있게 한다.

### seed 데이터

- `my-client` 의 `grant_types` 에 `refresh_token` 추가, `scopes` 에 `offline_access` 추가
- resource server 역할을 시연할 client 하나 신규 seed: `article-api` / secret

`article-api` 는 인가 모델상 필수가 아니다(인증된 client 면 누구나 introspect 할 수 있으므로). **호출자와 토큰 주인이 다른** 실제 introspection 상황을 e2e 로 보이기 위해 둔다.

인가 흐름에 참여하지 않으므로 `redirect_uris` · `scopes` · `grant_types` 를 **빈 문자열**로 둔다. 그러면 기존 `grant_types` 검사가 이 client 의 모든 토큰 요청을 자연히 거절하는데, 부작용이 아니라 원하는 동작이다 — 토큰을 발급받을 수 없고 남의 토큰을 조회만 하는 주체다.

---

## 3. 두 개의 관문

refresh token 을 둘러싼 검사는 **서로 다른 질문에 답하는 두 개**이고, 시점도 다르다.

| | 질문 | 근거 | 검사 시점 |
|---|---|---|---|
| `grant_types` 에 `refresh_token` | 이 client 가 refresh grant 를 **쓸 수 있나** | 관리자가 정한 client 등록 속성 | 발급 시 + 사용 시 |
| `offline_access` 동의 | 이 사용자가 장기 재사용을 **허락했나** | 사용자 동의 | 발급 시 |

**발급(code 교환)은 둘 다 통과해야 한다.** client 가 refresh grant 를 못 쓰는데 발급하면 평생 쓸 수 없는 토큰을 쥐여주는 것이고, 사용자가 동의하지 않았는데 발급하면 사용자 모르게 장기 접근권이 생긴다.

**사용(refresh grant 요청)은 `grant_types` 만 본다.** 이미 발급된 토큰의 동의 여부는 발급 시점에 확정된 것이다.

Keycloak · Spring Authorization Server 가 같은 구조를 쓴다.

---

## 4. token-state 내부 API

**설계 원칙: 판단을 소유자 쪽에 둔다.** token 이 "조회 → 판단 → 갱신"을 하면 왕복 사이에 경쟁 창이 생겨 재사용 탐지가 무력해진다. 연산 하나를 **한 번의 호출**로 표현한다.

### 4.1 발급

```
POST /internal/refresh-tokens
{ "clientId": str, "sub": str, "scope": str, "authTime": long }
→ 200 { "refreshToken": str, "expiresAt": long, "familyId": str }
```

원문은 token-state 가 생성하고 **이 응답에서만** 나온다. 저장은 해시라서 이후 어디서도 되꺼낼 수 없다.

### 4.2 회전 (핵심)

```
POST /internal/refresh-tokens/rotate
{ "refreshToken": str, "clientId": str, "requestedScope": str? }
→ 200 { "status": str, "sub": str, "scope": str, "authTime": long,
        "refreshToken": str, "expiresAt": long }
```

`requestedScope` 는 선택이다(RFC 6749 §6 의 축소 요청). 없으면(null · 빈 문자열) 축소하지 않는다. 한 트랜잭션 안에서 판정과 전이를 모두 수행한다.

| 조회 결과 | status | 부수 효과 |
|---|---|---|
| ACTIVE · 미만료 · client 일치 · scope 요청이 저장 범위 이내 | `ROTATED` | 기존 → CONSUMED, 같은 계열로 새 row insert |
| **CONSUMED** (이미 소진된 토큰이 다시 옴) | `REUSE_DETECTED` | **계열 전체 REVOKED** (`revoked_reason=REUSE_DETECTED`) |
| REVOKED | `REVOKED` | 없음 |
| 만료 또는 `family_expires_at` 초과 | `EXPIRED` | 없음 |
| 해시 미존재 | `NOT_FOUND` | 없음 |
| client 불일치 | `CLIENT_MISMATCH` | 없음 |
| `requestedScope` 가 저장된 scope 를 벗어남 | `SCOPE_EXCEEDED` | 없음 — 판정은 다른 모든 거절 사유 뒤, 상태 전이 직전에 한다 |

`ROTATED` 외의 응답에서 `refreshToken` 등 나머지 필드는 채우지 않는다.

**주의.** `SCOPE_EXCEEDED` 검사를 회전 이후로 미루면(예: token 서비스가 회전 응답을 받은 뒤 저장 scope 와 비교) 이미 이전 토큰이 CONSUMED 된 뒤라 새 토큰 원문을 버리게 된다 — 원문은 그 회전 응답에만 있으므로 grant 자체가 회수 불가능해진다. 그래서 검사를 token-state 안, 회전과 같은 트랜잭션에 둔다.

### 4.3 폐기

```
POST /internal/refresh-tokens/revoke
{ "refreshToken": str, "clientId": str }
→ 200 { "revoked": bool }
```

**계열 전체를 폐기한다.** refresh token 하나는 하나의 grant 를 대표하므로 폐기는 그 grant 를 끝내는 것이다(RFC 7009 §2.1 의 취지). client 가 일치하지 않으면 아무것도 하지 않고 `revoked: false`.

### 4.4 조회

```
POST /internal/refresh-tokens/introspect
{ "refreshToken": str }
→ 200 { "active": bool, "sub": str, "clientId": str, "scope": str,
        "exp": long, "iat": long }
```

`active: false` 면 나머지 필드를 채우지 않는다.

---

## 5. 외부 엔드포인트

### 5.1 refresh grant — `POST /oauth2/token`

```
Authorization: Basic <client credentials>
grant_type=refresh_token&refresh_token=<token>&scope=<선택, 축소 요청>
```

절차: client 인증 → `grant_types` 에 `refresh_token` 있는지 → token-state 회전 → 새 access token(+ id token).

**scope 축소** (RFC 6749 §6) — `scope` 파라미터는 저장된 scope 의 부분집합만 허용한다. 벗어나면 `invalid_scope`. **이 검증은 token 이 아니라 token-state 가 회전과 같은 트랜잭션 안에서 한다**(§4.2 `SCOPE_EXCEEDED`) — token 은 `requestedScope` 를 그대로 token-state 에 넘기기만 하고 직접 거르지 않는다. 검사 시점을 회전 이후로 미루면 이미 이전 토큰이 소진된 뒤라 새 토큰 원문(그 회전 응답에만 있다)을 버리게 되어 grant 를 통째로 잃는다. `SCOPE_EXCEEDED` 가 나오면 token-state 는 아무 상태도 바꾸지 않으므로, client 는 같은 refresh token 으로 올바른 scope 를 다시 보낼 수 있다. 축소는 **이번 access token 에만** 적용하고, 회전으로 새로 만들어지는 refresh row 는 **원래 scope 를 그대로 복사**한다. 아니면 한 번의 축소가 영구화된다.

id token 발급 여부는 이번 응답의 **유효 scope**(`effectiveScope` — 축소 요청이 있으면 그 값, 없으면 저장된 scope) 기준으로 판단한다. 축소 요청에서 `openid` 을 뺐다면 유효 scope 에 `openid` 가 없으므로 이번 응답에 id token 을 넣지 않지만, 저장된 refresh 의 scope 자체는 바뀌지 않으므로 다음 회전(축소 없이)에서는 다시 낼 수 있다.

**id token 재발급** — `openid` 이 scope 에 있으면 새 id token 을 함께 낸다. OIDC Core §12.2 가 규정하는 것:

- `sub` · `iss` · `aud` 동일, `iat` · `exp` 는 새 값
- **`nonce` 를 넣지 않는다** — 원래 인증 때 있었더라도. nonce 는 그 authorization 요청에 묶인 값이라 재발급 토큰에 실으면 리플레이 방어가 무너진다
- **`auth_time` 은 원래 인증 시각 그대로** — refresh row 의 `auth_time` 을 쓴다
- `at_hash` 는 새 access token 기준으로 다시 계산

**오류 응답** — token-state 의 `REUSE_DETECTED` · `REVOKED` · `EXPIRED` · `NOT_FOUND` · `CLIENT_MISMATCH` 를 **전부 `invalid_grant` 하나로 뭉갠다.** 구분해주면 "이건 이미 소진됐다" vs "이건 없다"를 알려주는 셈이라 탐색(probing)을 돕는다. 구분은 서버 로그와 감사에만 남긴다.

### 5.2 introspection — `POST /oauth2/introspect` (RFC 7662)

```
Authorization: Basic <client credentials>
token=<token>&token_type_hint=<선택>
```

**`token_type_hint` 는 힌트일 뿐이라 틀릴 수 있다.** JWT 파싱을 먼저 시도하고 실패하면 token-state 에 묻는다. 힌트 없이도 정확히 동작해야 한다.

- **access token(JWT)** — 로컬에서 서명 · `exp` · `iss` 만 검증한다. **token-state 를 조회하지 않는다.** 폐기를 refresh 한정으로 정한 결정의 직접적 귀결이다
- **refresh token** — token-state 조회
- **활성 응답(access token)** — `active`, `scope`, `client_id`, `sub`, `exp`, `iat`, `iss`, `token_type: "Bearer"`
- **활성 응답(refresh token)** — 위에서 `token_type` 을 뺀 것. `token_type` 은 RFC 6749 §7.1 이 정의하는 **access token 의 사용 방식**이라 refresh token 에는 의미가 없다
- **비활성 응답** — `{"active": false}` **그 이상 아무것도 주지 않는다**(RFC 7662 §2.2). 만료 · 폐기 · 형식 오류가 전부 같은 응답이라 구분이 새지 않는다

**조회 권한의 범위** — **인증된 등록 client 면 누구의 토큰이든** 조회할 수 있다. 이는 resource server 의 본래 상황이 "호출자와 토큰 주인이 다른" 것이기 때문이다. article-api 는 자기 앞으로 발급된 토큰을 가진 적이 없고, 검사 대상은 언제나 my-client 가 받아서 들고 온 토큰이다. 조회를 "자기 토큰만"으로 제한하면 resource server 는 아무것도 조회할 수 없다.

client 인증 실패(잘못된 secret · 미등록 client · Basic 헤더 없음)는 401 `invalid_client` 다.

**주의.** 이 정책은 인증된 client 를 전부 신뢰한다. 상용 구현(SAS · Keycloak)도 같지만, 권한을 좁히려면 client_credentials grant 로 받은 토큰의 `introspect` scope 를 확인하는 것이 정석이다.

### 5.3 revocation — `POST /oauth2/revoke` (RFC 7009)

```
Authorization: Basic <client credentials>
token=<token>&token_type_hint=<선택>
```

- **항상 200** — 존재하지 않는 토큰에도(§2.2). 표준이 정보 노출을 막는 방식이다
- **타 client 의 토큰도 200 무동작** — §2.1 은 거절을 허용하지만 §2.2 의 "invalid token → 200" 을 적용해 탐색을 막는다. SAS · Keycloak 동일
- **refresh token → 계열 전체 폐기**
- **access token 힌트 → 200 무동작.** §2 가 access token 폐기를 **MAY** 로 두므로 표준 위반이 아니다. 이 서버는 access token 을 폐기하지 않는다

### 5.4 discovery 갱신

```
grant_types_supported          += "refresh_token"
scopes_supported               += "offline_access"
introspection_endpoint            {issuer}/oauth2/introspect
revocation_endpoint               {issuer}/oauth2/revoke
introspection_endpoint_auth_methods_supported  ["client_secret_basic"]
revocation_endpoint_auth_methods_supported     ["client_secret_basic"]
```

---

## 6. 흐름

### 6.1 발급

1. client 가 `scope=openid profile email offline_access` 로 authorize
2. auth 가 `offline_access` 를 다른 scope 와 동일하게 처리 — client 허용 목록 확인, 동의 화면에 체크박스, 승인분을 code 에 적재
3. token 이 code 교환 시 확인: `offline_access` ∈ scope **AND** `refresh_token` ∈ client.grantTypes
4. 충족하면 token-state 에 발급 요청, 응답의 `refresh_token` 을 토큰 응답에 포함

### 6.2 회전

1. client 가 `grant_type=refresh_token` 제출
2. token-state 가 한 트랜잭션으로 소진 · 재발급
3. token 이 새 access token(+ id token) 과 **새 refresh token** 을 반환
4. 이전 refresh token 은 즉시 무효

### 6.3 탈취 탐지

공격자가 훔친 refresh token 을 사용하면, 정상 사용자의 다음 회전이 `CONSUMED` 를 만난다(또는 그 반대 순서). 어느 쪽이든 **계열 전체가 폐기**되고 양쪽 다 재인증으로 떨어진다. 조용한 지속 접근을 끊는 것이 목적이며, 정상 사용자가 한 번 불편해지는 것은 의도된 대가다.

---

## 7. 실패 모드

| 호출 | 실패 시 | 방향 | 근거 |
|---|---|---|---|
| token → token-state (발급) | 500 `server_error` | fail-closed | access token 만 주고 넘어가면 client 가 refresh 없이 사는 것을 모른다 |
| token → token-state (회전) | 500 `server_error` | fail-closed | 상태를 바꾸지 못했으면 토큰을 주지 않는다 |
| token → token-state (폐기) | 500 `server_error` | fail-closed | 폐기 실패를 성공으로 보고하면 안 된다 |
| token → token-state (introspect) | 500 `server_error` | fail-closed | `{"active":false}` 로 degrade 하면 **살아있는 토큰을 죽었다고 말하는** 것이라 더 나쁘다 |
| token → client-registry (introspect 권한) | 기존 정책 유지 | fail-closed | 404 → `invalid_client`, 그 외 → `server_error` |

**주의.** 회전의 정확성은 **행 잠금**이 만든다. 같은 refresh token 으로 동시 요청 두 건이 들어올 때, 조회를 `SELECT ... FOR UPDATE`(JPA `PESSIMISTIC_WRITE`)로 하지 않으면 두 트랜잭션이 모두 `ACTIVE` 를 읽고 **둘 다 회전에 성공**한다. 새로 만드는 행의 `token_hash` 는 서로 다른 난수라 unique 제약에도 걸리지 않아 조용히 통과한다. 잠금이 있어야 하나는 `ROTATED`, 다른 하나는 `CONSUMED` 를 보고 `REUSE_DETECTED` 로 이어진다.

**주의.** 잠금 대상은 행 하나가 아니라 **계열 전체**다. familyId 를 알아내는 첫 조회(`findFamilyIdByTokenHash`)는 잠금 없이 하고, `findByFamilyIdForUpdate` 로 계열의 모든 행을 한 번에 `PESSIMISTIC_WRITE` 로 잠근 뒤 대상 토큰 행을 그 잠긴 결과 안에서 다시 찾아 판정한다(rotate · revoke 공통). **모든 호출이 "계열 전체 → 그 안의 대상 행" 이라는 한 가지 순서만 써야 한다.** 대상 행 하나만 먼저 잠그고 그다음 계열을 잠그는 경로가 하나라도 남아 있으면, 계열을 통째로 먼저 잠그는 다른 트랜잭션과 서로 상대가 쥔 잠금을 기다리는 교착이 가능해진다.

**주의.** 첫 조회는 반드시 엔티티를 로드하지 않는 **스칼라 프로젝션**(`findFamilyIdByTokenHash`)이어야 한다. "행 잠금을 걸었다"와 "잠긴 상태로 판정한다"는 JPA 에서 같은 말이 아니다 — 이 슬라이스에서 실제로 부딪힌 함정이다. `findByTokenHash` 로 엔티티를 먼저 로드하면 그 인스턴스가 영속성 컨텍스트에 managed 로 남고, 뒤이은 `findByFamilyIdForUpdate` 가 SQL 로는 `FOR UPDATE` 잠금을 실제로 걸고 최신 행을 읽어와도 Hibernate 는 이미 있는 인스턴스를 그대로 돌려주며 필드를 갱신하지 않는다. 그러면 잠금은 걸렸는데 판정은 잠금 획득 이전 스냅샷으로 하게 되어, 동시 회전에서 두 요청이 모두 stale 한 상태(예: 둘 다 `ACTIVE`)를 보고 재사용 탐지가 통째로 무력해진다. `RefreshTokenServiceConcurrentRotateTest`(§8)에서 첫 조회를 `findByTokenHash` 로 되돌려 재현·확인했다.

**주의.** 그 결과 **정상 client 의 재시도도 계열을 죽인다.** 결함이 아니라 회전의 알려진 대가이며, client 는 refresh 요청을 직렬화해야 한다.

**주의.** 회전은 이전 토큰을 즉시 무효화하므로, client 가 새 refresh token 을 저장하기 전에 죽으면 그 계열을 잃는다. 짧은 유예(grace period)를 두는 구현도 있으나 재사용 탐지의 정확도와 맞바꾸는 선택이라 이 슬라이스에서는 두지 않는다.

---

## 8. 테스트 전략

### 단위 테스트 초점

- 회전 후 이전 토큰 재사용 → **계열 전체가 REVOKED 인지 DB 상태로 단언** (응답 status 만 보지 않는다)
- 같은 토큰으로 **동시 회전 두 건**(`RefreshTokenServiceConcurrentRotateTest`) → 정확히 하나만 `ROTATED`, 다른 하나는 `REUSE_DETECTED` 이고 제출된 토큰이 DB 상 REVOKED(`revoked_reason=REUSE_DETECTED`)임을 확인한다. `TokenGenerator.hash` 에 장벽을 세워 두 트랜잭션이 각자의 첫 조회를 마친 뒤에야 잠금 경쟁에 들어가도록 인터리빙을 강제한다.
  **한계.** "계열 전 행이 REVOKED" 는 여기서 단언하지 않는다. h2 에서 관찰되는 동작은 이렇다 — 늦게 잠근 쪽의 차단됐던 `SELECT ... FOR UPDATE` 가 풀릴 때, **기다리던 그 행 자체는 다시 읽어 최신 커밋 상태(CONSUMED)를 반영한다**(그래서 REUSE_DETECTED 로 판정할 수 있다). 하지만 **대기하는 동안 다른 트랜잭션이 새로 삽입·커밋한 형제 행까지 이번 조회의 행 집합에 포함하지는 않는다** — 그래서 이 테스트에서는 그 형제 행 하나가 ACTIVE 로 남을 수 있다. 이것은 구현의 결함이 아니라 테스트 DB(h2)에서 관찰된 잠금 의미론이다. (InnoDB 의 locking read 가 이 경우 형제 행까지 다시 읽어와 잠그는지는 **검증하지 않았다** — MySQL 로 이 테스트를 재실행해 확인한 적이 없으므로 단정하지 않는다.) "**잠글 때 이미 존재하던** 행 전부가 폐기된다"는 이미 커밋된 행으로만 구성해 h2 에서도 성립하는 위 단일 스레드 테스트(`reusingConsumedTokenAfterMultipleRotationsRevokesEveryFamilyMember`)가 고정한다. 반면 "**대기 중 다른 트랜잭션이 삽입한** 형제 행도 폐기된다"는 어느 테스트도 덮지 않는다. 이 동시성 테스트가 실제로 고정하는 것은 "회전이 정확히 한 번만 일어났다"(계열 행 개수가 3이 아니라 2)와 "제출된 토큰이 재사용으로 폐기됐다" 두 가지뿐이다.
- `family_expires_at` 초과 → `EXPIRED` (개별 `expires_at` 은 아직 유효한 상태로 구성해 절대 상한이 실제로 동작하는지 격리 검증)
- client 불일치 → `CLIENT_MISMATCH`, 상태 변화 없음
- scope 축소 요청이 **저장된 refresh 의 scope 를 바꾸지 않음**
- **refresh 로 받은 id token 에 `nonce` 부재, `auth_time` 이 원본과 동일**
- introspect 비활성 응답이 `{"active": false}` 단일 키인지 (필드가 새지 않는지)
- revoke 가 미존재 토큰 · 타 client 토큰에도 200
- token-state 장애 시 refresh grant 가 `server_error` (fail-closed)

### e2e 성공 기준

1. 동의 화면에 `offline_access` 체크박스가 뜨고, 체크하면 토큰 응답에 `refresh_token` 이 포함된다
2. 체크하지 않으면 `refresh_token` 이 없다
3. refresh grant 로 새 access token + 새 refresh token 을 받는다
4. 새 id token 에 `nonce` 가 없고 `auth_time` 이 최초 로그인 값과 같다
5. 이전 refresh token 재사용 → `invalid_grant`, DB 에서 계열 전체가 REVOKED
6. revoke 후 그 계열의 refresh 로 회전 시도 → `invalid_grant`
7. introspect — 살아있는 refresh 는 `active: true` + 필드, 폐기된 것은 `{"active": false}` 뿐
8. `article-api` 자격증명으로 **my-client 에게 발급된** 토큰을 introspect → `active: true` (호출자 ≠ 토큰 주인). 잘못된 secret → 401 `invalid_client`
9. 회귀 — 슬라이스 1 · 2 동작(code 재사용, PKCE, userinfo, 동의) 유지

---

## 9. 이번 슬라이스 제외

다음 슬라이스 대상이며 여기서는 구현하지 않는다.

- 내부 서비스 인증(API 키 · mTLS)
- Kafka 인증 이벤트 스트림
- back-channel logout (`sid`, 세션 추적)
- jwks 캐시 (슬라이스 2 의 알려진 한계)
- access token 폐기(deny-list) — 폐기를 refresh 한정으로 정한 결정에 따라 범위 밖
- **client_credentials grant + `introspect` scope** — introspection 인가를 좁히는 정석. 이번에는 "인증된 client 면 허용"으로 두고 다음 슬라이스 후보로 남긴다
- `typ: at+jwt` 구분, access token `scope` claim 의 RFC 9068 문자열화
- device grant · CIBA · private_key_jwt
