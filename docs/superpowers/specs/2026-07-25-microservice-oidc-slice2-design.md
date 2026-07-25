# microservice authorization server — 슬라이스 2 (OIDC 확장) 설계

**작성일**: 2026-07-25
**대상**: `oauth-2/authorization-server/practice/microservice/`
**선행 슬라이스**: [슬라이스 1 설계](2026-07-18-microservice-authorization-server-slice1-design.md) — authorization code + PKCE 를 6개 서비스로 관통 (완료, 커밋 6328138..e5e9355)

## 목표

슬라이스 1 의 OAuth 골격 위에 **OpenID Connect 인증 계층**을 얹는다. 세 기능을 한 슬라이스로 다룬다.

1. **id token 발급** — `openid` scope 요청 시 token flow 에서 함께 발급
2. **userinfo 엔드포인트** — access token 으로 인증하고 scope 에 따라 필터링된 사용자 claim 반환
3. **consent** — 동의 기록을 소유하는 **7번째 서비스**로 분리, 동의 화면은 auth 가 렌더

부수 목표: 서비스가 하나 늘고 서비스 간 호출이 늘어나는 상황에서 **경계와 실패 모드가 어떻게 달라지는지**를 실증한다.

## 서비스 구성 (변경분)

```
gateway(9000) ─┬─ /oauth2/authorize, /login, /oauth2/consent ─▶ auth(8081)
               ├─ /oauth2/token, /oauth2/jwks, /.well-known    ─▶ token(8082)
               └─ /userinfo                                     ─▶ token(8082)   [신규 라우팅]

내부(게이트웨이 비노출):
  auth  ─▶ consent(8086)         동의 조회/저장 (REST)          [신규 서비스]
  auth  ─▶ user-directory(8084)  로그인 credential 검증 (기존)
  auth  ─▶ client-registry(8085) client 조회 (기존)
  auth  ─▶ Redis                 authorization code, pending authorization
  token ─▶ user-directory(8084)  프로필 claim 조회               [신규 경로]
  token ─▶ client-registry(8085) client 인증 (기존)
  token ─▶ signing(8083)         JWT 서명 (기존, id token 도 동일 API 재사용)
  token ─▶ Redis                 code 원자 소비 (기존)
```

| 서비스 | 변경 | 내용 |
|---|---|---|
| **consent (8086)** | **신규** | 동의 기록의 소유자. MySQL `consents` 테이블. 내부 REST API 만 제공(화면 없음) |
| auth (8081) | 확장 | 동의 화면 렌더/제출 처리, consent 서비스 REST 질의, pending authorization 관리 |
| token (8082) | 확장 | id token 발급, `/userinfo` 엔드포인트, access token 자체 검증 |
| user-directory (8084) | 확장 | 프로필 필드 추가(profile/email scope 대응), 조회 응답 확장 |
| signing (8083) | 변경 없음 | id token 서명도 기존 `/internal/sign` 사용 |
| client-registry (8085) | 변경 없음 | (seed 의 client scope 에 `email` 추가만) |
| gateway | 설정 | `/userinfo` → token, `/oauth2/consent` → auth 라우팅 추가 |

### 설계 근거

- **consent 를 분리한 이유**: 동의 기록은 사용자·client 와 나란한 독립 도메인 데이터다. user-directory(사용자 소유), client-registry(client 소유)와 같은 성격의 내부 데이터 서비스가 되어 아키텍처가 대칭이 된다.
- **동의 화면은 auth 가 그리는 이유**: 화면은 로그인 세션과 진행 중 인가 맥락(front-channel)이 있어야 그릴 수 있다. consent 를 화면까지 가진 서비스로 만들면 auth ↔ consent 간 redirect 왕복과 세션 공유 문제가 생긴다. **기록의 소유(consent)와 화면의 소유(auth)를 분리**한다.
- **userinfo 가 token 서비스에 있는 이유**: userinfo 의 본질은 "access token 검증 + scope 기반 claim 필터링"이다. 토큰 검증(jwks·서명·만료)은 token 서비스의 기존 역량이므로, 별도 서비스로 빼면 그 검증 로직이 통째로 중복된다.
- **user-directory 를 외부에 열지 않는 이유**: user-directory 는 인증 없는 내부 API 다. 외부 노출은 사용자 데이터를 무방비로 여는 것이며, userinfo 는 그 앞에서 토큰 검증과 필터링을 수행하는 **공개 계층**이다.

