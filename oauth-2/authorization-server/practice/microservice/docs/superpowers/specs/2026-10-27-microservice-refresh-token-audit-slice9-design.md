# 마이크로서비스 인가 서버 — 슬라이스 9 설계: refresh token 이력 감사

운영자가 한 사용자의 refresh token 에 일어난 일을 계열별·시간순으로 조회할 수 있게 한다. 핵심은 **도난 의심으로 계열을 끊은 순간**이 반드시 남는 것이다.

대상: `oauth-2/authorization-server/practice/microservice/`
선행: 슬라이스 1~8 — 11개 바이너리, 284개 테스트, Kafka 토픽 1개(`oidc.session.logged-out.v1`)와 그 소비자 둘(`token-state`, `audit`)

> 슬라이스 8 설계 10절이 "감사 소비자"를 ① 로그아웃 이벤트(슬라이스 8) ② refresh 계열 이벤트 ③ 로그인 이벤트로 나눴다. 이 문서는 ② 이고, 번호는 슬라이스 9 로 둔다(번호 부여는 작성자 판단).

---

## 1. 왜 이 슬라이스인가

### 운영자가 이 기록을 여는 두 순간 (사용자 요구)

1. **도난 의심 알림이 왔을 때 그 사용자를 확인한다.** 알림 자체는 이 저장소 밖이다(7절). 이 슬라이스는 알림이 가리킬 사실과 그 사실을 조회할 방법을 만든다.
2. **긴급 상황에서 그 사용자의 refresh token 상태를 확인한다.** 확인만 한다 — 강제 폐기는 하지 않는다.

### 지금은 그 순간이 어디에도 이력으로 남지 않는다

`token-state` 의 재사용 탐지(슬라이스 3)는 이미 쓴(`CONSUMED`) 토큰이 다시 오면 그 계열 전체를 `REVOKED`(사유 `REUSE_DETECTED`)로 바꾼다. 그 결과는 `refresh_tokens` 행의 **현재 상태**로만 남는다. "언제, 어느 사용자의, 어느 client 계열이, 몇 개 끊겼는가"를 시간순으로 돌아볼 수단이 없고, 같은 계열에 로그아웃 폐기가 뒤따르면 그 구분도 사라진다(`revoke()` 는 이미 `REVOKED` 면 아무것도 바꾸지 않아 최초 사유만 남지만, 그 앞뒤 경과는 없다).

`token-state` 의 테이블로 "지금 상태"는 답할 수 있다. 사용자가 원한 것은 **시간순 기록**이다(사용자 답). 상태가 바뀐 뒤에도 남는 것은 이력뿐이다.

### 이 슬라이스가 새로 배우는 것

- **두 번째 발행자.** 슬라이스 7·8 의 발행자는 `session` 하나였다. 이번엔 `token-state` 가 발행한다. 그리고 `token-state` 는 이미 로그아웃 이벤트의 **소비자**이므로, 소비 처리 안에서 다시 발행하는 사슬(`session` → `token-state` → `audit`)이 생긴다.
- **비관적 잠금 트랜잭션 안의 outbox.** 회전·탐지는 계열 행 잠금(`findByFamilyIdForUpdate`) 안에서 판정된다(슬라이스 3). 계열을 끊는 일과 "끊었다"는 기록은 **같이 성공하거나 같이 실패**해야 한다 — 끊었는데 기록이 없거나, 기록은 있는데 안 끊겼으면 감사가 거짓말을 한다. 그래서 그 같은 트랜잭션에 outbox 행을 쓴다.
- **감사 조회 API 를 이 인가 서버가 발급한 토큰으로 지킨다.** 운영자는 client_credentials 로 `audit.read` scope 토큰을 받아 부른다. 슬라이스 4 가 introspection 을 지킨 방식과 같은 질문(신원은 client 인증, 능력은 scope)을 resource server 쪽에서 다시 푼다.

---

