# 마이크로서비스 인가 서버 — 슬라이스 7 설계: 서비스별 DB 분리와 outbox

소유권을 코드 규율이 아니라 **구조**로 만들고, 그래서 불가능해진 교차 정리를 이벤트로 푼다.

대상: `oauth-2/authorization-server/practice/microservice/`
선행: 슬라이스 1~6 — 10개 바이너리, 251개 테스트

---

## 1. 왜 이 슬라이스인가

### 발견 1 — 소유권이 규율뿐이다

서비스마다 테이블을 정확히 하나씩 갖고 있고, 남의 테이블을 읽는 코드는 **0건**이다(JOIN 도 native query 도 없다). 논리적 소유권은 지켜지고 있다.

그런데 지키는 수단이 **코드 규율뿐**이다.

| | 현재 |
|---|---|
| MySQL 인스턴스 | 1개 |
| 스키마 | 1개 (`microservice_as`) |
| 계정 | 5개 서비스가 전부 `root/1111` |

`user-directory` 가 내일 `SELECT * FROM clients` 를 해도 막을 것이 없다.

이 저장소는 슬라이스 4에서 정반대의 판단을 한 적이 있다 — `auth` 의 `ClientInfo` record 에 `client_scopes` 필드를 **아예 넣지 않아** 그 값이 auth 프로세스 메모리에 존재할 수 없게 만들었다. 그때 남긴 문장이 이것이다.

> 거르는 코드가 아니라 **시야에서 제거하는 구조**.

DB 는 그 원칙이 적용되지 않은 채 남아 있다.

### 발견 2 — 로그아웃해도 refresh token 이 산다

`auth` 의 `LogoutController` 는 `sessionClient.logout(sid)` 로 RP 들에 통지하고 `session.invalidate()` 로 세션을 끊는 것이 전부다. **`token-state` 를 부르는 곳이 로그아웃 경로에 없다**(`token-state` 를 부르는 것은 `token` 서비스뿐 — introspect·rotate·revoke).

즉 로그아웃한 뒤에도 그 사용자의 refresh token 으로 새 access token 을 계속 받을 수 있다. RP 세션은 끊겼는데 토큰 갱신 경로는 열려 있다.

### 두 발견이 하나로 묶이는 이유

발견 2를 고치려 할 때, **DB 가 하나면 잘못된 해법이 가능하다.**

> "로그아웃할 때 `refresh_tokens` 에서 그 `sub` 걸 지우면 되지."

한 줄이면 된다. 같은 DB, 같은 root 계정이니까. 그런데 그건 `session` 이 `token-state` 의 테이블을 건드리는 것이다. 소유권 붕괴이고, **아무도 못 막는다.**

스키마와 계정을 쪼개면 그 한 줄이 **구조적으로 불가능**해진다. 권한이 없으니 런타임에 막힌다. 그때 비로소 "그럼 어떻게 알리지?"가 되고 **이벤트가 필연**이 된다.

이 순서가 중요하다. 분리 없이 Kafka 를 넣으면 이벤트를 위한 이벤트가 된다.

---

## 2. 범위

### 고친다

1. **스키마·계정 분리** — 5개 DB 서비스가 각자 전용 스키마와 전용 계정을 갖는다
2. **로그아웃 시 refresh token 폐기** — 현재 결손
3. **그 전파를 Kafka 이벤트로** — 직접 발행으로 만들어 유실·유령을 재현한 뒤 outbox 로 닫는다

### 안 고친다

| 항목 | 이유 |
|---|---|
| 사용자·client 삭제 | 기능 자체가 없다. 없는 기능을 위한 정리 로직은 YAGNI |
| MySQL 인스턴스 분리 | **경계 바깥.** 실패 도메인·백업·스케일은 배포의 문제고 EKS 면 RDS 인스턴스 얘기다 |
| `oidc_sessions` purge | 단일 서비스 내부 문제라 교차 일관성이 아니다. 기존 한계로 유지 |
| Redis 공유 | 슬라이스 1의 **의도된 인계 채널**(auth→token code `GETDEL`)이지 공유 사고가 아니다 |
| CDC(Debezium) | 경계 바깥. Kafka Connect 클러스터와 커넥터를 운영하는 인프라 구성 요소다 |
| 저장소 이기종화(DynamoDB 등) | 이 슬라이스가 그 **선행 조건**을 만든다. 전환 자체는 별도 주제 |