## 데이터 흐름

### A. authorize + 동의

```
① GET /oauth2/authorize (인증된 세션)
② auth → client-registry: client 조회                          (기존)
③ auth 검증: redirect_uri 정확일치 → grantTypes → response_type/PKCE/scope   (기존)
④ auth → consent: GET /internal/consents/{sub}/{clientId}      [신규]
   ├─ 요청 scope ⊆ 승인된 scope → 동의 생략, 바로 code 발급
   └─ 미승인 scope 존재 → pending 저장 후 동의 화면 렌더
⑤ POST /oauth2/consent (사용자 제출, pending_id + 승인 scope)   [신규]
   ├─ pending 조회(없거나 만료 → 400 에러 페이지)
   ├─ 승인 scope = 제출 scope ∩ pending.scope   (폼 조작 방어)
   ├─ 승인이 하나도 없으면 → redirect_uri?error=access_denied
   ├─ auth → consent: POST /internal/consents (승인 scope 저장/병합)
   └─ code 발급(Redis) → redirect_uri?code=&state=
```

**pending authorization** — 동의 화면을 거치는 동안 진행 중 인가 요청을 서버에 보관한다.
- key `auth:pending:{id}`, TTL 300초
- value `{clientId, redirectUri, scope, sub, codeChallenge, state, nonce, authTime}`
- 폼에는 불투명한 `pending_id` 만 노출한다. client_id·redirect_uri·scope 를 hidden 필드로 흘리면 사용자가 조작할 수 있다(scope 상향, redirect_uri 변조).
- **표준이 정한 방식이 아니라 구현 선택이다.** OIDC 는 동의 화면 상태 유지 방법을 규정하지 않는다. 다만 같은 패턴이 널리 쓰인다 — SAS 는 진행 중 authorization 을 저장소에 두고 내부 `state` 파라미터로 조회하며(client 의 state 와 다른 값), keycloak 은 authentication session 에 두고 불투명한 tab id 를 노출한다.

### B. token 교환 + id token

```
기존 절차(client 인증 → code 원자 소비 → 바인딩 → PKCE) 후:
⑥ access token 서명                                            (기존)
⑦ code 의 scope 에 openid 포함 시:                              [신규]
   ├─ token → user-directory: GET /internal/users/{sub}
   ├─ id token claims 구성 (아래 표)
   ├─ token → signing: POST /internal/sign (기존 API 재사용)
   └─ 응답에 id_token 추가
```

**authorize 시점 값의 전달** — `nonce` 와 `auth_time` 은 authorize 시점(auth)에만 알 수 있는데 필요한 곳은 token 시점이다. client 가 token 요청에 실어 보내게 하면 조작 가능해 의미가 없으므로, **code 레코드에 함께 보관해 서버끼리만 전달**한다.

```
슬라이스1 계약: auth:code:{code} = {clientId, redirectUri, scope, sub, codeChallenge}
슬라이스2 확장: auth:code:{code} = {..., nonce, authTime}
```

표준은 "id token 에 nonce/auth_time 이 규칙대로 담길 것"을 요구하고, 나르는 방법은 규정하지 않는다(구현 선택). SAS 도 동일 개념으로 동작한다 — `JwtGenerator` 가 저장된 `OAuth2AuthorizationRequest.getAdditionalParameters()` 에서 `nonce` 를 읽어 id token 에 넣는 것을 바이트코드로 확인했다.

슬라이스 1 최종 리뷰에서 cross-service record 에 `@JsonIgnoreProperties(ignoreUnknown = true)` 를 넣어둔 덕분에 이 필드 추가는 배포 순서와 무관하게 안전하다.

### C. userinfo

```
client → gateway → token: GET 또는 POST /userinfo (Authorization: Bearer {access_token})
① 토큰 추출 — Authorization 헤더 우선, 없으면 form-encoded POST 의 access_token 파라미터
② access token 검증 — 서명(signing jwks), exp, iss
③ openid scope 보유 확인 (없으면 403 insufficient_scope)
④ scope 에 profile/email 이 있을 때만 토큰의 sub 로 user-directory 조회
⑤ scope 기반 필터링 후 application/json 반환 (sub 는 항상 포함)
```

## id token claim (표준 준수)