## 2. 서비스별 변경

| 서비스 | 변경 | 비고 |
|---|---|---|
| `token-state` | outbox 테이블·폴러 추가, 발급·회전·폐기 경로가 같은 트랜잭션에서 outbox 행을 쓴다. 새 토픽 선언. `revokeBySid` 가 계열별로 무엇을 바꿨는지 알 수 있게 바뀐다(4절). `rotate`·`revoke` 의 `now` 를 잠금 뒤로 옮긴다(3절) | 새 발행자 |
| `audit` | 새 토픽 소비, 새 테이블 `refresh_token_events`, 운영자 조회 API, Bearer 검증 | 소비자 + resource server |
| `client-registry` | 시드에 운영자 client 하나 추가(`client_credentials`, `client_scopes = audit.read`) | 시드만 |
| `session` | **변경 없음** | 슬라이스 8 의 0줄 원칙을 이번에도 유지 |
| `token`, `auth`, `signing`, `consent`, `user-directory`, `demo-rp`, gateway | 변경 없음을 목표로 한다 | `token` 의 client_credentials 가 `client_scopes` 에 있는 임의 scope 를 그대로 내주는지는 빌더가 확인(8절) |

스키마·계정은 그대로다. `outbox` 는 `ms_token_state`, `refresh_token_events` 는 `ms_audit` 에 각 서비스가 `ddl-auto` 로 만든다. 초기화 SQL 변경 없음.

---

## 3. 이벤트 계약

### 토픽 하나, 키는 계열 (작성자 판단)

- 토픽: `oidc.refresh-token.lifecycle.v1` — 파티션 3, replicas 1(기존 토픽과 같은 값)
- 키: `familyId`
- 사건 종류(`kind`): `ISSUED`, `ROTATED`, `REVOKED`

사건마다 토픽을 나누지 않는 이유: 한 계열의 사건이 한 파티션에 모여야 그 계열의 발행 순서가 소비 순서로 보존된다. 다만 감사 조회의 정렬 기준은 Kafka 순서가 아니라 `occurredAt` 이다(5절) — 키는 순서 **보존**을 위한 것이지 정렬을 대신하지 않는다.

### 페이로드 (JSON)

공통:

| 필드 | 값 | 출처 |
|---|---|---|
| `eventId` | UUID | outbox 행마다 새로 |
| `kind` | `ISSUED` / `ROTATED` / `REVOKED` | |
| `occurredAt` | ISO-8601 | 그 트랜잭션의 `now` — **계열 잠금을 얻은 뒤의 시각**(아래 "시각은 잠금 뒤에 잰다") |
| `familyId` | | `refresh_tokens.family_id` |
| `sub` | | `refresh_tokens.sub` — **사실이다**. 슬라이스 8 의 `reported_sub`(전문)과 달리 `token-state` 는 이 값의 소유자다 |
| `clientId` | | `refresh_tokens.client_id` |
| `sid` | nullable | `refresh_tokens.sid` — 롤링 배포 스큐로 null 인 행이 있을 수 있다(`RefreshTokenEntity.sid` 주석) |

종류별:

| `kind` | 추가 필드 | 의미 |
|---|---|---|
| `ISSUED` | `expiresAt`, `familyExpiresAt` | 새 계열의 첫 토큰. 만료는 사건 없이 지나가므로 조회가 만료를 계산하려면 이 값이 필요하다 |
| `ROTATED` | `expiresAt` | 회전으로 생긴 새 토큰의 만료(계열 상한으로 잘린 값) |
| `REVOKED` | `reason`, `revokedTokens`, `activeTokensRevoked` | `reason` ∈ `REUSE_DETECTED`, `CLIENT_REVOKED`, `SESSION_LOGGED_OUT` (`refresh_tokens.revoked_reason` 과 같은 문자열) |

토큰 원문·해시는 싣지 않는다.

### "몇 개 끊겼는가" — 두 수를 둔다 (작성자 판단)