경계 판정 기준은 [infra-project-backlog.md](../../infra-project-backlog.md) 를 따른다 — **"이걸 EKS 로 옮기면 매니페스트 몇 줄로 사라지는가?"**

---

## 3. 스키마·계정 분리

| 서비스 | 스키마 | 계정 | 테이블 |
|---|---|---|---|
| user-directory | `ms_user_directory` | `svc_user_directory` | `users` |
| client-registry | `ms_client_registry` | `svc_client_registry` | `clients` |
| consent | `ms_consent` | `svc_consent` | `consents` |
| token-state | `ms_token_state` | `svc_token_state` | `refresh_tokens` |
| session | `ms_session` | `svc_session` | `oidc_sessions`, `outbox` |

각 계정에는 **자기 스키마에만** `GRANT` 를 준다. MySQL 은 스키마가 곧 데이터베이스라, 다른 계정으로 `SELECT * FROM ms_client_registry.clients` 를 시도하면 권한 오류가 난다.

`docker-compose` 의 `MYSQL_DATABASE: microservice_as` 를 걷어내고 `docker-entrypoint-initdb.d` 초기화 SQL 로 스키마 5개와 계정 5개를 만든다. 각 서비스의 `ddl-auto` 는 자기 스키마에만 테이블을 만들게 되어 충돌 여지가 사라진다.

`root` 는 초기화·관리용으로만 남기고 애플리케이션 설정에서는 사라진다.

**기존 데이터는 이어지지 않는다.** 학습용이라 `ddl-auto` 재생성 + seed 재실행으로 충분하지만, README 기동 절차에 "볼륨을 지우고 다시 띄운다"를 명시한다.

**검증**: `svc_user_directory` 계정으로 `ms_client_registry.clients` 를 조회해 **권한 오류가 나는 것**을 실제로 확인한다. "분리했다"를 주장이 아니라 거부된 쿼리로 남긴다.

---

## 4. 로그아웃 → refresh 폐기

### `refresh_tokens` 에 `sid` 를 추가한다

현재 `refresh_tokens` 는 `sub`·`client_id`·`family_id` 는 갖지만 **`sid` 가 없다**(`oidc_sessions` 에는 있다). 그래서 지금 상태로는 "이 세션의 refresh 만"이라는 표현 자체가 불가능하다. `sub` + `client_id` 로 죽이면 **다른 브라우저에서 로그인한 세션까지 같이 죽는다.**

| 경로 | `sid` 를 어떻게 얻나 |
|---|---|
| code 교환 시 최초 발급 | `token` 이 code payload 에서 이미 `sid` 를 받는다(슬라이스 5). 그것을 `token-state` 의 issue 요청에 실어 보낸다 |
| 회전(rotate) | 이전 레코드의 `sid` 를 승계한다. 계열 전체가 같은 `sid` 를 갖는다 |
| client_credentials | 해당 없음 — refresh 를 발급하지 않는다. 컬럼은 nullable |

### 폐기 범위는 `sid` 단위

한 번의 로그아웃이 그 SSO 세션에 속한 **모든 client** 의 refresh 를 죽이고, **다른 세션은 건드리지 않는다.** `oidc_sessions` 를 `sid` 로 지우는 것과 정확히 같은 범위라 두 저장소가 같은 단위로 움직인다.

```sql
UPDATE refresh_tokens SET status = 'REVOKED', revoked_at = ?
 WHERE sid = ? AND status = 'ACTIVE'
```

**본질적으로 멱등**이다. 두 번째 실행은 0행을 갱신하고 끝난다. 이것이 5절의 at-least-once 요구를 별도 dedupe 없이 만족시킨다.

### 딸려 나오는 것 — refresh 재발급 id token 의 `sid`

`refresh_tokens` 가 `sid` 를 갖게 되면 README 의 이 한계가 **원인 자체를 잃는다**.

> "refresh 로 재발급한 id token 에는 `sid` 가 없다 — refresh token 레코드가 애초에 `sid` 를 보관하지 않으므로"