| claim | 값 | 표준 요건 |
|---|---|---|
| `iss` | issuer (`http://localhost:9000`) | 필수 |
| `sub` | code 의 sub | 필수 |
| `aud` | client_id | 필수 |
| `exp` | 발급시각 + 300초 | 필수 |
| `iat` | 발급시각 | 필수 |
| `nonce` | authorize 요청의 값 그대로 | 요청에 있었으면 필수 |
| `auth_time` | **authorize 요청을 처리한 시각**(epoch seconds) | max_age·essential 시 필수 — 이 구현은 항상 포함(허용됨) |
| `at_hash` | BASE64URL(SHA-256(access_token) 의 좌측 128비트) | RS256(alg) 기준 |
| `name`, `nickname`, `preferred_username` | scope 에 `profile` 포함 시 | scope 대응 |
| `email`, `email_verified` | scope 에 `email` 포함 시 | scope 대응 |

**제외**(표준 필수 아님, 이번 슬라이스 범위 밖): `sid`(back-channel logout 용), `azp`(단일 aud 이므로 불필요), `acr`/`amr`.

**주의.** 표준의 `auth_time` 은 "최종 사용자가 실제로 인증된 시각"이다. 이 구현은 `AuthorizeController` 가 authorize 요청을
처리한 시각(`Instant.now()`)을 넣으므로 둘이 다르다. 로그인 시각을 세션에 보관하지 않기 때문이다.
SSO 재사용(로그인은 예전에 했고 이번엔 세션만 재사용)에서 `auth_time` 이 authorize 마다 갱신되는 것으로 보이므로,
RP 가 `max_age` 로 재인증을 강제해도 이 값으로는 판단할 수 없다. 로그인 시각을 세션에 심는 것이 정석이다(다음 개선).

## userinfo 응답 (표준 준수)

- 인증: access token(Bearer). `sub` 는 **항상 반환**(필수).
- 메서드: **GET 과 POST 를 모두 지원한다**(OIDC Core 5.3.1 MUST). 토큰 전달은 Authorization 헤더 우선이고,
  없으면 form-encoded POST 의 `access_token` 파라미터를 본다(RFC 6750 2.2). 둘을 동시에 쓰면 400 `invalid_request` 다.
  RFC 6750 이 권장하지 않는 URI 쿼리 파라미터 전달은 지원하지 않는다(로그·Referer 에 토큰이 남는다).
- scope 필터링: `profile` → `name`·`nickname`·`preferred_username`, `email` → `email`·`email_verified`. scope 에 없으면 응답에서 제외한다.
  `email` 값이 없으면 `email_verified` 도 함께 뺀다. 매핑은 id token 과 같은 `ProfileClaimMapper` 를 쓴다(두 응답이 갈라지지 않게).
- content type: `application/json`.
- 에러: RFC 6750 형식. 토큰 없음 → 401 + `WWW-Authenticate: Bearer`, 무효 토큰 → 401 `invalid_token`, openid scope 없음 → 403 `insufficient_scope`.

## 공유 계약 (신규/확장)

```
consent 서비스
  GET  /internal/consents/{sub}/{clientId}
       200 { "sub": str, "clientId": str, "scopes": [str] }   (없으면 scopes: [])
  POST /internal/consents   { "sub": str, "clientId": str, "scopes": [str] }
       200 { "sub": str, "clientId": str, "scopes": [str] }   (기존 기록과 합집합으로 병합)

user-directory (확장)
  GET  /internal/users/{sub}
       200 { "sub", "username", "authorities": [str],
             "name", "nickname", "preferredUsername",
             "email", "emailVerified": bool }

Redis (확장)
  auth:code:{code}     { clientId, redirectUri, scope, sub, codeChallenge, nonce, authTime }  TTL 60s
  auth:pending:{id}    { clientId, redirectUri, scope, sub, codeChallenge, state, nonce, authTime }  TTL 300s
```

## 에러 처리와 실패 모드

| 상황 | 응답 | 담당 |
|---|---|---|
| 동의 전부 거부 | `redirect_uri?error=access_denied&state=` | auth |
| pending 없음/만료 | 400 에러 페이지 (**redirect 하지 않음**) | auth |
| userinfo 토큰 없음 | 401 + `WWW-Authenticate: Bearer` | token |
| userinfo 무효 토큰(서명/만료/iss/미공개 kid) | 401 `invalid_token` | token |
| userinfo openid scope 없음 | 403 `insufficient_scope` | token |
| userinfo 토큰 전달 방식 중복(헤더 + 폼) | 400 `invalid_request` | token |
| userinfo 의 sub 가 user-directory 에 없음(404) | 401 `invalid_token` | token |
| userinfo 의 user-directory 일시 장애(연결 실패·5xx) | 200 `{sub}` (프로필 claim 생략) | token |
| id token 발급 중 sub 가 user-directory 에 없음(404) | 400 `invalid_grant` | token |
| userinfo 중 signing 장애로 jwks 확보 실패 | 500 `server_error` | token |
| 미처리 예외 | 500 `server_error` (OAuth2 포맷) | token — 기존 `OAuth2ExceptionHandler` |