사용자는 계열을 끊은 순간에 **어느 사용자, 어느 client, 몇 개 끊겼는지**를 요구했다. 그런데 한 계열 안에서 쓸 수 있는(`ACTIVE`) 토큰은 회전 구조상 보통 하나다 — 회전할 때마다 이전 토큰은 `CONSUMED` 가 된다. 그래서 "몇 개"는 두 가지로 읽힌다.

- `revokedTokens`: 이 사건으로 **상태가 `REVOKED` 로 바뀐** 행 수. `revokeFamily` 는 `CONSUMED` 행까지 전부 바꾼다(이미 `REVOKED` 인 행은 `revoke()` 가 건너뛰므로 세지 않는다).
- `activeTokensRevoked`: 그중 직전까지 `ACTIVE` 였던 행 수 — 실제로 쓸 수 있던 토큰이 끊긴 수.

로그아웃 폐기(`revokeBySid`)는 `ACTIVE` 행만 바꾸므로 두 수가 같다. 두 수 모두 **실제로 바뀐 행**에서 세야 한다 — 바꾸려고 시도한 행이 아니다.

**바뀐 행이 0이면 사건도 없다.** `revoke()`(RFC 7009 경로)는 토큰을 찾고 client 가 맞으면, 계열이 이미 전부 `REVOKED` 여도 `true` 를 돌려준다. 이때 바뀐 행이 없으므로 발행하지 않는다(응답은 지금처럼 그대로). `revokeBySid` 도 같다.

**한 계열에 `REVOKED` 사건이 둘 이상 생길 수 있다.** 로그아웃(`revokeBySid`)은 `ACTIVE` 행만 바꾸고 `CONSUMED` 행은 그대로 둔다. 그 뒤 옛 토큰이 다시 들어오면 `REUSE_DETECTED` 경로가 남은 `CONSUMED` 행을 바꾼다 — `REVOKED(SESSION_LOGGED_OUT)` 뒤에 `REVOKED(REUSE_DETECTED, activeTokensRevoked=0)` 가 온다. 실제로 행이 바뀌었고 도난 신호이므로 **발행한다**(작성자 판단). 조회 쪽이 이를 어떻게 요약하는지는 5절.

### 시각은 잠금 뒤에 잰다 (작성자 판단, 기존 코드 변경)

지금 `rotate` 는 `Instant now = Instant.now()` 를 계열 잠금(`findByFamilyIdForUpdate`)보다 **먼저** 잰다. 같은 토큰으로 정상 client 와 공격자가 거의 동시에 들어오면, 잠금을 기다린 쪽의 `now` 가 잠금을 먼저 잡은 쪽보다 이를 수 있다 — T1 이 10.000 에 잠금을 잡고 `ROTATED` 를 커밋하고, 9.999 에 들어와 기다리던 T2 가 `CONSUMED` 를 보고 `REVOKED(REUSE_DETECTED)` 를 9.999 로 커밋한다. `occurredAt` 순으로 보면 폐기가 회전보다 앞선다. 이 슬라이스가 존재하는 바로 그 장면에서 순서가 뒤집히고, 순차 e2e 로는 절대 드러나지 않는다.

그래서 `rotate`·`revoke` 의 `now` 를 잠금을 얻은 뒤에 잰다. `revokeBySid` 도 잠금 조회 뒤에 잰다. 그러면 같은 계열 안에서 `occurredAt` 순서가 잠금 순서(= 커밋 순서)와 같아진다. 부수 효과: 만료 판정·`consumedAt`·새 토큰 `issuedAt` 도 잠금 시각 기준이 된다 — 판정이 실제로 일어나는 시각이므로 더 맞다. 이 순서는 경합 테스트로 보인다(6절 기준 2b). 그래도 조회의 상태 요약은 순서에 기대지 않는다(5절).

### 무엇을 발행하지 않는가