그냥 두면 문서가 거짓이 된다(이 저장소에서 네 번 재발한 실패 모드). 그래서 **refresh 재발급 id token 에도 `sid` 를 싣는다.** 컬럼이 이미 생겼으니 추가 비용이 거의 없고, 그래야 그 RP 가 나중에 back-channel logout 을 받았을 때 자기 세션과 대조할 수 있다.

---

## 5. 이벤트 계약

### 토픽

`oidc.session.logged-out.v1` — 파티션 3, replication factor 1(단일 브로커, 학습용). `docker-compose` 에 `apache/kafka` 공식 이미지를 **KRaft 모드**로 추가한다(Zookeeper 없음). 정확한 태그는 계획서에서 고정한다.

### 파티션 키는 `sid`

같은 세션의 이벤트는 같은 파티션에 들어가 순서가 보장된다. `sub` 로 잡으면 한 사용자의 모든 세션이 한 파티션에 몰리는데, 세션 간에는 순서 제약이 없으므로 병렬성만 잃는다.

### 페이로드

```json
{
  "eventId": "0d2a...",
  "sid": "Ax9c...",
  "sub": "user-sub-0001",
  "occurredAt": "2026-08-08T12:34:56.789Z"
}
```

**`clientId` 목록은 넣지 않는다.** 그것은 "그 세션에 무엇이 붙어 있었나"라는 `session` 의 내부 상태지 로그아웃이라는 **사실**이 아니다. 이벤트는 소비자가 당장 필요한 것이 아니라 일어난 사실을 서술해야 한다 — 소비자 요구에 맞춰 페이로드를 깎으면 두 번째 소비자가 붙는 순간 깨진다.

**`sub` 는 nullable 이다.** 위 예시는 채워진 값을 보여줄 뿐, 이 필드가 항상 있다는 뜻이 아니다. 등록된 RP 가 하나도 없는 세션(`openid` 없이 `offline_access` 만 받은 경로)은 `oidc_sessions` 행이 없어 소유자를 알 수 없다. 그런 세션도 그 `sid` 로 발급된 refresh token 은 존재할 수 있으므로 이벤트는 발행돼야 한다. 소비자가 폐기에 쓰는 값은 `sid` 하나뿐이라 `sub` 가 없어도 폐기는 정상 동작한다.

**`sub` 는 권위 있는 값이 아니다.** `session` 은 한 `sid` 아래 여러 `sub` 가 섞이는 것을 막지 않는다(`register` 에 그런 검사가 없다) — 실릴 때는 그 `sid` 의 첫 행 값 하나만 쓴다. 실제로 한 세션이 한 사용자로 유지되는 이유는 `auth` 의 `SessionIdIssuer.renew()` 가 로그인마다 항상 새 `sid` 를 발급하기 때문이다(슬라이스 5) — `session` 이 구조로 보장하는 게 아니라 다른 서비스의 규약에 기대는 것이다. 지금 소비자는 `sid` 만으로 판정하므로 영향이 없지만, 나중에 `sub` 를 신뢰하는 소비자(감사 로그 등)가 붙으면 이 사실을 알고 있어야 한다.

### 멱등은 소비자 쪽이고, 공짜다

Kafka 는 at-least-once 다. 처리를 끝내고 커밋 직전에 죽으면 같은 이벤트를 다시 받는다.

보통은 "이 이벤트를 처리했나"를 기록하는 표가 필요한데, 우리 폐기 연산이 조건부 갱신이라 **별도 dedupe 테이블이 필요 없다.**

그럼 `eventId` 는 왜 넣나 — 나중에 감사 로그 같은 **append-only 소비자**가 붙으면 그쪽은 멱등이 공짜가 아니다. 그때 필요한 것이 `eventId` 다. 지금은 추적용으로만 쓴다.

### 스키마 진화 — `.v1` 이 이름에 있는 이유

소비자는 보통 모르는 필드를 무시하도록 둔다(`FAIL_ON_UNKNOWN_PROPERTIES=false`). 그런데 슬라이스 3에서 이미 데인 자리다 — **검증 필드를 추가했더니 구버전이 조용히 무시해서 검증이 통째로 뚫렸다.** 예외도 안 났다.