**서비스 간 실패 모드**

- **consent 다운** → **fail-closed**. 동의 조회 실패 시 code 를 발급하지 않고 에러로 끝낸다. 승인된 것으로 간주하면 무단 발급이 되므로, "모르면 거부"가 보안 기본값이다.
- **user-directory 다운(일시 장애: 연결 실패·5xx)** → **degrade**. 사용자 존재 여부가 미확정인 상태이므로 인증 자체(누가 로그인했는가)는 성공시키고 프로필 조회라는 부가 기능만 뺀다.
  id token 은 **프로필 claim 없이 발급**하고(필수 claim 만으로 표준상 유효), userinfo 는 **`200 {sub}`** 로 응답한다.
  userinfo 를 503 으로 막지 않는 이유는 `sub` 가 표준 필수 claim 이고 토큰 검증만으로 이미 확정된 값이기 때문이다 — user-directory 없이도 돌려줄 수 있는 것을 굳이 막을 이유가 없다.
- **user-directory 가 404 를 준 경우** → degrade 가 아니다. 404 는 "사용자가 없다"는 **확정된 사실**이라 일시 장애와 성격이 다르다.
  존재하지 않는 주체에 대한 인증 주장을 만들지 않기 위해 userinfo 는 401 `invalid_token`, id token 발급은 400 `invalid_grant` 로 끝낸다.
- **signing 다운** → access token·id token 발급 불가(500 `server_error`).
  **`/userinfo` 도 500 `server_error` 다.** `AccessTokenVerifier` 에 **jwks 캐시가 없어** 매 요청 signing 을 호출하기 때문이다.
  즉 슬라이스 1 이 보여준 graceful degradation("검증자가 jwks 를 캐시해두면 signing 이 죽어도 기존 JWT 검증은 계속된다")은
  **jwks 를 캐시하는 외부 검증자에게만** 성립하고, 이 서버의 `/userinfo` 에는 성립하지 않는다.
  이때 401 `invalid_token` 을 주면 안 된다. RP 가 멀쩡한 토큰을 폐기하고 재인증을 돌려 signing 장애 한 번이 전 RP 의 동시 재인증으로 증폭된다.
  "키를 확보하지 못했다"와 "토큰이 무효다"는 다른 사건이므로 `AccessTokenVerifier` 가 예외를 갈라 던진다.
  (jwks 캐시는 성능 항목이 아니라 **가용성 항목**이다 — 다음 개선)

**보안 불변식**
- 동의 제출은 pending 의 scope 범위 안에서만 승인된다(제출 scope ∩ pending scope). 폼 조작으로 상위 scope 를 승인할 수 없다.
- code 에 실리는 scope 는 **승인된 scope** 다(요청 scope 가 아니다). 따라서 access token·id token 의 권한이 동의 범위를 넘지 않는다.
- 슬라이스 1 의 불변식(open redirect 방지, PKCE 강제, code 원자 1회 소비, client bcrypt 인증, 키 격리)은 그대로 유지된다.

## 검증

**단위/슬라이스 테스트**
- id token claim 구성: nonce 반영(요청에 있을 때/없을 때), **at_hash 계산 정확성(알려진 값 대조)**, scope 별 프로필 claim 포함/제외
- userinfo scope 필터링: openid 만 → sub 만, +profile → 이름류 포함, +email → 이메일 포함
- consent API: 조회(기록 없음 → 빈 scope), 저장(기존 기록과 합집합 병합)
- 동의 scope 교집합 로직: 제출 scope 가 pending 을 넘으면 잘려나감

at_hash 는 잘못 구현해도 조용히 통과할 수 있으므로 알려진 값 대조 테스트를 반드시 둔다(슬라이스 1 에서 "서명 검증 없는 테스트"가 리뷰에 걸린 것과 같은 이유).