**상태가 바뀌지 않은 시도는 발행하지 않는다** — `rotate` 의 `NOT_FOUND`, `CLIENT_MISMATCH`, `REVOKED`(이미 끊긴 토큰으로 재시도), `EXPIRED`, `SCOPE_EXCEEDED`, `revoke` 가 `false` 를 낸 경우. `RotateStatus` 주석대로 `SCOPE_EXCEEDED` 는 어떤 상태도 바꾸지 않아야 하고, outbox 행도 그 "상태"에 포함된다. 끊긴 계열의 토큰으로 다시 들어오는 시도는 보안상 의미가 있지만 이번 범위가 아니다(9절 한계).

---

## 4. 발행자 — `token-state`

### outbox 는 `session` 의 모양을 따른다

`session` 의 `OutboxEntity` / `OutboxPublisher` 를 복제한다(모듈이 독립이라 공유 라이브러리는 없다). 슬라이스 7·8 이 이미 겪은 함정을 처음부터 피한다.

- `payload` 매핑은 `@Lob` 만 두지 말고 길이를 명시한다 — Hibernate 6 + MySQL 에서 기본 길이는 `tinytext`(255바이트)다(슬라이스 8). 이 슬라이스의 페이로드는 255바이트를 넘을 수 있다.
- 발행 표시는 `TransactionTemplate` 으로 — `@Transactional` 자기 호출은 프록시를 우회한다(슬라이스 7).
- 토픽은 발행자(`token-state`)와 소비자(`audit`)가 모두 `NewTopic` 으로 선언한다. 브로커의 자동 생성은 꺼져 있다(`KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`).

### 각 경로에서 outbox 행을 쓰는 곳

| 경로 | 트랜잭션 | 발행 |
|---|---|---|
| `issue` | 기존 `@Transactional` | `ISSUED` 1건 |
| `rotate` → `ROTATED` | 기존 계열 잠금 트랜잭션 | `ROTATED` 1건 |
| `rotate` → `REUSE_DETECTED` | 같은 트랜잭션. 예외 없이 `failed(...)` 를 반환하므로 **폐기가 커밋된다** — outbox 행도 함께 커밋돼야 한다 | `REVOKED`(`REUSE_DETECTED`) 1건 |
| `revoke` (RFC 7009) 로 행이 실제로 바뀜 | 기존 계열 잠금 트랜잭션 | `REVOKED`(`CLIENT_REVOKED`) 1건. 바뀐 행이 0이면 없음 |
| `revokeBySid` (로그아웃 이벤트 소비) | 소비자의 트랜잭션 | 실제로 행이 바뀐 **계열마다** `REVOKED`(`SESSION_LOGGED_OUT`) 1건 |
| 위 3절의 실패 경로 | — | 없음 |

### `revokeBySid` 는 모양이 바뀐다

지금은 JPQL 벌크 UPDATE 한 번이라 "어느 계열의 무엇이 바뀌었는가"를 모른다. 계열별 사건을 내려면 바뀐 행을 알아야 한다. 요구는 둘이다.

1. 사건은 **실제로 바뀐 행**만 말한다. 같은 로그아웃 이벤트가 다시 와서(재전달) 바뀐 행이 없으면 사건도 없다 — 그래서 소비 재전달이 감사 쪽 중복을 만들지 않는다(그래도 `eventId` 멱등은 둔다, 5절).
2. 동시에 진행되는 `rotate` 와 엇갈려도 수치가 틀리지 않는다. 슬라이스 7 이 남긴 한계("`revokeBySid` 는 계열 잠금 없음")가 이 요구와 직접 부딪힌다.

작성자 판단: `sid` 의 `ACTIVE` 행을 잠금 조회로 읽고, 엔티티의 `revoke()` 로 바꾼 뒤 계열별로 묶어 기록한다. 잠금 순서가 `rotate` 와 교착을 만들지 않는지, InnoDB 에서 실제로 직렬화되는지는 빌더가 따져 테스트로 보인다(h2 의 잠금 의미론은 InnoDB 와 다르다 — 슬라이스 3). 증명하지 못하면 그 사실을 한계에 적는다 — "닫혔다"고 쓰지 않는다.