그래서 규칙을 둔다. **보안 판단을 새 필드에 싣지 않는다.** 판단이 바뀌면 필드를 더하는 대신 **토픽 버전을 올린다**(`.v2`). 구버전은 새 토픽을 아예 구독하지 않으므로 "조용히 무시"가 불가능해진다.

### 소비 실패 — DLT

`token-state` 소비자는 수동 커밋(처리 성공 후 ack)이다. **무한 재시도로 일부러 설정하면** 그 파티션의 뒷 이벤트가 전부 막힌다(head-of-line blocking) — 한 사용자의 폐기가 안 되는 동안 다른 사용자들 폐기까지 멈춘다.

다만 이건 기본값 얘기가 아니다. Spring Boot 오토컨피그가 커스텀 에러 핸들러 빈 없이 쓰는 기본 `DefaultErrorHandler`(`FixedBackOff(0, 9)` — 9회, 0ms 간격)도 이미 유한 백오프라, 소진되면 ERROR 로그 한 줄을 남기고 그 레코드를 넘겨 다음으로 진행한다 — head-of-line blocking 은 기본값에서부터 일어나지 않는다(Task 8 이 `DeadLetterTopicTest` 에서 DLT 단언만 빼고 커스텀 빈 없이 돌려 통제 실험으로 확인했다).

그래서 `KafkaConsumerConfig` 가 실제로 바꾸는 것은 둘이다 — (1) 재시도 횟수·간격을 기본값(9회/0ms)에서 2회/200ms 로 명시 제어, (2) 재시도가 소진된 레코드의 행선지를 "로그 한 줄"에서 조회·재처리 가능한 `oidc.session.logged-out.v1.dlt` 토픽으로 바꾼다. 실질적 가치는 (2)의 관측성이다 — 실패가 조용히 사라지지 않는다는 뜻이지, 이 설정이 없으면 스트림이 막힌다는 뜻은 아니다.

---

## 6. outbox

### 왜 필요한가

`session` 이 로그아웃을 처리할 때 하는 일은 둘이다 — DB 에서 `oidc_sessions` 행 삭제, 그리고 Kafka 로 편지 보내기. **이 둘은 서로 다른 시스템이라 하나의 원자 단위가 될 수 없다.**

| 순서 | 사이에서 죽으면 |
|---|---|
| DB 먼저 → Kafka 나중 | **유실.** 로그아웃은 됐는데 편지가 영영 안 간다. refresh 가 살아남는다 |
| Kafka 먼저 → DB 나중 | **유령.** 일어나지 않은 로그아웃이 전파된다. 세션은 살아 있는데 refresh 만 죽는다 |

2단계 커밋은 느리고 장애 시 더 복잡해져 실무에서 거의 쓰지 않는다.

### 무엇을 하나

Kafka 에 바로 보내지 않고, **세션 삭제와 같은 트랜잭션 안에서** `outbox` 테이블에 한 줄 넣는다.

```
BEGIN
  DELETE FROM oidc_sessions WHERE sid = ?
  INSERT INTO outbox (...)
COMMIT
```

같은 DB 안이므로 둘 다 커밋되거나 둘 다 롤백된다. 틈이 사라진다.

별도 발행자가 주기적으로 `outbox` 를 훑어 미발행 행을 Kafka 로 보내고 표시한다. 이 단계는 실패해도 편지가 DB 에 남아 있으므로 다음 주기에 재시도된다.

여기서 at-least-once 가 생긴다 — 보내기는 성공했는데 표시 직전에 죽으면 다음 주기에 다시 보낸다. 그래서 소비자가 멱등해야 하고, 4절에서 그것이 공짜였다.

### outbox 테이블 (`ms_session.outbox`)

| 컬럼 | 용도 |
|---|---|
| `id` | PK |
| `event_id` | 편지의 `eventId`. 재발행해도 같은 값 |
| `topic` | 대상 토픽 |
| `partition_key` | `sid` |
| `payload` | JSON 본문 |
| `created_at` | 생성 시각 |
| `published_at` | 발행 시각. `null` 이면 미발행 |