**관통 e2e** (7서비스 + nginx/MySQL/Redis, 게이트웨이 경유)

1. 최초 인가 → **동의 화면 노출** → 동의 → code → token 응답에 **id_token 포함**
2. id token 검증: iss/sub/aud/exp/iat, **nonce 가 요청값과 일치**, **at_hash 가 access token 과 대응**, auth_time 존재, jwks 로 RS256 서명 검증 통과
3. **재인가 시 동의 화면 생략** (consent 기록으로 바로 code 발급)
4. `/userinfo` (Bearer) → sub + scope 대응 claim. **openid 만 있는 토큰은 프로필 claim 미포함**
5. 동의 전부 거부 → `error=access_denied`
6. userinfo 무효 토큰 → 401 `invalid_token`
7. (회귀) 슬라이스 1 기준 유지: code 재사용·PKCE 변조 → `invalid_grant`, 미등록 redirect_uri → redirect 없이 400

## 프로젝트 구조

```
microservice/
├── consent/                    # 신규 (8086)
│   ├── build.gradle, settings.gradle, .gitignore, gradle wrapper (signing 에서 복사)
│   └── src/main/java/dev/starryeye/consent/
│       ├── ConsentApplication.java
│       ├── jpa/ConsentEntity.java, ConsentEntityRepository.java
│       ├── ConsentController.java          (내부 API)
│       └── dto/ConsentResponse.java, SaveConsentRequest.java
├── auth/                       # 확장
│   ├── client/ConsentClient.java, ConsentInfo.java
│   ├── PendingAuthorizationStore.java, PendingAuthorization.java
│   ├── ConsentPageController.java          (화면 렌더 + 제출)
│   └── resources/templates/consent.html
├── token/                      # 확장
│   ├── IdTokenIssuer.java                  (claim 구성 + at_hash)
│   ├── AccessTokenVerifier.java            (jwks 기반 검증)
│   ├── ProfileClaimMapper.java             (scope->claim 매핑, id token/userinfo 공유)
│   ├── UserInfoController.java
│   └── client/UserDirectoryClient.java, UserProfile.java
├── user-directory/             # 확장 (UserEntity 프로필 필드, 응답 확장, seed 값)
├── gateway/nginx.conf          # /userinfo → token, /oauth2/consent → auth
└── docker-compose/             # 변경 없음 (consent 는 기존 MySQL 사용)
```

**Global Constraints** (슬라이스 1 승계): Java 21, Spring Boot 3.4.5 / dependency-management 1.1.7, SAS starter 금지, 패키지 `dev.starryeye.<service_name>`, gradle `--no-daemon`, 클래스 설명 javadoc 은 클래스 바디 안, 커밋 트레일러 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`, 새 서비스 디렉토리에 `.gitignore` 필수.

**seed 확장**: user 에 프로필 값(name·nickname·preferred_username·email·email_verified), client `my-client` 의 scope 에 `email` 추가.

## 이번 슬라이스 제외 항목

**다음 슬라이스로 예약** (순서 합의됨)
- Kafka 인증 이벤트 스트림 (슬라이스 3 유력) — 로그인·토큰 발급·동의 부여/철회를 이벤트로 발행, 감사·brute force 잠금·세션 무효화 팬아웃
- 내부 서비스 간 인증 (API 키 또는 mTLS) — 현재 신뢰 네트워크 가정
- refresh token / introspection

**후보 (아직 미예약)**
- back-channel logout (`sid` claim, logout 엔드포인트) — 이번 설계에서 `sid` 를 제외하며 파생된 주제

**슬라이스가 아닌 개선 항목**
- ForwardedHeaderFilter 미적용 (슬라이스 1 README 의 알려진 한계 — 로그인 redirect 가 게이트웨이 포트를 잃는 문제). 작은 수정이라 언제든 끼워넣을 수 있다.
- ~~AuthorizeController MockMvc 통합 테스트 보강~~ → 최종 리뷰 후속으로 `AuthorizeControllerTest` 를 추가해 해소했다.
- **jwks 캐시** (성능이 아니라 가용성 항목 — 위 "signing 다운" 참고)
- **`typ: at+jwt`** (RFC 9068) 로 access token 과 id token 을 구분. signing 의 서명 API 계약(헤더는 signing 이 전적으로 소유)을 바꿔야 한다.
- **`scope` claim 형식** — RFC 9068 2.2.3 은 공백 구분 문자열인데 이 구현은 JSON 배열이다.