### 이 슬라이스에서 `token-state` 는 소비자이자 발행자다

로그아웃 이벤트 소비 → `revokeBySid` → outbox 행 쓰기가 한 트랜잭션이다. Kafka 오프셋 커밋은 그 뒤다. 트랜잭션은 커밋됐는데 오프셋 커밋 전에 죽으면 같은 로그아웃 이벤트가 다시 오고, 1번 요구 덕에 두 번째 처리는 아무것도 바꾸지 않는다.

---

## 5. 소비자·저장소 — `audit`

### 새 테이블 `refresh_token_events` (작성자 판단)

슬라이스 8 의 `audit_events` 를 넓히지 않고 테이블을 따로 둔다. 이유: `audit_events` 의 주체는 세션(`subject_sid NOT NULL` = 사실)이고, 이 사건들의 주체는 계열이다 — 여기선 `sid` 가 null 일 수 있다. 같은 테이블에 넣으면 슬라이스 8 이 세운 "`subject_sid` 는 사실, not null" 이 무너진다.

| 컬럼 | 제약 | 비고 |
|---|---|---|
| `event_id` | UNIQUE, not null | 멱등 키 |
| `event_type` | not null | 받은 토픽 이름(슬라이스 8 과 같은 규칙) |
| `kind` | not null | |
| `occurred_at` / `recorded_at` | not null | 슬라이스 8 과 같은 두 시각 |
| `sub`, `client_id`, `family_id` | not null | 셋 다 `token-state` 소유의 사실 |
| `sid` | nullable | |
| `reason`, `revoked_tokens`, `active_tokens_revoked` | nullable | `REVOKED` 만 |
| `expires_at`, `family_expires_at` | nullable | `ISSUED`/`ROTATED` |
| `payload` | not null, 길이 명시(mediumtext 목표) | 원문 그대로 |

조회 경로를 위한 인덱스(`sub`, `family_id`, `occurred_at` 등)는 빌더가 정한다.

### 멱등·실패 처리는 슬라이스 8 그대로

- 저장은 `AuditRecorder` 와 같은 방식: 선조회 → `@Transactional` 없이 save → `DataIntegrityViolationException` 을 잡고 재확인 → 행이 있을 때만 흡수.
- 컨슈머 그룹은 기존 `audit` 그대로(토픽만 하나 더 구독). 실패는 `KafkaConsumerConfig.dltFor(record.topic())` 로 `oidc.refresh-token.lifecycle.v1.audit.dlt` 에 간다. **이 DLT 의 `NewTopic` 선언이 필요하다** — 슬라이스 8 의 `KafkaConsumerConfig` 주석이 이 전제를 이미 적어 두었다.
- 페이로드에 필수 필드(`eventId`, `kind`, `occurredAt`, `familyId`, `sub`, `clientId`)가 빠지면 흡수하지 않고 실패한다.

### 운영자 조회 API

`GET /audit/users/{sub}/refresh-token-families` (경로 이름은 빌더 재량)