발행자는 `published_at IS NULL` 인 행을 오래된 것부터 가져간다.

### 발행자는 `session` 안 in-process 폴러

`@Scheduled` 스케줄러 하나, **고정 주기 500ms**(설정으로 조정 가능). 배포 단위가 늘지 않고, `session` 이 자기 테이블을 읽는 것이므로 **소유권을 위반하지 않는다.**

이 주기가 곧 8절 성공 기준 12번의 보안 창 하한이다 — 500ms 로 잡으면 로그아웃 후 최대 그만큼은 refresh 가 아직 살아 있다. 값을 키우면 DB 부하가 줄고 창이 넓어진다. **맞바꿈이 설정 한 줄에 드러나는 것 자체가 이 절의 요점이다.**

검토했으나 채택하지 않은 대안:

- **전용 워커(별도 배포 단위) + `session` 의 outbox API** — 분리의 목적인 자원 격리가 REST 를 거치면서 사라진다(폴링이 `session` 의 요청 처리 스레드·커넥션을 그대로 탄다). 그리고 `claim`/`published`/`release` API 는 결국 `FOR UPDATE SKIP LOCKED` 를 HTTP 로 흉내내는 것이고, 임대 만료와 "같은 키는 한 워커에게만"까지 프로토콜로 설계해야 한다. **여러 서비스의 outbox 를 한 relay 가 모으는 공용 플랫폼 컴포넌트**로는 말이 되지만, outbox 를 쓰는 서비스가 하나뿐인 지금은 비용만 낸다
- **CDC** — 경계 바깥. 백로그로 넘긴다

### 재현 단계 — 직접 발행 먼저

먼저 **직접 발행**으로 만들어 두 실패를 실제로 보인 뒤 outbox 로 전환한다.

| | 직접 발행에서 | outbox 로 바꾸면 |
|---|---|---|
| **유실** — Kafka 정지 후 로그아웃 | **로그아웃 트랜잭션 전체가 롤백된다** — 세션 행도 안 지워지고 refresh 도 안 죽는다 | 편지가 outbox 에 남아, Kafka 를 올리면 그때 나가서 폐기된다 |
| **유령** — 커밋 실패 주입 | 세션은 남는데 refresh 만 죽는다 | 편지도 함께 롤백돼 아예 안 나간다 |

주의. **유실 행은 최초 예측과 실제 구현이 갈린 자리다(Task 7 재검토).** `publish()` 는
     `SessionService.consumeForLogout` 의 `@Transactional` 메서드 **안에서 블로킹으로** 불린다. Kafka
     가 죽어 있어 `publish()` 가 예외를 던지면 그 예외가 트랜잭션 프록시까지 그대로 올라가 세션 삭제까지
     통째로 롤백시킨다 — "세션은 지워지는데 편지만 못 갔다"(유실)가 아니라 애초에 아무 것도 지워지지
     않는다. Task 6 의 `DirectPublishFailureModeTest.publishFailureRollsBackTheWholeLogout` 이 이 결과를
     실제로 고정했다. 최종 결과(세션도 안 지워지고 refresh 도 안 죽는다)는 원래 예측과 같지만 경로는 다르다.

주의. **유령 행은 Task 6 이 실제로 재현하지는 않았다.** `publishHappensBeforeCommit` 은 커밋 실패를
     주입하지 않는다 — `publish()` 가 커밋 **전에** 불려 그 순간 밖에서는 세션 행이 아직 보인다는 전제만
     확인한다. 그 전제가 성립해야 "발행은 됐는데 커밋이 나중에 실패하는" 유령이 원리적으로 가능해진다는
     뜻이지, 그 전제로부터 실제로 커밋을 실패시켜 관찰한 테스트가 있었다는 뜻은 아니다. 이 표의 "유령"
     칸은 그 전제로부터의 추론으로 남겨둔다.