- 계열별로 묶는다(사용자 답). 계열 안의 사건은 `occurredAt` 오름차순, 같은 시각이면 기록 순(`id`). 계열끼리는 최근에 발급된 것이 위(작성자 판단).
- 계열마다 `clientId`, `sid`, 발급 시각, 절대 상한, 그리고 **이력으로 재구성한 상태**를 함께 낸다(작성자 판단).
  - `REVOKED` 사건이 **하나라도** 있으면 → `REVOKED`. `REVOKED` 는 되돌릴 수 없는 종착 상태라(`token-state` 에서 끊긴 계열은 다시 회전하지 않는다) 사건 순서와 무관하게 판정된다 — 3절의 시각 보정이 어떤 이유로 어긋나도 "끊긴 계열이 살아 있다"고 보여 주지 않는다.
    - 요약 사유는 **첫** `REVOKED` 사건의 사유 — 그 계열을 쓸 수 없게 된 순간이다.
    - `REUSE_DETECTED` 사건이 하나라도 있으면 `reuseDetected: true` 를 함께 낸다. 로그아웃으로 먼저 끊긴 뒤 재사용이 탐지돼도 도난 신호가 요약에서 가려지지 않게 하기 위해서다. 세부는 사건 목록에 전부 있다.
  - 아니면, 조회 시각이 마지막 토큰의 `expiresAt` 이나 `familyExpiresAt` 을 지났으면 → `EXPIRED`(`token-state` 의 `isExpired` 와 같은 두 조건)
  - 아니면 → `ACTIVE`
- 이 상태는 **감사 이력으로 재구성한 값**이다. 진실의 출처는 `token-state` 이고, outbox 폴링(500ms)과 소비만큼 늦다. 응답과 문서가 이를 숨기지 않는다.
- 사건이 하나도 없는 `sub` 는 200 과 빈 목록이다. 감사는 사용자 존재 여부를 모른다.
- 페이지네이션은 없다(작성자 판단 — 한 사용자의 계열 수가 작다는 전제, 9절 한계).
- 게이트웨이(:9000)에 노출하지 않는다. 다른 내부 서비스처럼 `audit:8089` 로 직접 부른다(작성자 판단).

### API 보호

- `Authorization: Bearer <access token>` 만 받는다. 이 인가 서버가 발급한 access token 이어야 한다.
  - 서명: `signing` 의 `/oauth2/jwks`
  - `iss` = `http://localhost:9000`(`token` 의 `my.issuer` 와 같은 값), `exp`
  - `typ` = `at+jwt` — 같은 키로 id token(`JWT`)·logout token(`logout+jwt`)도 서명되므로 이걸 안 보면 id token 으로도 API 가 열린다(슬라이스 5)
  - `scope` 에 `audit.read`
- 오류는 RFC 6750: 토큰 없음 → 401, 무효 → 401 `invalid_token`, scope 부족 → 403 `insufficient_scope`.
- **jwks 를 가져오지 못한 것은 토큰 무효가 아니다** → 500(슬라이스 2 의 교훈 — 우리 쪽 장애를 클라이언트의 토큰 문제로 둔갑시키지 않는다).
- jwks 캐시는 둔다(작성자 판단). Spring 의 `NimbusJwtDecoder` 는 기본으로 jwks 를 캐시한다. 그래서 키를 한 번 가져온 뒤에는 `signing` 이 내려가도 캐시된 키로 검증이 계속된다 — 슬라이스 1 이 실증한 graceful degradation 과 같은 성질이고, `token` 의 `AccessTokenVerifier` 가 "캐시 없음"을 한계로 남긴 것보다 가용성이 낫다. 500 은 **캐시된 키가 없을 때** jwks 를 못 가져온 경우의 응답이다. 캐시 기간은 빌더가 실제 값을 확인해 문서에 적는다.

구현 방식(작성자 판단): `audit` 에 `spring-boot-starter-oauth2-resource-server` 를 쓴다. 이 저장소는 Spring Security 학습 저장소이고, 인가 서버 쪽은 SAS 없이 직접 구현했지만 resource server 쪽은 Spring Security 가 하는 일을 그대로 쓰는 것이 자연스럽다. 확인된 사실 하나: 이 프로젝트의 Spring Security **6.4.5 의 `spring-security-oauth2-jose` jar 에는 `JwtTypeValidator` 가 없다**(jar 목록으로 확인). 그래서 `typ: at+jwt` 를 받아들이고 그 외를 거부하는 설정을 빌더가 찾아 붙이고 테스트로 보여야 한다 — 기본 디코더가 `at+jwt` 를 어떻게 다루는지도 실측으로 확인한다. 대안은 `token` 의 `AccessTokenVerifier` 를 복제하는 것이다. 위 요구를 모두 만족하면 어느 쪽이든 된다.

### 운영자 client

`client-registry` 시드: `audit-operator` / `secret`(기존 시드와 같은 관례), `grant_types = client_credentials`, `client_scopes = audit.read`, `scopes`·`redirect_uris` 비움, 로그아웃 통지 대상 아님(`article-api` 시드와 같은 모양).

`audit.read` 는 discovery 의 `scopes_supported` 에 광고하지 않는다(작성자 판단 — 운영자 전용 능력이고, `token` 쪽에 "`USER_DELEGATED_SCOPES` = `scopes_supported` − `introspect`" 관계를 단언하는 테스트가 있다. 광고하면 그 관계를 다시 정의해야 한다).

---

## 6. 성공 기준

e2e 는 슬라이스 8 의 `http/e2e-login-logout.sh` 를 확장하거나 곁에 둔다. README 에는 **실측 출력만** 옮긴다.

1. **평소 이력**: 로그인 → refresh 두 번 → 조회 API 가 계열 하나에 `ISSUED`, `ROTATED`, `ROTATED` 를 시간순으로, 상태 `ACTIVE` 로 보여 준다.
2. **도난 의심 순간**(핵심): 이미 쓴 refresh token 을 다시 보내면 `token` 은 `invalid_grant`, 조회 API 의 그 계열에 `REVOKED`(`REUSE_DETECTED`) 가 생기고 `sub`·`clientId`·`revokedTokens`·`activeTokensRevoked` 가 실제 `refresh_tokens` 의 변화와 일치한다(같은 계열 행을 직접 조회해 대조). 상태는 `REVOKED`, `reuseDetected: true`.
   - 2b. **경합**: 같은 토큰으로 회전과 재사용이 동시에 들어와도 `REVOKED` 사건의 `occurredAt` 이 `ROTATED` 보다 앞서지 않고, 상태는 `REVOKED` — 테스트(잠금 의미론은 InnoDB 기준으로 보인다. h2 로만 보였다면 그렇다고 적는다).
   - 2c. **로그아웃 뒤 재사용**: 로그아웃으로 끊긴 계열에 옛 토큰이 다시 오면 `REVOKED(REUSE_DETECTED, activeTokensRevoked=0)` 가 추가되고, 요약 사유는 `SESSION_LOGGED_OUT` 그대로, `reuseDetected: true`.
3. **원자성**: outbox 행 쓰기가 실패하면 계열 폐기도 롤백된다 — 테스트로 보인다(e2e 불필요).
4. **실패 경로는 조용하다**: `SCOPE_EXCEEDED`·`EXPIRED`·`NOT_FOUND`·`CLIENT_MISMATCH`·이미 `REVOKED` 인 토큰의 재시도, 이미 전부 끊긴 계열에 대한 `/oauth2/revoke` 는 outbox 행을 만들지 않는다 — 테스트.
5. **로그아웃 사슬**: 같은 사용자의 두 세션 중 하나를 로그아웃하면 그 세션 계열만 `REVOKED`(`SESSION_LOGGED_OUT`), 다른 세션 계열은 `ACTIVE`. 같은 로그아웃 이벤트를 재전달해도 사건이 늘지 않는다.
6. **RFC 7009 폐기**: `/oauth2/revoke` 로 끊은 계열이 `REVOKED`(`CLIENT_REVOKED`).
7. **API 보호**: 토큰 없음 401 / id token 으로 호출 401 / `audit.read` 없는 access token 403 `insufficient_scope` / `audit-operator` 토큰 200 / 캐시된 키가 없는 상태에서 `signing` 이 내려가 있으면(jwks 조회 실패) 500 / 키를 캐시한 뒤 `signing` 을 내리면 캐시 기간 동안 200.
8. **독립성**: `audit` 을 내려도 refresh·로그아웃·폐기는 정상이고, 다시 올리면 밀린 사건을 기록한다(토픽 보존 기간 안에서).
9. **멱등**: 같은 `eventId` 의 재전달은 한 줄 — 테스트.
10. **`session/src` diff 0줄.**