두 실패를 **먼저 통과하는 테스트로 고정**하고, outbox 로 바꾼 뒤 그 테스트가 반대 결과를 요구하도록
뒤집는다. "outbox 가 필요하다"가 설명이 아니라 **재현된 실패**로 남는다. Task 7 의
`OutboxFailureModeTest`(옛 `DirectPublishFailureModeTest`)가 그 뒤집힌 결과를 담는다 — `record()` 는
Kafka 를 직접 부르지 않으므로 "유실" 재현은 "Kafka 가 죽어 있어도 로그아웃이 커밋되고 편지는 outbox 에
남는다"가 되고, "유령" 재현은 `TransactionTemplate` 으로 커밋을 강제 rollback 시켜 outbox 행도 세션
삭제와 함께 사라지는지를 본다.

---

## 7. 실패 모드

| 무엇이 죽었나 | 결과 |
|---|---|
| `session` | `auth` 의 통지가 실패 → **fail-open**(슬라이스 5 결정). 세션은 끊기고 `sid` 는 고아, refresh 도 안 죽는다. **기존 한계 그대로** |
| **Kafka** | 편지가 outbox 에 쌓인다. 살아나면 나가서 폐기된다. **지연되지만 결국 된다** |
| `token-state` | 편지가 Kafka 에 남는다. 살아나면 읽는다. **지연되지만 결국 된다** |
| 특정 편지만 계속 실패 | 제한 재시도 후 DLT. 그 한 건은 처리 안 되고 DLT 로 남는다 |

가운데 두 줄이 이 슬라이스에서 얻는 것이다. 동기 REST 였다면 둘 다 "실패하고 끝"이었다.

**마지막 줄의 "뒷줄이 막히지 않는다"는 이 DLT 설정이 새로 만든 성질이 아니다.** Spring Boot 가
커스텀 에러 핸들러 빈 없이 쓰는 기본 `DefaultErrorHandler` 도 이미 유한 재시도(`FixedBackOff(0, 9)`
— 9회, 0ms)라, 소진되면 로그 한 줄을 남기고 다음 레코드로 넘어간다 — head-of-line blocking 은
기본값에서부터 일어나지 않는다. Task 8 이 통제 실험으로 확인했다: `DeadLetterTopicTest` 에서
`kafkaErrorHandler` 빈을 주석 처리하고 DLT 단언만 뺀 채 돌려도 "뒤 편지 처리" 단언은 통과했다
(task-8-report.md 참고). 이 슬라이스의 DLT 설정이 실제로 바꾸는 것은 재시도 횟수·간격의 명시 제어
(2회/200ms)와, 소진된 레코드의 행선지를 로그에서 조회·재처리 가능한 DLT 토픽으로 바꾸는 관측성이다
— "안 막힌다"가 아니라 "실패가 안 보이지 않게 된다"가 이 설정의 성과다.

### 보안 창은 남는다

로그아웃 → outbox 기록(즉시) → **폴링 주기** → Kafka → 소비 → 폐기. 이 창 동안은 refresh 로 새 access token 을 받을 수 있다.

**이것이 outbox 의 대가다 — 원자성과 전달 보장을 얻는 대신 즉시성을 잃는다.** 동기 REST 는 정반대였다(창이 0이지만 전달 보장이 없다).

실무에서는 동기 호출을 먼저 시도하고 이벤트를 보증으로 쓰는 조합을 쓴다. 여기서는 하지 않는다 — 주제가 흐려진다. 한계로 기록한다.

---

## 8. 성공 기준

**분리**

1. `svc_user_directory` 계정으로 `ms_client_registry.clients` 조회 → **권한 오류**
2. 5개 스키마에 각자 자기 테이블만 존재

**결손이 닫혔나**

3. 로그아웃 전에는 refresh 로 새 access token 발급 성공 → 로그아웃 후(창 경과)에는 같은 refresh 로 `invalid_grant`
4. **다른 세션은 산다** — 세션 A 를 로그아웃해도 세션 B 의 refresh 는 유효
5. refresh 재발급 id token 에 `sid` 가 실린다

**재현된 실패 (직접 발행 단계)**

6. Kafka 정지 상태로 로그아웃 → 세션은 지워지고 **refresh 는 살아남는다**
7. 커밋 실패 주입 → 세션은 남는데 **refresh 만 죽는다**

**outbox 로 전환한 뒤 같은 상황**

8. Kafka 정지 상태로 로그아웃 → outbox 에 행이 남음 → Kafka 재기동 → **폐기된다**
9. 커밋 실패 주입 → outbox 행도 없고 편지도 안 나간다