---

## 7. 범위

### 한다

- `token-state` 의 발급·회전·폐기 사건 발행(outbox), `revokeBySid` 의 계열별 기록
- `audit` 의 새 토픽 소비·저장·운영자 조회 API·Bearer 보호
- 운영자 client 시드
- 위 성공 기준의 테스트와 e2e, README

### 하지 않는다

- **도난 의심 알림(Slack·메일 등)** — 탐지 사실을 보고 사람을 깨우는 일은 모니터링 플랫폼의 규칙(CloudWatch 알람, Alertmanager 등)이 하는 일이다. [인프라 프로젝트 백로그](../../infra-project-backlog.md)로 넘긴다(작성자 판단, 저장소의 범위 경계 기준에 따름).
- 운영자의 강제 폐기 API (사용자 답: 확인만)
- 사용자 본인의 자기 이력 조회
- 상태가 바뀌지 않은 시도의 기록(3절)
- 이 기능 이전에 발급된 토큰의 이력 이관 (사용자 답: 아직 배포 전이라 불필요)
- access token 의 폐기·추적 — access token 은 상태 없는 JWT 다. 계열을 끊어도 이미 나간 access token 은 `exp`(300초)까지 유효하다. 감사 기록은 그 사실을 바꾸지 않는다
- 로그인 이벤트(③ — 다음 슬라이스)
- 게이트웨이 노출, 페이지네이션

---

## 8. 빌더에게 남기는 것 (확인하고 보고할 것)

- `token` 의 client_credentials 가 `client_scopes` 의 `audit.read` 를 그대로 내주는지, `USER_DELEGATED_SCOPES` 가드와 discovery 관계 테스트에 영향이 없는지 — 영향이 있으면 `token` 을 바꿔도 된다. 이 경우 2절 표를 사실에 맞춘다
- `revokeBySid` 의 잠금 방식과 `rotate` 와의 교착·직렬화 증명(4절)
- `typ: at+jwt` 검증의 실제 구성(5절)
- 조회 경로·응답 JSON 의 정확한 모양, 인덱스
- 기존 `audit` 의 `AuditRecorder` 를 일반화할지, 새 레코더를 둘지
- outbox 폴러가 `session` 과 같은 주기(500ms)여도 되는지

---

## 9. 알려진 한계 (이 슬라이스가 남기는 것)

- 조회 API 의 상태는 감사 이력으로 재구성한 값이다 — outbox 폴링·소비만큼 늦고, `token-state` 가 진실의 출처다
- 이 기능 이전에 발급된 토큰은 기록이 없다
- 상태가 바뀌지 않은 시도(끊긴 계열 토큰의 재시도 포함)는 기록되지 않는다
- 감사는 토픽 보존 기간(브로커 기본 7일) 안의 사건만 소급한다(슬라이스 8 과 같다)
- `token-state` 다중 인스턴스 시 outbox 발행자 경쟁(슬라이스 7 의 `session` 과 같은 한계)
- 계열을 끊어도 이미 발급된 access token 은 만료까지 유효하다
- 페이지네이션 없음
- 조회 API 는 게이트웨이 밖 내부 포트다. 내부 서비스 간 무인증은 인프라 프로젝트 소관이다([백로그](../../infra-project-backlog.md))

---

## 10. 후속

- ③ 로그인 이벤트 — 무상태인 `auth` 가 로그인 성공을 어떻게 발행하는가(outbox 를 둘 DB 가 없다)
- 끊긴 계열 토큰의 재시도 기록, 사용자 본인 조회 — 필요해지면