**전달 성질**

10. 같은 이벤트를 두 번 처리 → 두 번째는 0행 갱신, 오류 없음
11. 계속 실패하는 편지 → 제한 재시도 후 DLT 로 이동, **그 뒤 편지는 정상 처리**
12. **보안 창을 숫자로 측정** — 로그아웃 시각부터 폐기 완료까지 실측값을 README 에 기록

---

## 9. 이 설계가 관계형 DB 에 기대는 것

나중에 저장소를 바꿀 때 무엇이 깨지는지 미리 표시한다.

- **여러 테이블에 걸친 트랜잭션** — `oidc_sessions` 삭제와 `outbox` 기록을 한 번에. outbox 패턴의 전제 자체다
- **조건 기반 대량 갱신** — `UPDATE ... WHERE sid = ? AND status = ACTIVE` 한 방. 키-값 저장소면 조회 후 반복 갱신이 된다
- **인덱스 스캔 기반 폴링** — `WHERE published_at IS NULL ORDER BY created_at`. DynamoDB 라면 sparse GSI + 샤딩이 필요하고, 애초에 Streams 를 쓰게 된다(CDC 가 DB 에 내장돼 있으므로 "인프라라서 미룬다"는 판단이 성립하지 않는다)

그리고 슬라이스 3의 `@Lock(PESSIMISTIC_WRITE)` 는 키-값 저장소에 없다. 버전 속성 + 조건부 쓰기로 낙관적 잠금을 직접 만들고 재시도 루프를 짜야 하므로, 재사용 탐지 로직이 통째로 다시 설계된다.

---

## 10. 알려진 한계 (이 슬라이스가 남기는 것)

- **보안 창** — 폴링 주기만큼 폐기가 늦다. 즉시성이 필요하면 동기 호출을 병행하고 이벤트를 보증으로 쓰는 조합이 있다
- **`session` 다중 인스턴스에서 발행자 경쟁** — 폴러가 같은 행을 집어 중복 발행이 는다. 소비자가 멱등이라 피해는 없다. 정석 해법은 `FOR UPDATE SKIP LOCKED`, 다른 길은 outbox API 계약을 만들어 전용 relay 를 붙이는 것. 지금은 인스턴스가 하나라 도달하지 않는다
- **`sid` 가 `null` 인 옛 refresh 행** — 폐기에 걸리지 않는다. 스키마 재생성으로 실제로는 도달 불가지만 컬럼이 nullable 인 이상 코드상 가능성은 남는다
- **이벤트 페이로드의 `sub` 가 `null` 일 수 있다** — 등록된 RP 가 없는 세션(`openid` 없이 `offline_access` 만 받은 경로)은 `oidc_sessions` 행이 없어 소유자를 모른 채 이벤트가 나간다. 소비자는 폐기에 `sid` 만 쓰므로 동작에는 영향이 없지만, 나중에 `sub` 를 쓰는 소비자가 붙으면 이 nullable 을 반드시 처리해야 한다
- **`outbox` 정리 수단 없음** — 발행된 행이 무한히 쌓인다. `oidc_sessions` purge 부재와 같은 성격
- **`session` 이 죽었을 때의 fail-open** — 슬라이스 5의 결정 그대로. 통지도 폐기도 일어나지 않는다
- **`root` 계정이 초기화용으로 남는다** — 컨테이너 내부에서만 쓰이지만 존재 자체는 남는다

## 11. 백로그로 넘기는 것

- **CDC(Debezium)** — 폴링을 대체. 인프라 프로젝트
- **전용 outbox relay + API 계약** — 여러 서비스가 outbox 를 쓰게 되면 그때
- **저장소 이기종화** — `session` 만 DynamoDB 로 옮기는 것. 이 슬라이스의 스키마 분리가 선행 조건이고, 발행자가 폴링에서 Streams 로 바뀌는 것을 직접 비교할 수 있다. **우선순위 최하 — 하지 않을 수도 있다**
- **감사·관측 소비자** — 같은 토픽을 읽어 감사 로그를 쌓는 두 번째 소비자. `eventId` 기반 dedupe 가 그때 필요해진다
