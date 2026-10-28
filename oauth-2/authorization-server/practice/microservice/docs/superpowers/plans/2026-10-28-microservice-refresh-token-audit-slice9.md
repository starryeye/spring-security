# 마이크로서비스 인가 서버 슬라이스 9 — refresh token 이력 감사 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `token-state` 가 refresh token 계열의 발급·회전·폐기를 outbox 로 발행하고, `audit` 이 그것을 받아 적어 운영자가 한 사용자의 계열 이력을 Bearer 토큰으로 조회하게 한다.

**Architecture:** `token-state` 는 상태를 바꾸는 트랜잭션 안에서 `outbox` 행을 쓰고, 폴러(500ms)가 `oidc.refresh-token.lifecycle.v1`(키 `familyId`)로 보낸다. 시각은 계열 잠금을 얻은 뒤에 재고, 로그아웃 폐기(`revokeBySid`)는 회전과 같은 계열 잠금 경로를 탄다. `audit` 은 같은 그룹(`audit`)으로 새 토픽을 소비해 `refresh_token_events` 에 쌓고, `GET /audit/users/{sub}/refresh-token-families` 를 Spring Security resource server(`audit.read` scope)로 지킨다.

**Tech Stack:** Spring Boot 3.4.5, Java 21, Spring for Apache Kafka, Spring Security 6.4.5 (`spring-boot-starter-oauth2-resource-server`), Nimbus JOSE+JWT, MySQL 8, Apache Kafka 3.9.2(KRaft), h2·EmbeddedKafka(테스트)

**Spec:** [2026-10-27-microservice-refresh-token-audit-slice9-design.md](../specs/2026-10-27-microservice-refresh-token-audit-slice9-design.md)

모든 경로는 `oauth-2/authorization-server/practice/microservice/` 기준이다.

## Global Constraints

- Spring Boot **3.4.5**, `io.spring.dependency-management` **1.1.7**, Java toolchain **21**, gradle wrapper **8.13**.
- **`session/src` 는 한 줄도 바뀌면 안 된다**(성공 기준 10). 확인: `git diff --stat a98d192 -- oauth-2/authorization-server/practice/microservice/session/src` 가 비어 있어야 한다.
- **`token`·`auth`·`signing`·`consent`·`user-directory`·`demo-rp`·`gateway` 도 바꾸지 않는다**(작성자 판단: `token` 의 client_credentials 는 `client_scopes` 의 `audit.read` 를 코드 변경 없이 내준다 — `ClientCredentialsGrantService.grant` 를 읽은 결과다. Task 6 e2e 가 실제 토큰으로 확인한다). 어느 태스크든 이 모듈들을 고쳐야 한다고 판단되면 고치지 말고 BLOCKED 로 보고한다.
- **빌드 명령**: 각 모듈 디렉토리에서 `JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn $JAVA_HOME/bin/java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain cleanTest test --no-daemon`. PATH 의 java 는 17이다. `cleanTest` 를 빼면 UP-TO-DATE 로 테스트가 안 돈다.
- **토픽**: `oidc.refresh-token.lifecycle.v1` — 파티션 3, replicas 1, 키 `familyId`. 감사 DLT: `oidc.refresh-token.lifecycle.v1.audit.dlt` — 파티션 3, replicas 1. 발행자(`token-state`)와 소비자(`audit`)가 모두 `NewTopic` 으로 같은 값을 선언한다.
- **페이로드 필드 이름**(JSON, 이 철자 그대로): 공통 `eventId`, `kind`, `occurredAt`, `familyId`, `sub`, `clientId`, `sid` / `ISSUED`: `expiresAt`, `familyExpiresAt` / `ROTATED`: `expiresAt` / `REVOKED`: `reason`, `revokedTokens`, `activeTokensRevoked`. `kind` 값은 `ISSUED`·`ROTATED`·`REVOKED`. `reason` 값은 `REUSE_DETECTED`·`CLIENT_REVOKED`·`SESSION_LOGGED_OUT`(`refresh_tokens.revoked_reason` 과 같은 문자열). 시각은 ISO-8601 문자열. **토큰 원문·해시는 싣지 않는다.**
- **바뀐 행이 0이면 사건도 없다.** 상태를 바꾸지 않은 시도(`NOT_FOUND`·`CLIENT_MISMATCH`·`REVOKED`·`EXPIRED`·`SCOPE_EXCEEDED`, `revoke` 가 `false`, 이미 전부 끊긴 계열의 `revoke`)는 outbox 행을 만들지 않는다.
- **운영자 client**: `audit-operator` / `secret`, `grant_types = client_credentials`, `client_scopes = audit.read`, `scopes`·`redirect_uris`·`post_logout_redirect_uris` 빈 문자열, `backchannel_logout_uri = null`. `audit.read` 는 discovery 의 `scopes_supported` 에 넣지 않는다.
- **조회 API**: `GET /audit/users/{sub}/refresh-token-families`, `audit:8089` 직접(게이트웨이 비노출). 검증 값: `iss = http://localhost:9000`, jwks `http://localhost:8083/oauth2/jwks`, `typ = at+jwt`, scope `audit.read`. 오류는 RFC 6750(401 / 401 `invalid_token` / 403 `insufficient_scope`), 키를 확보하지 못하면 500.
- **`token-state` 테스트는 실제 브로커로 발행하지 않는다.** 테스트 yml 의 `my.outbox-poll-interval-ms` 는 `3600000` 이다. 발행을 보는 테스트는 `publishPending()` 을 직접 부르고 EmbeddedKafka 로만 보낸다 — 스택을 띄워 둔 채 테스트를 돌려도 가짜 사건이 실제 `audit` 에 들어가지 않게 하기 위해서다.
- **주석은 한국어**, 기존 파일 문체를 따른다. 함정·판단 근거는 `주의.` 문단으로, 현재형 지식 문체로 쓴다. 경험담 서술("겪었다", "확인했다", "돌려 보니", "슬라이스 N 에서 ~하게 됐다")을 넣지 않는다 — session 의 `OutboxPublisher` 주석처럼 복제 원본에 그런 문장이 있으면 규칙만 옮긴다. **코드가 하는 일보다 한 칸이라도 세게 주장하지 않는다.**
- **이 계획이 적은 라이브러리 동작은 출처가 붙어 있다.** `(코드 읽기: …)` 는 jar 의 바이트코드를 읽은 결과이고 실측이 아니다. 그 사실을 주석·README 에 옮길 때는 출처를 문장에 남기거나("…의 코드상 기본값은 …") 직접 측정한 뒤 측정으로 적는다. `(실측 필요)` 는 아직 아무도 확인하지 않은 것이다. **계획의 문장을 측정 결과처럼 옮겨 적지 않는다.**
- **`git add` 는 경로를 명시한다.** `-A`, `-a`, `.` 금지 — 저장소에 영구 미커밋 자격증명 파일이 있다. 커밋마다 `git push origin main` 까지 하고, 강제 푸시는 하지 않는다. 커밋 메시지 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- 새 테스트마다 구현을 일부러 되돌려 그 테스트가 실패하는 것을 보고, **되돌린 코드와 깨진 단언 사이의 인과를 스스로 따져** `git diff` 와 함께 보고서에 남긴 뒤 복구한다.
- 브로커·MySQL 이 관련된 측정 전에는 `docker ps --filter name=microservice-as --format '{{.Names}} {{.Status}}'` 로 실제 상태를 확인하고 측정 조건을 적는다.
- **MySQL 이 있어야 도는 테스트**(`RefreshTokenServiceMySqlLockSemanticsTest`)는 MySQL 을 띄우고 돌려, 보고서에 그 클래스가 **건너뛰어지지 않았음**(테스트 리포트 XML 의 `skipped="0"`)을 남긴다. 돌리지 못했으면 README·한계에 "InnoDB 에서 보이지 못했다"고 쓴다.

## Review Focus

스펙이 암시하지만 성공 기준에 직접 적히지 않은 입력 중 실제로 물 가능성이 높은 순서다. 각 줄의 테스트는 소유 태스크에 들어 있다.

1. **한 OP 세션에 client 가 둘인 로그아웃**(두 RP 가 각자 `offline_access` 계열을 가진 세션) — 계열마다 `REVOKED` 사건이 하나씩, 각자 자기 `familyId`·`clientId` 로 나가야 한다. → Task 3 `logoutRecordsOneRevokedEventPerFamily`
2. **`ISSUED` 사건이 없는 계열**(그 사건이 감사 DLT 로 갔거나 보존 기간 밖) — 조회가 실패하지 않고 계열을 보여 준다. `issuedAt`·`familyExpiresAt` 은 null, 만료 판정은 남은 `ROTATED` 의 `expiresAt` 으로 한다. → Task 5 `familyWithoutIssuedEventIsStillShown`
3. **필드 타입이 틀린 페이로드**(`"revokedTokens":"3"`, `"sub":123`, `"eventId":""`) — 강제 변환해 기록하지 않고 감사 DLT 로 간다. `JsonNode.asText()`·`asInt()` 는 조용히 변환한다. → Task 4 `JsonFieldsTest`, `wrongTypedCountGoesToDlt`
4. **일부가 이미 끊긴 계열의 폐기**(로그아웃으로 `ACTIVE` 가 끊긴 뒤 옛 토큰으로 `/oauth2/revoke`) — 실제로 바뀐 `CONSUMED` 행만 세어 `REVOKED(CLIENT_REVOKED, revokedTokens=n, activeTokensRevoked=0)`. → Task 3 `revokeOfPartlyRevokedFamilyCountsOnlyChangedRows`
5. **알 수 없는 `kind`**(같은 v1 계약에 종류가 더해진 경우) — 기록되고 사건 목록에 보이지만 상태 판정에는 쓰이지 않는다. → Task 4 `unknownKindIsRecorded`, Task 5 `unknownKindDoesNotChangeTheState`

---

## File Structure

### `token-state`

| 경로 | 책임 | 태스크 |
|---|---|---|
| `src/main/java/dev/starryeye/token_state/jpa/OutboxEntity.java` (신규) | 미발행 사건 한 줄. `session` 의 같은 이름 클래스와 같은 모양, `payload` 길이만 명시 | 1 |
| `src/main/java/dev/starryeye/token_state/jpa/OutboxEntityRepository.java` (신규) | `findTop100ByPublishedAtIsNullOrderByIdAsc()` | 1 |
| `src/main/java/dev/starryeye/token_state/outbox/OutboxPublisher.java` (신규) | `@Scheduled` 폴러 | 1 |
| `src/main/java/dev/starryeye/token_state/event/RefreshTokenLifecycleEvent.java` (신규) | 페이로드 record | 1 |
| `src/main/java/dev/starryeye/token_state/event/RefreshTokenEventPublisher.java` (신규) | 사건을 outbox 행으로 쓴다(호출자 트랜잭션에 참여) | 1 |
| `src/main/java/dev/starryeye/token_state/event/KafkaTopicConfig.java` (신규) | 발행 토픽 `NewTopic` | 1 |
| `src/main/java/dev/starryeye/token_state/TokenStateApplication.java` | `@EnableScheduling` | 1 |
| `src/main/java/dev/starryeye/token_state/RefreshTokenService.java` | 사건 발행, 잠금 뒤 시각, `revokeBySid` 재구성 | 1·2·3 |
| `src/main/java/dev/starryeye/token_state/jpa/RefreshTokenEntity.java` | `revoke` 가 바꿨는지 돌려준다 | 2 |
| `src/main/java/dev/starryeye/token_state/jpa/RefreshTokenEntityRepository.java` | 계열 id 스칼라 조회 추가, 벌크 UPDATE 제거 | 3 |
| `src/main/java/dev/starryeye/token_state/event/SessionLoggedOutConsumer.java` | 주석만 | 3 |
| `src/main/resources/application.yml`, `src/test/resources/application.yml` | `my.outbox-poll-interval-ms` | 1 |
| `src/test/java/dev/starryeye/token_state/OutboxEvents.java` (신규) | 테스트 도우미: 계열의 outbox 행·사건 | 1 |
| `src/test/java/dev/starryeye/token_state/RefreshTokenFixtures.java` (신규) | 테스트 도우미: `RefreshTokenServiceRotateTest` 의 만료 헬퍼를 옮긴 것 | 2 |
| `src/test/java/dev/starryeye/token_state/RefreshTokenServiceEventTest.java` (신규) | 경로별 사건 내용 | 1·2 |
| `src/test/java/dev/starryeye/token_state/RefreshTokenEventAtomicityTest.java` (신규) | 사건을 못 쓰면 상태 변경도 롤백 | 1·2 |
| `src/test/java/dev/starryeye/token_state/RefreshTokenServiceSilentFailureTest.java` (신규) | 실패 경로는 사건이 없다 | 2 |
| `src/test/java/dev/starryeye/token_state/RefreshTokenEventOrderTest.java` (신규) | 잠금 뒤 시각(h2, 결정적) | 2 |
| `src/test/java/dev/starryeye/token_state/outbox/OutboxPublisherTest.java` (신규) | 폴러 발행 | 1 |
| `src/test/java/dev/starryeye/token_state/RefreshTokenServiceRevokeBySidTest.java` | 로그아웃 사건 | 3 |
| `src/test/java/dev/starryeye/token_state/event/SessionLoggedOutConsumerTest.java` | 재전달 | 3 |
| `src/test/java/dev/starryeye/token_state/RefreshTokenServiceMySqlLockSemanticsTest.java` | InnoDB 경합 셋 | 3 |
| `src/test/java/dev/starryeye/token_state/RefreshTokenServiceRotateTest.java`, `jpa/RefreshTokenEntityTest.java` | 헬퍼 이동, `revoke` 반환값 | 2 |

### `audit`

| 경로 | 책임 | 태스크 |
|---|---|---|
| `src/main/java/dev/starryeye/audit/IdempotentInsert.java` (신규) | 선조회 → save → 위반 시 재확인. `AuditRecorder` 에서 뽑아낸다 | 4 |
| `src/main/java/dev/starryeye/audit/RefreshTokenEventRecord.java`, `RefreshTokenEventRecorder.java` (신규) | 기록 입력과 멱등 기록 | 4 |
| `src/main/java/dev/starryeye/audit/jpa/RefreshTokenEventEntity.java`, `RefreshTokenEventEntityRepository.java` (신규) | `refresh_token_events` | 4 |
| `src/main/java/dev/starryeye/audit/event/JsonFields.java` (신규) | 타입을 지키는 필드 읽기 | 4 |
| `src/main/java/dev/starryeye/audit/event/RefreshTokenEventConsumer.java` (신규) | 새 토픽 소비 | 4 |
| `src/main/java/dev/starryeye/audit/AuditRecorder.java`, `event/AuditEventConsumer.java`, `event/KafkaConsumerConfig.java` | `IdempotentInsert`·`JsonFields` 사용, 새 토픽·DLT 선언 | 4 |
| `src/main/java/dev/starryeye/audit/query/*.java` (신규) | 조회 서비스·응답·컨트롤러 | 5 |
| `src/main/java/dev/starryeye/audit/config/SecurityConfig.java` (신규) | resource server | 5 |
| `build.gradle`, `src/main/resources/application.yml`, `src/test/resources/application.yml` | 의존성, `my.issuer`·`my.jwk-set-uri` | 5 |
| `src/test/java/dev/starryeye/audit/...` | Task 4·5 의 테스트 | 4·5 |

### 그 밖

| 경로 | 무엇을 | 태스크 |
|---|---|---|
| `client-registry/src/main/java/dev/starryeye/client_registry/ClientSeedInitializer.java` + 그 테스트 | `audit-operator` 시드 | 5 |
| `http/e2e-login-logout.sh` | `login` 이 `sub=`·`id_token=` 도 출력 | 6 |
| `http/e2e-refresh-audit.sh` (신규) | refresh·revoke·운영자 토큰·조회 | 6 |
| `README.md` | 서비스 표·기동·관통 flow·API 절·검증 결과·한계·문서 링크 | 6 |

---

## Task 1: `token-state` outbox 와 `ISSUED` 사건

**Files:**
- Create: `token-state/src/main/java/dev/starryeye/token_state/jpa/OutboxEntity.java`, `jpa/OutboxEntityRepository.java`, `outbox/OutboxPublisher.java`, `event/RefreshTokenLifecycleEvent.java`, `event/RefreshTokenEventPublisher.java`, `event/KafkaTopicConfig.java`
- Modify: `token-state/src/main/java/dev/starryeye/token_state/TokenStateApplication.java`, `RefreshTokenService.java`(`issue` 만), `src/main/resources/application.yml`, `src/test/resources/application.yml`
- Test: `token-state/src/test/java/dev/starryeye/token_state/OutboxEvents.java`, `RefreshTokenServiceEventTest.java`, `RefreshTokenEventAtomicityTest.java`, `outbox/OutboxPublisherTest.java`

**Interfaces:**
- Produces:
  - `RefreshTokenEventPublisher.LIFECYCLE_TOPIC = "oidc.refresh-token.lifecycle.v1"`
  - `void RefreshTokenEventPublisher.issued(RefreshTokenEntity issued, Instant at)`
  - `void RefreshTokenEventPublisher.rotated(RefreshTokenEntity rotated, Instant at)` — `expiresAt` 만 싣는다(Task 2 가 부른다)
  - `void RefreshTokenEventPublisher.revoked(RefreshTokenEntity member, Instant at, String reason, int revokedTokens, int activeTokensRevoked)` — `member` 에서 `familyId`·`sub`·`clientId`·`sid` 를 읽는다(Task 2·3 이 부른다)
  - `record RefreshTokenLifecycleEvent(String eventId, String kind, Instant occurredAt, String familyId, String sub, String clientId, String sid, Instant expiresAt, Instant familyExpiresAt, String reason, Integer revokedTokens, Integer activeTokensRevoked)` — 클래스 수준 `@JsonInclude(JsonInclude.Include.NON_NULL)`(같은 모듈의 `RotateResult` 관용). 그래서 `sid` 가 null 이면 키 자체가 빠진다 — 소비자는 "없음"과 JSON null 을 같게 읽는다(Task 4)
  - `OutboxEntity`(`eventId`, `topic`, `partitionKey`, `payload`, `createdAt`, `publishedAt`, `markPublished(Instant)`), `OutboxEntityRepository.findTop100ByPublishedAtIsNullOrderByIdAsc()`
  - 테스트 도우미 `OutboxEvents`: `static List<OutboxEntity> rowsOf(OutboxEntityRepository, String familyId)`(id 오름차순), `static List<JsonNode> eventsOf(OutboxEntityRepository, ObjectMapper, String familyId)`, `static List<String> kindsOf(OutboxEntityRepository, ObjectMapper, String familyId)`. 이 모듈의 h2 는 이름 있는 인메모리 DB 라 컨텍스트끼리 테이블을 공유한다 — 단언은 언제나 `familyId`(UUID)로 걸러서 한다

- [ ] **Step 1: 실패하는 테스트 — 발급이 `ISSUED` 를 쓴다**

`RefreshTokenServiceEventTest`(`@SpringBootTest`, `RefreshTokenService`·`OutboxEntityRepository`·`ObjectMapper`·`TokenGenerator` 주입):

```java
@Test
@DisplayName("발급하면 같은 트랜잭션에서 ISSUED 사건을 outbox 에 쓴다")
void issueRecordsIssuedEvent() throws Exception {
	IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

	List<OutboxEntity> rows = OutboxEvents.rowsOf(outboxRepository, issued.familyId());
	assertThat(rows).hasSize(1);
	assertThat(rows.get(0).getTopic()).isEqualTo("oidc.refresh-token.lifecycle.v1");
	assertThat(rows.get(0).getPartitionKey()).isEqualTo(issued.familyId());

	JsonNode event = objectMapper.readTree(rows.get(0).getPayload());
	assertThat(event.get("eventId").asText()).isEqualTo(rows.get(0).getEventId());
	assertThat(event.get("kind").asText()).isEqualTo("ISSUED");
	assertThat(event.get("familyId").asText()).isEqualTo(issued.familyId());
	assertThat(event.get("sub").asText()).isEqualTo("user-sub-0001");
	assertThat(event.get("clientId").asText()).isEqualTo("my-client");
	assertThat(event.get("sid").asText()).isEqualTo("SID-A");
	Instant occurredAt = Instant.parse(event.get("occurredAt").asText());
	Instant expiresAt = Instant.parse(event.get("expiresAt").asText());
	assertThat(expiresAt.getEpochSecond()).isEqualTo(issued.expiresAt());
	assertThat(Duration.between(occurredAt, expiresAt)).isEqualTo(Duration.ofSeconds(60));          // 테스트 yml ttl
	assertThat(Duration.between(occurredAt, Instant.parse(event.get("familyExpiresAt").asText())))
			.isEqualTo(Duration.ofSeconds(300));                                                     // 테스트 yml 계열 상한
	assertThat(event.has("reason")).isFalse();
	assertThat(event.has("revokedTokens")).isFalse();
	assertThat(event.has("activeTokensRevoked")).isFalse();
	assertThat(rows.get(0).getPayload())
			.doesNotContain(issued.refreshToken())
			.doesNotContain(tokenGenerator.hash(issued.refreshToken()));
}

@Test
@DisplayName("sid 가 없는 발급은 sid 키 없이 나간다")
void issuedEventOmitsAbsentSid() throws Exception {
	IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, null);

	assertThat(OutboxEvents.eventsOf(outboxRepository, objectMapper, issued.familyId()).get(0).has("sid")).isFalse();
}
```

`RefreshTokenEventAtomicityTest`(`@SpringBootTest`, `@MockitoSpyBean RefreshTokenEventPublisher eventPublisher`):

```java
@Test
@DisplayName("ISSUED 를 쓰지 못하면 발급도 없던 일이 된다")
void issueRollsBackWhenTheEventCannotBeRecorded() {
	doThrow(new IllegalStateException("outbox unavailable")).when(eventPublisher).issued(any(), any());
	long refreshRows = repository.count();

	assertThatThrownBy(() -> service.issue("my-client", "user-sub-0001", "openid", 1000L, "SID-A"))
			.isInstanceOf(IllegalStateException.class);

	assertThat(repository.count()).isEqualTo(refreshRows);
}
```

- [ ] **Step 2: 실패 확인** — 위 두 클래스가 컴파일 실패(없는 타입)로 떨어진다.

- [ ] **Step 3: 구현**

- `OutboxEntity`: `session/src/main/java/dev/starryeye/session/jpa/OutboxEntity.java` 와 같은 컬럼·인덱스(`outbox`, `idx_outbox_unpublished` = `published_at, id`). 다른 점은 `payload` 하나 — `@Lob @Column(nullable = false, length = 16_777_215)`. 이유를 `주의.` 로 적는다: `@Lob String` 만 두면 Hibernate 6 + MySQL 의 DDL 이 `tinytext`(255바이트)가 되고, 이 사건 페이로드는 그보다 길다. 값은 `audit` 의 `AuditEventEntity.payload` 와 같은 규칙(`16_777_215` → `mediumtext`)을 따른다고 그 클래스를 가리킨다.
- `RefreshTokenEventPublisher`(`@Component`): `session` 의 `LogoutEventPublisher` 와 같은 모양 — 레코드를 만들고 `ObjectMapper` 로 직렬화해 `OutboxEntity` 를 `save` 한다. `eventId` 는 `UUID.randomUUID()`, `topic` 은 `LIFECYCLE_TOPIC`, `partitionKey` 는 `familyId`, `createdAt` 은 `at`. 직렬화 실패는 `IllegalStateException` 으로 던져 호출자 트랜잭션을 죽인다. 주석: 호출자 트랜잭션에 참여한다는 것, 키가 계열인 이유(한 계열의 사건이 한 파티션에서 순서대로 소비된다 — 다만 조회의 정렬 기준은 `occurredAt` 이다).
- `KafkaTopicConfig`: `@Bean NewTopic refreshTokenLifecycleTopic()` → `TopicBuilder.name(LIFECYCLE_TOPIC).partitions(3).replicas(1)`. 주석: `audit` 의 `KafkaConsumerConfig` 도 같은 값으로 선언한다(Task 4), 두 선언은 같아야 한다.
- `RefreshTokenService`: 생성자에 `RefreshTokenEventPublisher` 를 더하고, `issue` 의 `repository.save(entity)` 다음에 `eventPublisher.issued(entity, now)`.

- [ ] **Step 4: 통과 확인** — `RefreshTokenServiceEventTest`, `RefreshTokenEventAtomicityTest` 통과.

- [ ] **Step 5: 실패하는 테스트 — 폴러가 보낸다**

`outbox/OutboxPublisherTest`:

```java
@SpringBootTest(properties = {
		"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
		// 전용 h2 — 다른 컨텍스트가 공유 DB 에 남긴 미발행 행을 이 폴러가 함께 보내지 않게
		"spring.datasource.url=jdbc:h2:mem:token-state-outbox-publisher;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
@EmbeddedKafka(partitions = 3, topics = RefreshTokenEventPublisher.LIFECYCLE_TOPIC)
class OutboxPublisherTest {
	// @BeforeEach: KafkaTestUtils.consumerProps("lifecycle-test", "true", broker) 로 소비자를 만들고
	//              broker.consumeFromAnEmbeddedTopic(consumer, true, LIFECYCLE_TOPIC) — seekToEnd=true(session 의 같은 테스트와 같은 이유)

	@Test
	@DisplayName("미발행 행을 계열을 키로 보내고 발행 표시를 남긴다")
	void publishesPendingRowsKeyedByFamilyAndMarksThem() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

		publisher.publishPending();

		ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
				consumer, RefreshTokenEventPublisher.LIFECYCLE_TOPIC, Duration.ofSeconds(10));
		OutboxEntity row = OutboxEvents.rowsOf(outboxRepository, issued.familyId()).get(0);
		assertThat(record.key()).isEqualTo(issued.familyId());
		assertThat(record.value()).isEqualTo(row.getPayload());
		assertThat(row.getPublishedAt()).isNotNull();
	}

	@Test
	@DisplayName("이미 보낸 행은 다시 보내지 않는다")
	void doesNotResendPublishedRows() {
		service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-B");
		publisher.publishPending();
		KafkaTestUtils.getSingleRecord(consumer, RefreshTokenEventPublisher.LIFECYCLE_TOPIC, Duration.ofSeconds(10));

		publisher.publishPending();

		assertThat(consumer.poll(Duration.ofSeconds(2)).count()).isZero();
	}
}
```

- [ ] **Step 6: 구현 — 폴러와 스케줄링**

- `outbox/OutboxPublisher`: `session/src/main/java/dev/starryeye/session/outbox/OutboxPublisher.java` 를 복제한다(`@Scheduled(initialDelayString = "${my.outbox-poll-interval-ms}", fixedDelayString = "${my.outbox-poll-interval-ms}") public void publishPending()`, 실패한 행에서 멈춤, 표시는 `TransactionTemplate`). 주석은 규칙만 옮기고, 원본의 경험담 괄호 두 곳("실제로 처음엔 … 겪었다", "실제로 한 번 관찰됐다")은 현재형 규칙으로 바꾼다. 첫 `주의.`(보안 창)는 이 발행자의 의미로 바꾼다 — 이 주기만큼 감사 기록이 늦고, 폐기 자체는 늦지 않는다(폐기는 이미 커밋됐다).
- `TokenStateApplication`: `@EnableScheduling` 과, `session` 의 `SessionApplication` 처럼 "테스트 컨텍스트도 폴러를 물려받는다 — 그래서 테스트 yml 이 주기를 길게 잡는다"는 `주의.`.
- main `application.yml` `my:` 아래 `outbox-poll-interval-ms: 500`(작성자 판단: `session` 과 같은 값 — 스펙 8절. 이 주기는 감사 기록의 지연일 뿐 보안 창이 아니라 더 줄일 이유가 없다). test `application.yml` `my:` 아래 `outbox-poll-interval-ms: 3600000` 과 주석 두 줄: (1) 이 모듈의 h2 는 이름 있는 인메모리 DB 라 다른 컨텍스트가 만든 미발행 행도 같은 테이블에 있다, (2) 기본 yml 의 bootstrap 이 `localhost:9092` 라 주기를 짧게 두면 스택을 띄워 둔 채 테스트를 돌릴 때 테스트의 가짜 사건이 실제 브로커로 나간다. 발행을 보는 테스트는 `publishPending()` 을 직접 부른다.

- [ ] **Step 7: 전체 테스트**

Run: `token-state` 에서 빌드 명령(Global Constraints).
Expected: BUILD SUCCESSFUL. 기존 52 passed / 1 skipped(`RefreshTokenServiceMySqlLockSemanticsTest`, MySQL 없음) + 새 테스트 5개.

- [ ] **Step 8: 변이 확인** — (a) `issue` 의 `eventPublisher.issued(...)` 를 지우면 `issueRecordsIssuedEvent` 가, (b) `RefreshTokenLifecycleEvent` 의 `@JsonInclude` 를 지우면 `issueRecordsIssuedEvent`(`has("reason")`)·`issuedEventOmitsAbsentSid` 가, (c) `OutboxPublisher` 의 표시 블록을 지우면 `doesNotResendPublishedRows` 가 실패하는지 본다. 각각 `git diff`·실패 단언을 보고서에 남기고 복구한다.

- [ ] **Step 9: 커밋**

```bash
cd /Users/starryeye/study/spring-security
M=oauth-2/authorization-server/practice/microservice/token-state
git add $M/src/main/java/dev/starryeye/token_state/jpa/OutboxEntity.java \
        $M/src/main/java/dev/starryeye/token_state/jpa/OutboxEntityRepository.java \
        $M/src/main/java/dev/starryeye/token_state/outbox/OutboxPublisher.java \
        $M/src/main/java/dev/starryeye/token_state/event/RefreshTokenLifecycleEvent.java \
        $M/src/main/java/dev/starryeye/token_state/event/RefreshTokenEventPublisher.java \
        $M/src/main/java/dev/starryeye/token_state/event/KafkaTopicConfig.java \
        $M/src/main/java/dev/starryeye/token_state/TokenStateApplication.java \
        $M/src/main/java/dev/starryeye/token_state/RefreshTokenService.java \
        $M/src/main/resources/application.yml $M/src/test/resources/application.yml \
        $M/src/test/java/dev/starryeye/token_state/OutboxEvents.java \
        $M/src/test/java/dev/starryeye/token_state/RefreshTokenServiceEventTest.java \
        $M/src/test/java/dev/starryeye/token_state/RefreshTokenEventAtomicityTest.java \
        $M/src/test/java/dev/starryeye/token_state/outbox/OutboxPublisherTest.java
git commit -m "token-state: publish refresh token issuance through an outbox" -m "<본문: 왜 outbox 인가, 키가 계열인 이유, 테스트가 실제 브로커로 보내지 않는 이유>" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push origin main
```

---

## Task 2: 회전·폐기 사건과 잠금 뒤 시각

**Files:**
- Modify: `token-state/src/main/java/dev/starryeye/token_state/RefreshTokenService.java`(`rotate`, `revoke`, `revokeFamily` → `revokeMembers`), `jpa/RefreshTokenEntity.java`(`revoke` 반환값)
- Create (test): `token-state/src/test/java/dev/starryeye/token_state/RefreshTokenFixtures.java`, `RefreshTokenServiceSilentFailureTest.java`, `RefreshTokenEventOrderTest.java`
- Modify (test): `RefreshTokenServiceEventTest.java`, `RefreshTokenEventAtomicityTest.java`, `RefreshTokenServiceRotateTest.java`(만료 헬퍼를 `RefreshTokenFixtures` 로 옮긴다), `jpa/RefreshTokenEntityTest.java`

**Interfaces:**
- Consumes: Task 1 의 `RefreshTokenEventPublisher`, `OutboxEvents`
- Produces:
  - `public boolean RefreshTokenEntity.revoke(Instant at, String reason)` — 상태를 바꿨으면 `true`, 이미 `REVOKED` 라 그대로 두면 `false`
  - `private int RefreshTokenService.revokeMembers(List<RefreshTokenEntity> members, Instant at, String reason)` — `members` 를 폐기하고, 바뀐 행이 하나 이상이면 `REVOKED` 사건을 쓰고, 바뀐 행 수를 돌려준다. `revokedTokens` = 바뀐 행 수, `activeTokensRevoked` = 그중 바꾸기 직전 상태가 `ACTIVE` 였던 수. `members` 가 비었거나 바뀐 행이 0이면 사건이 없다. 세 폐기 경로(재사용·RFC 7009·로그아웃)가 모두 이것만 쓴다 — "바뀐 행이 0이면 사건도 없다"가 한 곳에 있게(Task 3 이 로그아웃에서 쓴다)
  - 테스트 도우미 `RefreshTokenFixtures.expireIndividualToken(RefreshTokenEntityRepository repository, RefreshTokenEntity entity)` — `RefreshTokenServiceRotateTest` 의 같은 이름 private 헬퍼를 옮긴 것(같은 해시로 `expires_at` 만 과거인 행으로 바꿔 저장하고 그 행을 돌려준다)

- [ ] **Step 1: 실패하는 테스트**

`RefreshTokenEntityTest` 에 추가(기존 `newEntity()` 픽스처 사용):

```java
@Test
void revokeReportsWhetherItChangedTheRow() {
	RefreshTokenEntity entity = newEntity();
	Instant at = Instant.parse("2026-07-25T00:01:00Z");

	assertThat(entity.revoke(at, "CLIENT_REVOKED")).isTrue();
	assertThat(entity.revoke(at.plusSeconds(1), "SESSION_LOGGED_OUT")).isFalse();
	assertThat(entity.getRevokedReason()).isEqualTo("CLIENT_REVOKED");
}
```

`RefreshTokenServiceEventTest` 에 추가:

```java
@Test
@DisplayName("회전하면 새 토큰의 만료를 실은 ROTATED 를 쓴다")
void rotateRecordsRotatedEvent() throws Exception {
	IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

	RotateResult rotated = service.rotate(issued.refreshToken(), "my-client", null);

	List<JsonNode> events = OutboxEvents.eventsOf(outboxRepository, objectMapper, issued.familyId());
	assertThat(events).extracting(e -> e.get("kind").asText()).containsExactly("ISSUED", "ROTATED");
	JsonNode event = events.get(1);
	assertThat(Instant.parse(event.get("expiresAt").asText()).getEpochSecond()).isEqualTo(rotated.expiresAt());
	assertThat(event.get("sub").asText()).isEqualTo("user-sub-0001");
	assertThat(event.get("clientId").asText()).isEqualTo("my-client");
	assertThat(event.get("sid").asText()).isEqualTo("SID-A");
	assertThat(event.has("familyExpiresAt")).isFalse();
	assertThat(event.has("reason")).isFalse();
}

@Test
@DisplayName("재사용이 탐지되면 실제로 바뀐 행 수를 실은 REVOKED(REUSE_DETECTED) 하나를 쓴다")
void reuseRecordsOneRevokedEventWithCounts() throws Exception {
	IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
	RotateResult first = service.rotate(issued.refreshToken(), "my-client", null);
	service.rotate(first.refreshToken(), "my-client", null);                 // CONSUMED 2, ACTIVE 1

	RotateResult reuse = service.rotate(issued.refreshToken(), "my-client", null);

	assertThat(reuse.status()).isEqualTo(RotateStatus.REUSE_DETECTED);
	List<JsonNode> events = OutboxEvents.eventsOf(outboxRepository, objectMapper, issued.familyId());
	assertThat(events).extracting(e -> e.get("kind").asText()).containsExactly("ISSUED", "ROTATED", "ROTATED", "REVOKED");
	JsonNode revoked = events.get(3);
	assertThat(revoked.get("reason").asText()).isEqualTo("REUSE_DETECTED");
	assertThat(revoked.get("revokedTokens").asInt()).isEqualTo(3);
	assertThat(revoked.get("activeTokensRevoked").asInt()).isEqualTo(1);
	assertThat(repository.findByFamilyId(issued.familyId()))
			.allSatisfy(row -> assertThat(row.getRevokedReason()).isEqualTo("REUSE_DETECTED"))
			.hasSize(3);
}

@Test
@DisplayName("client 가 폐기하면 REVOKED(CLIENT_REVOKED) 를 쓴다")
void clientRevokeRecordsRevokedEvent() throws Exception {
	IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
	RotateResult rotated = service.rotate(issued.refreshToken(), "my-client", null);

	assertThat(service.revoke(rotated.refreshToken(), "my-client")).isTrue();

	JsonNode revoked = OutboxEvents.eventsOf(outboxRepository, objectMapper, issued.familyId()).get(2);
	assertThat(revoked.get("reason").asText()).isEqualTo("CLIENT_REVOKED");
	assertThat(revoked.get("revokedTokens").asInt()).isEqualTo(2);
	assertThat(revoked.get("activeTokensRevoked").asInt()).isEqualTo(1);
}
```

`RefreshTokenEventAtomicityTest` 에 추가(`TokenGenerator` 주입):

```java
@Test
@DisplayName("REVOKED 를 쓰지 못하면 재사용 탐지의 계열 폐기도 롤백된다")
void reuseRevocationRollsBackWhenTheEventCannotBeRecorded() {
	IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1000L, "SID-A");
	RotateResult rotated = service.rotate(issued.refreshToken(), "my-client", null);
	doThrow(new IllegalStateException("outbox unavailable"))
			.when(eventPublisher).revoked(any(), any(), anyString(), anyInt(), anyInt());

	assertThatThrownBy(() -> service.rotate(issued.refreshToken(), "my-client", null))
			.isInstanceOf(IllegalStateException.class);

	assertThat(statusOf(issued.refreshToken())).isEqualTo(RefreshTokenStatus.CONSUMED);
	assertThat(statusOf(rotated.refreshToken())).isEqualTo(RefreshTokenStatus.ACTIVE);
}

@Test
@DisplayName("ROTATED 를 쓰지 못하면 회전도 롤백된다")
void rotationRollsBackWhenTheEventCannotBeRecorded() {
	IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1000L, "SID-A");
	doThrow(new IllegalStateException("outbox unavailable")).when(eventPublisher).rotated(any(), any());

	assertThatThrownBy(() -> service.rotate(issued.refreshToken(), "my-client", null))
			.isInstanceOf(IllegalStateException.class);

	assertThat(statusOf(issued.refreshToken())).isEqualTo(RefreshTokenStatus.ACTIVE);
	assertThat(repository.findByFamilyId(issued.familyId())).hasSize(1);
}
// statusOf(token) = repository.findByTokenHash(tokenGenerator.hash(token)).orElseThrow().getStatus()
```

`RefreshTokenServiceSilentFailureTest`(`@SpringBootTest`) — 각 테스트는 "시도 직전의 그 계열 outbox 행 수"를 재고, 시도 뒤에 같은지 본다. 계열이 없는 경로는 전체 `outboxRepository.count()` 로 본다.

| 테스트 | 준비 | 시도 | 기대 상태 |
|---|---|---|---|
| `notFoundRecordsNothing` | — | `rotate("no-such-token", "my-client", null)` | `NOT_FOUND`, 전체 행 수 그대로 |
| `clientMismatchRecordsNothing` | `issue(my-client)` | `rotate(t0, "other-client", null)` | `CLIENT_MISMATCH` |
| `scopeExceededRecordsNothing` | `issue(my-client, "openid")` | `rotate(t0, "my-client", "openid email")` | `SCOPE_EXCEEDED` |
| `expiredRecordsNothing` | `issue` 후 `RefreshTokenFixtures.expireIndividualToken` | `rotate(t0, "my-client", null)` | `EXPIRED` |
| `retryWithRevokedTokenRecordsNothing` | `issue`, `t1 = rotate(t0)`, `rotate(t0)`(재사용으로 계열 폐기) | `rotate(t1, "my-client", null)` | `REVOKED` |
| `revokeOfUnknownTokenRecordsNothing` | — | `revoke("no-such-token", "my-client")` | `false`, 전체 행 수 그대로 |
| `revokeByAnotherClientRecordsNothing` | `issue(my-client)` | `revoke(t0, "other-client")` | `false` |
| `revokeOfFullyRevokedFamilyRecordsNothing` | `issue`, `revoke(t0, "my-client")` | `revoke(t0, "my-client")` | `true`(RFC 7009 응답은 그대로), 행 수 그대로 |

`RefreshTokenEventOrderTest`(`@SpringBootTest`, `@MockitoSpyBean TokenGenerator tokenGenerator`):

```java
/**
 * 시각은 계열 잠금을 얻은 뒤에 잰다(스펙 3절). 잠금을 기다린 재사용이 그 사이 커밋된 회전보다 이른 시각으로
 *      기록되면, 감사 이력에서 "끊김"이 "회전"보다 먼저 일어난 것처럼 보인다.
 *
 * 주의. 잠금 대기를 그대로 재현하지 않는다. 늦게 처리될 쪽("late" 스레드)을 rotate 입구(첫 hash 호출)에서 세워
 *      두고, 그동안 다른 스레드가 회전을 끝까지 커밋한 뒤 풀어 준다. 시각을 입구에서 재는 구현이면 late 의 시각이
 *      회전보다 이르고, 잠금 뒤에 재는 구현이면 늦다 — 잠금 의미론과 무관하게 결정적이라 h2 로도 성립한다.
 *      InnoDB 에서 실제 잠금 대기로 같은 순서를 보이는 것은 RefreshTokenServiceMySqlLockSemanticsTest 다(Task 3).
 */
@Test
@DisplayName("회전 뒤에 처리된 재사용은 회전보다 늦은 시각으로 기록된다")
void reuseProcessedAfterARotationIsRecordedAfterIt() throws Exception {
	IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1000L, "SID-A");

	CountDownLatch lateEntered = new CountDownLatch(1);
	CountDownLatch rotationCommitted = new CountDownLatch(1);
	AtomicBoolean lateFirstHash = new AtomicBoolean(true);
	doAnswer(invocation -> {
		if ("late".equals(Thread.currentThread().getName()) && lateFirstHash.getAndSet(false)) {
			lateEntered.countDown();
			rotationCommitted.await(10, TimeUnit.SECONDS);
		}
		return invocation.callRealMethod();
	}).when(tokenGenerator).hash(anyString());

	ExecutorService late = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "late"));
	try {
		Future<RotateResult> reuse = late.submit(() -> service.rotate(issued.refreshToken(), "my-client", null));
		assertThat(lateEntered.await(10, TimeUnit.SECONDS)).isTrue();

		RotateResult rotated = service.rotate(issued.refreshToken(), "my-client", null);
		rotationCommitted.countDown();

		assertThat(rotated.status()).isEqualTo(RotateStatus.ROTATED);
		assertThat(reuse.get(30, TimeUnit.SECONDS).status()).isEqualTo(RotateStatus.REUSE_DETECTED);
	} finally {
		late.shutdownNow();
	}

	List<JsonNode> events = OutboxEvents.eventsOf(outboxRepository, objectMapper, issued.familyId());
	assertThat(events).extracting(e -> e.get("kind").asText()).containsExactly("ISSUED", "ROTATED", "REVOKED");
	assertThat(Instant.parse(events.get(2).get("occurredAt").asText()))
			.isAfterOrEqualTo(Instant.parse(events.get(1).get("occurredAt").asText()));
}
```

- [ ] **Step 2: 실패 확인** — `revoke` 반환값·사건 테스트가 실패한다. `reuseProcessedAfterARotationIsRecordedAfterIt` 는 지금 구현(`now` 가 `rotate` 첫 줄)에서 `ROTATED` 사건이 없어 실패한다 — 시각 단언이 실패하는 것은 Step 8 변이로 따로 본다.

- [ ] **Step 3: 구현**

- `RefreshTokenEntity.revoke(Instant, String)` 이 `boolean` 을 돌려준다. 기존 javadoc 에 `@return` 한 줄.
- `RefreshTokenService.rotate`: `Instant now = Instant.now()` 를 `findByFamilyIdForUpdate` 바로 뒤로 옮긴다(해시 계산·스칼라 조회는 그 앞에 그대로). 재사용 분기는 `revokeMembers(family, now, "REUSE_DETECTED")`. 회전 성공은 `repository.save(rotated)` 다음에 `eventPublisher.rotated(rotated, now)`.
- `RefreshTokenService.revoke`: 잠금 뒤 `Instant now = Instant.now()` 를 지역 변수로 두고 `revokeMembers(family, now, "CLIENT_REVOKED")`. 반환값(`true`)은 그대로 — 응답 계약을 바꾸지 않는다.
- `revokeFamily` 를 `revokeMembers` 로 바꾼다(Interfaces). 사건의 `familyId`·`sub`·`clientId`·`sid` 는 `members.get(0)` 에서 읽는다 — 한 계열의 행은 이 값이 같다(`rotate` 가 복사한다).
- `rotate` javadoc 에 `주의.` 두 문단: (1) 시각은 계열 잠금을 얻은 뒤에 잰다 — 잠금을 기다린 요청의 시각이 그 사이 커밋된 요청보다 이르면 감사 이력의 순서가 뒤집힌다. 만료 판정·`consumedAt`·새 토큰 `issuedAt` 도 이 시각을 쓴다. (2) 상태를 바꾼 경로만 사건을 쓴다 — 실패 경로(`NOT_FOUND`…`SCOPE_EXCEEDED`)는 outbox 행도 만들지 않는다(`SCOPE_EXCEEDED` 는 "아무 상태도 바꾸지 않는다"는 기존 계약에 outbox 도 포함된다).
- 테스트: `RefreshTokenServiceRotateTest` 의 `expireIndividualToken` 을 `RefreshTokenFixtures` 로 옮기고 그 테스트도 도우미를 부르게 한다(`expireFamily` 는 그 클래스에 남겨도 된다).

- [ ] **Step 4: 통과 확인** — 모듈 전체 테스트.

- [ ] **Step 5: 변이 확인** — (a) `rotate` 의 `now` 를 첫 줄로 되돌리면 `reuseProcessedAfterARotationIsRecordedAfterIt` 의 시각 단언이 실패하는지(다른 단언은 통과해야 인과가 맞다), (b) `revokeMembers` 의 "바뀐 행 0이면 사건 없음" 가드를 지우면 `revokeOfFullyRevokedFamilyRecordsNothing` 이, (c) `activeTokensRevoked` 를 바뀐 행 수로 잘못 세면 `reuseRecordsOneRevokedEventWithCounts` 가 실패하는지 본다.

- [ ] **Step 6: 커밋** — 위 Files 의 경로를 명시해 `git add`, 제목 예: `token-state: publish rotation and revocation, timed after the family lock`, 본문에 잠금 뒤 시각의 이유(경합에서 순서가 뒤집히는 장면)와 "바뀐 행 0이면 사건 없음". `git push origin main`.

---

## Task 3: 로그아웃 폐기를 계열 잠금으로, InnoDB 경합 검증

**Files:**
- Modify: `token-state/src/main/java/dev/starryeye/token_state/RefreshTokenService.java`(`revokeBySid` + javadoc), `jpa/RefreshTokenEntityRepository.java`, `event/SessionLoggedOutConsumer.java`(주석만)
- Modify (test): `RefreshTokenServiceRevokeBySidTest.java`, `event/SessionLoggedOutConsumerTest.java`, `RefreshTokenServiceMySqlLockSemanticsTest.java`

**Interfaces:**
- Consumes: Task 2 의 `revokeMembers`, `RefreshTokenFixtures.expireIndividualToken`, Task 1 의 `OutboxEvents`
- Produces:
  - `List<String> RefreshTokenEntityRepository.findFamilyIdsBySidAndStatus(String sid, RefreshTokenStatus status)` — `select distinct r.familyId from RefreshTokenEntity r where r.sid = :sid and r.status = :status order by r.familyId`(스칼라, 잠금 없음)
  - `revokeActiveBySid(...)` 는 지운다(호출자가 없어진다)
  - `int RefreshTokenService.revokeBySid(String sid)` — 시그니처·반환값(바뀐 행의 합) 그대로

**잠금 방식(작성자 판단).** 스펙 4절은 "sid 의 ACTIVE 행을 잠금 조회로 읽는다"를 제안하고 잠금 방식은 빌더에게 맡겼다(8절). 이 계획은 `sid` 로 직접 잠그지 않고 **회전과 같은 경로 하나**를 쓴다 — 계열 id 를 스칼라로 찾고(엔티티를 올리지 않는다), 계열마다 `findByFamilyIdForUpdate` 로 잠근다. 이유: (1) `refresh_tokens` 에 `sid` 인덱스가 없어 `sid` 로 잠그는 조회는 전체 스캔이 되고, InnoDB REPEATABLE READ 에서 스캔한 모든 행을 잠근다 — 로그아웃 하나가 모든 사용자의 회전을 멈춘다(MySQL 문서의 locking read 규칙에서 추론, 실측 아님). (2) 저장소의 `findByFamilyIdForUpdate` 주석이 "모든 호출부가 이 메서드 하나로만 계열을 잠그므로 잠금 순서가 항상 같다"를 교착 부재의 근거로 든다 — 같은 경로를 쓰면 그 논증이 그대로 선다. 계열은 `familyId` 순으로 잠근다(로그아웃끼리도 순서가 같게).

```java
@Transactional
public int revokeBySid(String sid) {
	int revoked = 0;
	for (String familyId : repository.findFamilyIdsBySidAndStatus(sid, RefreshTokenStatus.ACTIVE)) {
		List<RefreshTokenEntity> family = repository.findByFamilyIdForUpdate(familyId);
		Instant now = Instant.now();
		List<RefreshTokenEntity> active = family.stream()
				.filter(member -> member.getStatus() == RefreshTokenStatus.ACTIVE && sid.equals(member.getSid()))
				.toList();
		revoked += revokeMembers(active, now, "SESSION_LOGGED_OUT");
	}
	return revoked;
}
```

- [ ] **Step 1: 실패하는 테스트 (h2)**

`RefreshTokenServiceRevokeBySidTest` 에 추가(`OutboxEntityRepository`·`ObjectMapper`·`TokenGenerator` 주입. 기존 네 테스트는 그대로 통과해야 한다):

```java
@Test
@DisplayName("로그아웃은 실제로 바뀐 계열마다 REVOKED(SESSION_LOGGED_OUT) 하나를 쓴다 — 한 세션의 두 client")
void logoutRecordsOneRevokedEventPerFamily() throws Exception {
	IssueResult first = service.issue("client-one", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
	IssueResult second = service.issue("client-two", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
	IssueResult other = service.issue("client-one", "user-sub-0001", "openid offline_access", 1000L, "SID-B");

	assertThat(service.revokeBySid("SID-A")).isEqualTo(2);

	assertRevoked(first.familyId(), "client-one");
	assertRevoked(second.familyId(), "client-two");
	assertThat(OutboxEvents.kindsOf(outboxRepository, objectMapper, other.familyId())).containsExactly("ISSUED");
}
// assertRevoked(familyId, clientId): 사건이 [ISSUED, REVOKED] 이고 REVOKED 의 reason=SESSION_LOGGED_OUT,
//      clientId=clientId, sid=SID-A, revokedTokens=1, activeTokensRevoked=1

@Test
@DisplayName("같은 로그아웃을 다시 처리하면 아무것도 바뀌지 않고 사건도 없다")
void redeliveredLogoutRecordsNothing() throws Exception {
	IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

	assertThat(service.revokeBySid("SID-A")).isEqualTo(1);
	assertThat(service.revokeBySid("SID-A")).isZero();

	assertThat(OutboxEvents.kindsOf(outboxRepository, objectMapper, issued.familyId())).containsExactly("ISSUED", "REVOKED");
}

@Test
@DisplayName("로그아웃은 CONSUMED 행을 남기므로 그 뒤의 옛 토큰 재사용도 탐지되고 따로 기록된다")
void logoutLeavesConsumedRowsSoALaterReplayIsStillDetected() throws Exception {
	IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
	service.rotate(issued.refreshToken(), "my-client", null);
	service.revokeBySid("SID-A");

	assertThat(service.rotate(issued.refreshToken(), "my-client", null).status()).isEqualTo(RotateStatus.REUSE_DETECTED);

	List<JsonNode> events = OutboxEvents.eventsOf(outboxRepository, objectMapper, issued.familyId());
	assertThat(events).extracting(e -> e.get("kind").asText()).containsExactly("ISSUED", "ROTATED", "REVOKED", "REVOKED");
	assertThat(events.get(2).get("reason").asText()).isEqualTo("SESSION_LOGGED_OUT");
	assertThat(events.get(2).get("activeTokensRevoked").asInt()).isEqualTo(1);
	assertThat(events.get(3).get("reason").asText()).isEqualTo("REUSE_DETECTED");
	assertThat(events.get(3).get("revokedTokens").asInt()).isEqualTo(1);
	assertThat(events.get(3).get("activeTokensRevoked").asInt()).isZero();
}

@Test
@DisplayName("일부가 끊긴 계열을 client 가 폐기하면 바뀐 CONSUMED 행만 센다(Review Focus 4)")
void revokeOfPartlyRevokedFamilyCountsOnlyChangedRows() throws Exception {
	IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
	service.rotate(issued.refreshToken(), "my-client", null);
	service.revokeBySid("SID-A");

	assertThat(service.revoke(issued.refreshToken(), "my-client")).isTrue();

	JsonNode last = OutboxEvents.eventsOf(outboxRepository, objectMapper, issued.familyId()).get(3);
	assertThat(last.get("reason").asText()).isEqualTo("CLIENT_REVOKED");
	assertThat(last.get("revokedTokens").asInt()).isEqualTo(1);
	assertThat(last.get("activeTokensRevoked").asInt()).isZero();
}

@Test
@DisplayName("만료됐지만 상태가 ACTIVE 인 행도 activeTokensRevoked 에 들어간다 — 수는 상태 기준이다")
void expiredButActiveRowCountsAsActive() throws Exception {
	IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
	RefreshTokenFixtures.expireIndividualToken(repository,
			repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken())).orElseThrow());

	assertThat(service.revokeBySid("SID-A")).isEqualTo(1);

	assertThat(OutboxEvents.eventsOf(outboxRepository, objectMapper, issued.familyId()).get(1)
			.get("activeTokensRevoked").asInt()).isEqualTo(1);
}
```

`SessionLoggedOutConsumerTest` 에 추가(`OutboxEntityRepository`·`ObjectMapper` 주입):

```java
@Test
@DisplayName("같은 로그아웃 이벤트가 두 번 와도 REVOKED 사건은 하나다")
void redeliveredLogoutEventAddsNoEvent() throws Exception {
	IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-R");
	IssueResult marker = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-M");
	String payload = objectMapper.writeValueAsString(new SessionLoggedOutEvent(
			UUID.randomUUID().toString(), "SID-R", "user-sub-0001", Instant.now()));

	kafkaTemplate.send(SessionLoggedOutConsumer.LOGGED_OUT_TOPIC, "SID-R", payload).get(5, TimeUnit.SECONDS);
	kafkaTemplate.send(SessionLoggedOutConsumer.LOGGED_OUT_TOPIC, "SID-R", payload).get(5, TimeUnit.SECONDS);
	// 같은 키 → 같은 파티션에서 앞의 둘 뒤에 처리된다. 표지가 끊기면 앞의 둘도 끝났다.
	String markerPayload = objectMapper.writeValueAsString(new SessionLoggedOutEvent(
			UUID.randomUUID().toString(), "SID-M", "user-sub-0001", Instant.now()));
	kafkaTemplate.send(SessionLoggedOutConsumer.LOGGED_OUT_TOPIC, "SID-R", markerPayload).get(5, TimeUnit.SECONDS);

	await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
			assertThat(OutboxEvents.kindsOf(outboxRepository, objectMapper, marker.familyId())).contains("REVOKED"));
	assertThat(OutboxEvents.kindsOf(outboxRepository, objectMapper, issued.familyId())).containsExactly("ISSUED", "REVOKED");
}
```

- [ ] **Step 2: 실패 확인 → Step 3: 구현** — 위 `revokeBySid`, 저장소 메서드 추가·삭제. 주석을 사실에 맞춘다:
  - `RefreshTokenService.revokeBySid` javadoc: 폐기 범위(sid 단위) 문단은 유지. "계열 단위로 잠그지 않는다"·"놓친 행은 정리되지 않는다" 두 문단을 지우고 다음으로 바꾼다 — (1) 잠금 경로가 회전과 같다(스칼라 → 계열마다 `findByFamilyIdForUpdate`, `familyId` 순), 첫 조회가 스칼라여야 하는 이유는 `rotate` 와 같다. (2) 시각은 계열마다 잠금 뒤에 잰다. (3) 사건은 실제로 바뀐 행만 말한다 — 재전달은 바뀐 행이 없어 사건도 없다. (4) 남는 빈틈: 첫 조회 뒤에 같은 `sid` 로 **새 계열**이 생기면(로그아웃 직전에 받은 code 의 교환) 이 호출은 그 계열을 모른다 — 코드 구조에서 나오는 결론이고 테스트로 재현하지 않았다. (5) `activeTokensRevoked` 는 상태 기준이다 — 만료됐지만 `ACTIVE` 인 행도 센다. InnoDB 에서 회전과의 두 순서가 실제로 직렬화되는지는 Step 5 의 테스트가 보인다 — 그 결과에 맞춰 문장을 쓴다(못 보였으면 못 보였다고).
  - `RefreshTokenEntityRepository`: `findByFamilyIdForUpdate` javadoc 의 호출부 목록에 로그아웃 폐기(`revokeBySid`)를 더한다. 새 스칼라 조회의 javadoc 에 기존 `revokeActiveBySid` 의 "`sid` 가 null 인 행은 `sid = ?` 에 걸리지 않는다 — 빈틈" 문단을 옮긴다.
  - `SessionLoggedOutConsumer` javadoc 셋째 `주의.`(계열 잠금 없음·놓친 행) 를 새 구현에 맞게 고친다.

- [ ] **Step 4: h2 통과 확인** — 모듈 전체.

- [ ] **Step 5: InnoDB 경합 테스트**

MySQL 을 띄운다: `docker compose -p microservice-as -f docker-compose/docker-compose.yml up -d mysql` (`docker ps` 로 확인). `RefreshTokenServiceMySqlLockSemanticsTest` 에 `@MockitoSpyBean RefreshTokenEventPublisher eventPublisher`, `OutboxEntityRepository`, `ObjectMapper`, `JdbcTemplate` 를 더하고 `clean()` 에서 outbox 도 지운다. 클래스 javadoc 에 세 테스트가 보이는 성질을 한 문단 더한다. 대기 확인 도우미:

```java
// 다른 트랜잭션이 계열 잠금을 기다리고 있는지 MySQL 에 직접 묻는다 — 시간에 맡기지 않는다.
private void awaitLockWait() {
	await().atMost(10, TimeUnit.SECONDS).until(() -> jdbcTemplate.queryForObject(
			"select count(*) from performance_schema.data_locks"
					+ " where OBJECT_SCHEMA = 'token_state_test' and LOCK_STATUS = 'WAITING'",
			Integer.class) > 0);
}
```

(실측 필요: root 로 `performance_schema.data_locks` 가 읽히고, 대기 중에 1 이상이 나오는지. 첫 실행에서 값을 찍어 보고서에 남긴다. 안 되면 `information_schema.innodb_trx` 의 `trx_state = 'LOCK WAIT'` 로 바꾸고 그 사실을 적는다.)

세 테스트. 모두 `service.issue("my-client", "user-sub-0001", "openid offline_access", 1700000000L, "SID-A")` 로 시작하고, 스텁은 발급 **뒤에** 건다. 스레드는 이름 붙인 단일 스레드 executor 로 만든다. 교착이 나면 `Future.get` 이 예외(`CannotAcquireLockException` 류)로 드러난다 — 두 future 가 모두 값을 돌려주는 것 자체가 "교착 없음"의 단언이다.

1. `reuseWaitingForTheFamilyLockIsRecordedAfterTheRotation` (성공 기준 2b, InnoDB)
   - "late" 스레드의 첫 `hash` 에서 `lateEntered` 를 내리고 `firstHoldsLock` 를 기다린다. "first" 스레드는 `generate()` 안에서 `firstHoldsLock` 를 내리고 `releaseFirst` 를 기다린다(`doAnswer(...).when(tokenGenerator).generate()`).
   - 순서: late 제출 → `lateEntered` 대기 → first 제출 → `firstHoldsLock` 대기(이제 late 가 잠금으로 간다) → `awaitLockWait()` → `releaseFirst` 내림.
   - 단언: first `ROTATED`, late `REUSE_DETECTED`. 사건 `[ISSUED, ROTATED, REVOKED]`, `REVOKED.occurredAt` ≥ `ROTATED.occurredAt`, `revokedTokens = 2`, `activeTokensRevoked = 1`. 계열 행 2개 모두 `REVOKED`.
   - 이 순서에서는 late 가 회전보다 **먼저** `rotate` 에 들어오므로, 시각을 입구에서 재는 구현이면 단언이 실패한다(Step 6 변이).
2. `logoutWaitingBehindARotationRevokesTheRotatedToken` (회전이 먼저 잠금)
   - first(rotate)를 `generate()` 에서 세우고, `firstHoldsLock` 뒤 "logout" 스레드로 `revokeBySid("SID-A")` 제출 → `awaitLockWait()` → `releaseFirst`.
   - 단언: rotate `ROTATED`, `revokeBySid` 가 `1`. 원래 토큰 `CONSUMED`, 새 토큰 `REVOKED`·`SESSION_LOGGED_OUT`. 사건 `[ISSUED, ROTATED, REVOKED]`, REVOKED 는 `SESSION_LOGGED_OUT`, `1`/`1`, `occurredAt` ≥ ROTATED.
   - 이것이 슬라이스 7 한계("회전이 동시에 새 ACTIVE 형제 행을 만들면 로그아웃이 그 행을 놓칠 수 있다")에 대한 이 슬라이스의 답이다. 잠금을 기다리던 로그아웃이 그 사이 삽입된 형제 행을 보는지가 핵심이다.
3. `rotationWaitingBehindALogoutFindsTheFamilyRevoked` (로그아웃이 먼저 잠금)
   - "logout" 스레드의 `eventPublisher.revoked(...)` 에서(잠금을 쥔 채) `logoutHoldsLock` 를 내리고 `releaseLogout` 을 기다린 뒤 실제 메서드를 부른다(`doAnswer(...).when(eventPublisher).revoked(any(), any(), anyString(), anyInt(), anyInt())`). 그 뒤 first 로 rotate 제출 → `awaitLockWait()` → `releaseLogout`.
   - 단언: `revokeBySid` 가 `1`, rotate 는 `REVOKED`. 계열 행 1개, `REVOKED`·`SESSION_LOGGED_OUT`. 사건 `[ISSUED, REVOKED]` — 회전 사건 없음.

Run: `token-state` 전체 테스트(MySQL 이 떠 있는 상태). Expected: `RefreshTokenServiceMySqlLockSemanticsTest` 4개가 **실행되고** 통과(XML 리포트 `skipped="0"` 을 보고서에 붙인다). 각 테스트를 5회 반복해 흔들림이 없는지 본다(IDE 대신 `--tests` 필터로 반복하고 회차별 결과를 남긴다).

- [ ] **Step 6: 변이 확인** — (a) `rotate` 의 `now` 를 첫 줄로 되돌리면 1번이, (b) `revokeBySid` 를 계열 잠금 없이 `repository.findByFamilyId(familyId)` 로 바꾸면 2번이 어떻게 되는지(실패하면 어느 단언인지, 통과하면 통과한다고) 남긴다. (b) 는 결과가 어느 쪽이든 그대로 기록한다 — 통과한다면 2번 테스트가 잠금의 필요를 증명하지 못한다는 뜻이므로 README 에 그렇게 적는다.

- [ ] **Step 7: 커밋** — Files 의 경로를 명시해 `git add`, 제목 예: `token-state: revoke on logout through the family lock and say what changed`. 본문에 잠금 방식 판단의 두 이유와 InnoDB 테스트 결과(실행 여부·반복 횟수). `git push origin main`. MySQL 은 다음 태스크를 위해 내려도 된다.

---

## Task 4: `audit` 이 refresh 계열 사건을 받아 적는다

**Files:**
- Create: `audit/src/main/java/dev/starryeye/audit/IdempotentInsert.java`, `RefreshTokenEventRecord.java`, `RefreshTokenEventRecorder.java`, `jpa/RefreshTokenEventEntity.java`, `jpa/RefreshTokenEventEntityRepository.java`, `event/JsonFields.java`, `event/RefreshTokenEventConsumer.java`
- Modify: `audit/src/main/java/dev/starryeye/audit/AuditRecorder.java`, `event/AuditEventConsumer.java`, `event/KafkaConsumerConfig.java`
- Test: `audit/src/test/java/dev/starryeye/audit/RefreshTokenEventRecorderTest.java`, `event/JsonFieldsTest.java`, `event/RefreshTokenEventConsumerTest.java`; Modify `event/AuditEventConsumerTest.java`

**Interfaces:**
- Produces:
  - `RefreshTokenEventConsumer.LIFECYCLE_TOPIC = "oidc.refresh-token.lifecycle.v1"`
  - `RefreshTokenEventEntity` — 테이블 `refresh_token_events`, 유니크 `uk_refresh_token_events_event_id`(`event_id`), 인덱스 `idx_refresh_token_events_sub`(`sub`). 컬럼: `id`(IDENTITY), `event_id` varchar(36) not null, `event_type` varchar(255) not null, `kind` varchar(30) not null, `occurred_at`·`recorded_at` not null, `sub` varchar(255) not null, `client_id` varchar(255) not null, `family_id` varchar(36) not null, `sid` varchar(64) null, `reason` varchar(30) null, `revoked_tokens`·`active_tokens_revoked` Integer null, `expires_at`·`family_expires_at` null, `payload` `@Lob @Column(nullable = false, length = 16_777_215)`. `@Builder` 로 전부 받는다(`recordedAt` 포함 — 테스트가 직접 행을 만든다, Task 5)
  - `RefreshTokenEventEntityRepository`: `boolean existsByEventId(String eventId)`, `List<RefreshTokenEventEntity> findBySubOrderByOccurredAtAscIdAsc(String sub)`
  - `record RefreshTokenEventRecord(String eventId, String eventType, String kind, Instant occurredAt, String familyId, String sub, String clientId, String sid, Instant expiresAt, Instant familyExpiresAt, String reason, Integer revokedTokens, Integer activeTokensRevoked, String payload)`
  - `boolean RefreshTokenEventRecorder.record(RefreshTokenEventRecord record)` — 새로 기록하면 `true`, 이미 있으면 `false`
  - `static boolean IdempotentInsert.insertOnce(String eventId, Predicate<String> exists, Runnable save)` — `AuditRecorder.record` 의 알고리즘(선조회 → save → `DataIntegrityViolationException` 이면 재확인, 행이 있을 때만 흡수, 없으면 그대로 던짐). `@Transactional` 없음
  - `JsonFields`(package-private, `event` 패키지): `requiredText`, `optionalText`, `requiredInstant`, `optionalInstant`, `optionalInteger` — 모두 `(JsonNode node, String field)`. 규칙: 키가 없거나 JSON null → `required*` 는 `IllegalArgumentException`, `optional*` 는 `null`. 값이 있으면 텍스트 필드는 문자열이어야 하고 비어 있으면 안 된다, 정수 필드는 `int` 범위의 정수여야 한다, 시각은 `Instant.parse` 가 받아야 한다. 어긋나면 `IllegalArgumentException`(필드 이름 포함) 또는 `DateTimeParseException` — 둘 다 재시도 후 감사 DLT 로 간다

- [ ] **Step 1: 실패하는 테스트**

`JsonFieldsTest`(스프링 없음, `ObjectMapper` 로 노드를 만든다) — 아래 표의 각 줄이 테스트 하나다.

| 테스트 | 입력 | 기대 |
|---|---|---|
| `requiredTextReadsAString` | `{"a":"x"}` | `"x"` |
| `requiredTextRejectsAbsentNullBlankAndNonText` | `{}`, `{"a":null}`, `{"a":""}`, `{"a":"  "}`, `{"a":123}`, `{"a":{}}` | 모두 `IllegalArgumentException` |
| `optionalTextIsNullWhenAbsentOrNull` | `{}`, `{"a":null}` | `null` |
| `optionalTextRejectsBlankAndNonText` | `{"a":""}`, `{"a":true}` | `IllegalArgumentException` |
| `optionalIntegerReadsAnInt` | `{"a":3}` | `3` |
| `optionalIntegerRejectsTextAndFractions` | `{"a":"3"}`, `{"a":3.5}`, `{"a":4294967296}` | `IllegalArgumentException` |
| `requiredInstantParsesIso8601` | `{"a":"2026-10-28T01:02:03.456789Z"}` | 그 `Instant` |
| `requiredInstantRejectsGarbage` | `{"a":"yesterday"}` | `DateTimeParseException` |

`RefreshTokenEventRecorderTest`(`@SpringBootTest`, 전용 datasource `jdbc:h2:mem:audit-refresh-recorder;DB_CLOSE_DELAY=-1;MODE=MySQL`, `spring.kafka.listener.auto-startup=false` — `AuditRecorderTest` 와 같은 이유):
- `recordsFacts` — `REVOKED` 레코드 하나를 넣고 모든 컬럼이 그대로인지, `recordedAt` 이 채워졌는지.
- `sameEventIdIsRecordedOnce` — 같은 레코드 두 번: `true` 다음 `false`, 행 1개.
- `nullSidIsRecorded` — `sid = null` 로 기록된다.

`RefreshTokenEventConsumerTest`(`AuditEventConsumerTest` 와 같은 틀: EmbeddedKafka `partitions = 3`, topics = 새 토픽과 그 `.audit.dlt`, 전용 datasource `jdbc:h2:mem:audit-refresh-consumer…`, DLT 프로브는 `seekToEnd=true`, 순서가 필요한 단언은 같은 키 + 표지). 페이로드 문자열은 Task 1 의 실제 모양과 같게 만든다(`sid` 가 없으면 키가 빠진 모양).

| 테스트 | 보내는 것 | 기대 |
|---|---|---|
| `issuedEventIsRecorded` | ISSUED(모든 공통 필드 + `expiresAt`·`familyExpiresAt`) | 행 1개, `eventType` = 토픽 이름, 각 컬럼 값, `payload` 원문 그대로 |
| `revokedEventIsRecordedWithCounts` | REVOKED(`reason`, `revokedTokens: 3`, `activeTokensRevoked: 1`) | `reason`·두 수 |
| `absentSidIsRecordedAsNull` | `sid` 키 없는 ISSUED | `sid` null |
| `duplicateDeliveryIsRecordedOnce` | 같은 페이로드 두 번 + 표지 | 행 1개, DLT 0건(성공 기준 9) |
| `missingFamilyIdGoesToDlt` | `familyId` 없는 ROTATED | DLT 에 원문, 행 없음 |
| `wrongTypedCountGoesToDlt` | `"revokedTokens":"3"` 인 REVOKED | DLT 에 원문, 행 없음(Review Focus 3) |
| `unknownKindIsRecorded` | `kind: "SUSPENDED"` | 기록된다, `kind` 그대로(Review Focus 5) |

`AuditEventConsumerTest` 에 `blankSidGoesToAuditDlt` 추가 — `"sid":""` 인 로그아웃 이벤트가 DLT 로 가고 행이 없다. 슬라이스 8 이 미룬 결함(`text()` 가 `asText()` 라 빈 값이 not-null 을 통과해 빈 sid 로 기록됨)을 이 태스크가 닫는다.

- [ ] **Step 2: 실패 확인 → Step 3: 구현**
  - `IdempotentInsert`: `AuditRecorder` 의 알고리즘과 그 `주의.` 문단 중 알고리즘에 관한 것(선조회만으로는 멱등이 아니다 / 무조건 흡수하면 안 된다 / `@Transactional` 을 두지 않는다)을 옮긴다. `AuditRecorder` 에는 `audit_events` 고유의 문단(payload 가 다른 같은 eventId)과 `IdempotentInsert` 를 가리키는 한 줄을 남긴다. `AuditRecorderBranchTest` 는 고치지 않고 그대로 통과해야 한다 — 같은 갈래를 `AuditRecorder` 를 통해 계속 고정한다.
  - `RefreshTokenEventRecorder`: `IdempotentInsert.insertOnce(record.eventId(), repository::existsByEventId, () -> repository.save(...))`, `recordedAt = Instant.now()`.
  - `RefreshTokenEventConsumer`: `@KafkaListener(topics = LIFECYCLE_TOPIC, groupId = "${my.consumer-group-id}")`, `onEvent(@Payload String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic)`. 필수: `eventId`, `kind`, `occurredAt`, `familyId`, `sub`, `clientId`. 선택: `sid`, `expiresAt`, `familyExpiresAt`, `reason`, `revokedTokens`, `activeTokensRevoked`. `kind` 값은 검사하지 않는다(모르는 종류도 기록 — 상태 판정이 무시한다, Task 5). 주석: 그룹이 `AuditEventConsumer` 와 같다(같은 `audit` 그룹의 다른 리스너 — 토픽마다 오프셋이 따로다), `sub` 는 이 토픽에선 사실이다(발행자 `token-state` 가 소유자 — 슬라이스 8 의 `reported_sub` 와 다르다), 예외를 잡지 않는다.
  - `AuditEventConsumer`: `text()` 를 지우고 `JsonFields` 를 쓴다 — `eventId`·`sid` 는 `requiredText`, `occurredAt` 은 `requiredInstant`, `sub` 는 `optionalText`. 클래스 주석의 "없는 필드는 null 로 넘긴다 … 기록기에서 not null 위반" 문단을 새 동작(소비자가 먼저 거른다)에 맞게 고친다.
  - `KafkaConsumerConfig`: `@Bean NewTopic refreshTokenLifecycleTopic()`(3, 1)과 `@Bean NewTopic refreshTokenLifecycleAuditDlt()`(`dltFor(LIFECYCLE_TOPIC)`, 3, 1). 클래스 주석의 "지금 구독하는 토픽은 하나지만" 을 두 토픽으로 고치고, 새 토픽은 `token-state` 의 `KafkaTopicConfig` 와 값을 맞춘다고 적는다.

- [ ] **Step 4: 통과 확인** — `audit` 전체(기존 15개 + 새 테스트).

- [ ] **Step 5: 변이 확인** — (a) `JsonFields.optionalInteger` 를 `asInt()` 로 바꾸면 `wrongTypedCountGoesToDlt`·`JsonFieldsTest` 가, (b) `KafkaConsumerConfig` 의 새 DLT 선언을 지우면 `missingFamilyIdGoesToDlt` 가 어떻게 되는지(EmbeddedKafka 가 토픽을 미리 만들어 통과할 수 있다 — 그렇다면 그 사실과, 이 선언을 실제로 증명하는 것은 Task 6 의 `kafka-topics --list` 라는 점을 적는다), (c) `IdempotentInsert` 의 재확인을 지우면 `AuditRecorderBranchTest.absorbsWhenRowAppearedConcurrently` 가 실패하는지 본다.

- [ ] **Step 6: 커밋** — 경로 명시, 제목 예: `audit: record refresh token lifecycle events`. `git push origin main`.

---

## Task 5: 운영자 조회 API 와 Bearer 보호, 운영자 client

**Files:**
- Modify: `audit/build.gradle` — `implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'`, `testImplementation 'org.springframework.security:spring-security-test'`
- Modify: `audit/src/main/resources/application.yml`, `audit/src/test/resources/application.yml` — `my.issuer: http://localhost:9000`, `my.jwk-set-uri: http://localhost:8083/oauth2/jwks`
- Create: `audit/src/main/java/dev/starryeye/audit/config/SecurityConfig.java`, `query/RefreshTokenEventView.java`, `query/RefreshTokenFamilyView.java`, `query/RefreshTokenFamiliesResponse.java`, `query/RefreshTokenFamilyQuery.java`, `query/RefreshTokenAuditController.java`
- Modify: `client-registry/src/main/java/dev/starryeye/client_registry/ClientSeedInitializer.java`, `client-registry/src/test/java/dev/starryeye/client_registry/ClientSeedInitializerTest.java`
- Test: `audit/src/test/java/dev/starryeye/audit/query/RefreshTokenFamilyViewTest.java`, `query/RefreshTokenAuditControllerTest.java`, `security/TestJwks.java`, `security/AuditApiProtectionTest.java`, `security/AuditApiJwksUnavailableTest.java`, `security/AuditApiJwksCacheTest.java`

**Interfaces:**
- Consumes: Task 4 의 `RefreshTokenEventEntity`(+ `@Builder`), `findBySubOrderByOccurredAtAscIdAsc`
- Produces (응답 JSON 은 이 record 의 필드 이름 그대로다):
  - `record RefreshTokenFamiliesResponse(String sub, Instant asOf, List<RefreshTokenFamilyView> families)`
  - `record RefreshTokenFamilyView(String familyId, String clientId, String sid, Instant issuedAt, Instant familyExpiresAt, String reconstructedState, String revokedReason, boolean reuseDetected, List<RefreshTokenEventView> events)` + `static RefreshTokenFamilyView from(List<RefreshTokenEventEntity> eventsInOrder, Instant asOf)`
  - `record RefreshTokenEventView(String eventId, String kind, Instant occurredAt, Instant recordedAt, Instant expiresAt, Instant familyExpiresAt, String reason, Integer revokedTokens, Integer activeTokensRevoked)` + `static RefreshTokenEventView from(RefreshTokenEventEntity)`
  - `RefreshTokenFamiliesResponse RefreshTokenFamilyQuery.familiesOf(String sub, Instant asOf)`
  - `GET /audit/users/{sub}/refresh-token-families` → `RefreshTokenFamiliesResponse`(컨트롤러는 `Instant.now()` 를 `asOf` 로 넘기기만 한다)

**재구성 규칙(스펙 5절을 그대로 코드로).** `from` 은 이미 `occurredAt` → `id` 순으로 정렬된 한 계열의 사건을 받는다.
- `clientId`·`sid` 는 첫 사건의 값. `issuedAt` 은 `ISSUED` 사건의 `occurredAt`, `familyExpiresAt` 은 `ISSUED` 의 `familyExpiresAt` — `ISSUED` 가 없으면 둘 다 null.
- `REVOKED` 사건이 하나라도 있으면 `reconstructedState = "REVOKED"`(순서와 무관), `revokedReason` = 목록에서 첫 `REVOKED` 의 `reason`, `reuseDetected` = `reason == REUSE_DETECTED` 인 `REVOKED` 가 있는가.
- 아니면 마지막 `ISSUED`/`ROTATED` 의 `expiresAt` 이나 `familyExpiresAt` 이 `asOf` 보다 **앞이면**(`isBefore`, `token-state` 의 `isExpired` 와 같은 비교) `"EXPIRED"`, 아니면 `"ACTIVE"`. 값이 없는 쪽은 판정에서 빠진다.
- 그 밖의 `kind` 는 사건 목록에만 들어간다.
- `RefreshTokenFamilyQuery`: 저장소 결과를 `familyId` 로 묶고(첫 등장 순서 유지), 계열끼리는 `issuedAt`(없으면 첫 사건의 `occurredAt`) 내림차순, 같으면 `familyId` 오름차순.

**보안 구성(작성자 판단 + 확인된 라이브러리 동작).**

```java
// SecurityConfig
http.authorizeHttpRequests(authorize -> authorize
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers(HttpMethod.GET, "/audit/**").hasAuthority("SCOPE_audit.read")
				.anyRequest().denyAll())
		.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
		.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
		.csrf(AbstractHttpConfigurer::disable);

@Bean
JwtDecoder jwtDecoder(@Value("${my.jwk-set-uri}") String jwkSetUri, @Value("${my.issuer}") String issuer) {
	NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
			.jwtProcessorCustomizer(processor -> processor.setJWSTypeVerifier(
					new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType("at+jwt"))))
			.build();
	decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
	return decoder;
}
```

이 코드 각 줄의 근거와, 주석에 옮길 때의 출처 표시:
- **typ** (코드 읽기: `spring-security-oauth2-jose-6.4.5` 의 `NimbusJwtDecoder$JwkSetUriJwtDecoderBuilder.processor()`, `nimbus-jose-jwt-9.37.3` 의 `DefaultJWTProcessor`·`DefaultJOSEObjectTypeVerifier`) — Spring 빌더는 type verifier 를 설정하지 않고, Nimbus 의 기본값은 `DefaultJOSEObjectTypeVerifier.JWT` 로 `typ` 이 `JWT` 이거나 없는 토큰만 받는다. 그래서 이 줄이 없으면 **id token(`JWT`)은 typ 검사를 통과하고 이 서버의 access token(`at+jwt`)은 거부된다** — 보안과 기능이 동시에 거꾸로다. customizer 는 빌더가 마지막에 적용한다. 6.4.5 에는 `JwtTypeValidator` 가 없다(스펙 5절). Step 6 변이가 이것을 실측으로 바꾼다. Boot 3.4.5 가 실제로 고르는 Nimbus 버전은 `./gradlew dependencies --configuration runtimeClasspath | grep nimbus` 로 확인해 보고서에 적는다.
- **iss·exp** (코드 읽기: `JwtValidators.createDefaultWithIssuer`, `JwtTimestampValidator`) — 기본 시계 오차 허용이 60초다. 만료 후 60초까지는 받는다. 주석과 README 에 이 값을 적는다.
- **jwks 캐시** (코드 읽기: `.cache(...)` 를 주지 않으면 빌더가 failover 없는 `new RemoteJWKSet(url, retriever)` 를 만든다. Nimbus 9.37.3 `DefaultJWKSetCache` 의 기본값은 수명 15분·갱신 시점 5분이다. `RemoteJWKSet.get` 은 갱신 시점이 지나면 요청마다 다시 받으려 하고, 받기에 실패해도 수명 안의 캐시가 있으면 그 키로 계속하며, 수명이 지나 캐시가 비면 예외를 던진다) — 그래서 signing 이 내려간 동안 캐시로 버티는 창은 **마지막으로 받은 뒤 최대 15분**이고, 5분이 지난 뒤로는 요청마다 실패하는 jwks 호출이 하나씩 나간다(코드 읽기, 실측 아님). 테스트가 보이는 것은 "받은 직후 몇 초 안에 signing 이 내려가도 200"뿐이다. 문서에는 둘을 구분해 적는다.
- **jwks 실패 → 500** (코드 읽기: `NimbusJwtDecoder` 는 `RemoteKeySourceException` 을 `BadJwtException` 이 아닌 `JwtException` 으로 바꾸고, `spring-security-web-6.4.5` 의 `AuthenticationEntryPointFailureHandler.rethrowAuthenticationServiceException` 기본값이 `true` 다) — 그래서 키를 못 받은 요청은 401 이 아니라 예외로 컨테이너까지 올라간다. **예상(실측 필요)**: 컨테이너가 `/error` 로 ERROR 디스패치를 하면 그 요청도 보안 필터를 다시 지나고, `denyAll` 에 걸려 응답이 401 로 바뀐다 — `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` 이 그것을 막는다. Step 6 변이가 이 예상을 확인하거나 반박한다. 결과를 그대로 주석에 적는다.
- `aud` 는 검증하지 않는다 — client_credentials 토큰의 `aud` 는 발급받은 client 자신이고(README 한계 "client_credentials 토큰의 `sub == aud`"), 이 서버에는 자원을 가리키는 `aud` 가 없다. 주석 한 줄로 그 한계를 가리킨다.

- [ ] **Step 1: 실패하는 테스트 — 재구성 규칙**

`RefreshTokenFamilyViewTest`(스프링 없음). 사건은 `RefreshTokenEventEntity.builder()` 로 만든다. `T0 = Instant.parse("2026-10-28T00:00:00Z")`.

| 테스트 | 사건(순서대로) | `asOf` | 기대 |
|---|---|---|---|
| `familyWithRotationsIsActive` | ISSUED(T0, exp T0+14d, famExp T0+30d), ROTATED(T0+1h, exp T0+1h+14d) | T0+2h | `ACTIVE`, `issuedAt` T0, `familyExpiresAt` T0+30d, `revokedReason` null, `reuseDetected` false, 사건 2개 |
| `anyRevokedEventMakesTheFamilyRevokedWhateverItsPosition` | ISSUED(T0), REVOKED(REUSE_DETECTED, T0+1h), ROTATED(T0+1h+1ms) | T0+2h | `REVOKED` |
| `firstRevocationIsTheSummaryAndReuseIsFlagged` | ISSUED, ROTATED, REVOKED(SESSION_LOGGED_OUT), REVOKED(REUSE_DETECTED) | T0+2h | `REVOKED`, `revokedReason` SESSION_LOGGED_OUT, `reuseDetected` true |
| `expiredWhenTheLastTokenExpiryHasPassed` | ISSUED(exp T0+14d), ROTATED(T0+1h, exp E=T0+1h+14d) | E+1s → `EXPIRED`; E → `ACTIVE` | 경계 포함(같으면 만료 아님) |
| `expiredWhenTheFamilyCapHasPassed` | ISSUED(famExp T0+30d), ROTATED(T0+20d, exp T0+30d) | T0+30d+1s | `EXPIRED` |
| `familyWithoutIssuedEventIsStillShown` | ROTATED(T0+1h, exp E) | E-1s → `ACTIVE`; E+1s → `EXPIRED` | `issuedAt`·`familyExpiresAt` null, `clientId`·`sid` 는 ROTATED 의 값(Review Focus 2) |
| `unknownKindDoesNotChangeTheState` | ISSUED(T0), kind `SUSPENDED`(T0+1h) | T0+2h | `ACTIVE`, 사건 2개(Review Focus 5) |

`RefreshTokenAuditControllerTest`(`@SpringBootTest @AutoConfigureMockMvc`, 전용 datasource `jdbc:h2:mem:audit-query…`, `spring.kafka.listener.auto-startup=false`). 요청에는 `SecurityMockMvcRequestPostProcessors.jwt().authorities(new SimpleGrantedAuthority("SCOPE_audit.read"))` 를 붙인다 — 토큰 검증 자체는 `security` 패키지 테스트가 본다.
- `familiesAreNewestFirstAndEventsInTimeOrder` — sub `user-sub-0001` 에 계열 A(ISSUED 10:00, ROTATED 10:05, 같은 10:05 의 ROTATED 하나 더를 나중에 저장), 계열 B(ISSUED 11:00), 다른 sub 의 계열 C 를 저장. 기대: `$.sub`, `$.asOf` 존재, `$.families.length()` 2, `[0].familyId` B, `[1].familyId` A, A 의 `events[*].eventId` 가 저장 순서(같은 시각 둘은 id 순), C 없음. 그리고 `[1]` 에 `clientId`·`sid`·`issuedAt`·`familyExpiresAt`·`reconstructedState`·`revokedReason`·`reuseDetected`, `events[0]` 에 `eventId`·`kind`·`occurredAt`·`recordedAt`·`expiresAt`·`familyExpiresAt`·`reason`·`revokedTokens`·`activeTokensRevoked` 키가 있다.
- `unknownSubIsAnEmptyList` — 200, `$.families` 빈 배열.

- [ ] **Step 2: 실패 확인 → Step 3: 구현** — `query` 패키지와 컨트롤러. 응답 record 의 javadoc 에 `주의.`: 이 상태는 감사 이력으로 재구성한 값이고 진실의 출처는 `token-state` 다, outbox 폴링·소비만큼 늦다, 그래서 필드 이름이 `reconstructedState` 다. `REVOKED` 를 순서와 무관하게 판정하는 이유(끊긴 계열은 다시 회전하지 않는다 — 시각 보정이 어긋나도 "끊긴 계열이 살아 있다"고 보이지 않게).

- [ ] **Step 4: 실패하는 테스트 — Bearer 보호**

`security/TestJwks`(테스트 도우미, `AutoCloseable`):
- `static TestJwks start()` — RSA 2048 키(`kid` = `"test-kid"`)를 만들고 JDK `com.sun.net.httpserver.HttpServer` 를 `127.0.0.1:0` 에 띄워 `/oauth2/jwks` 에서 공개키 JWKS(`new JWKSet(key.toPublicJWK()).toString()`, `Content-Type: application/json`)를 준다.
- `String jwksUri()`, `void close()`(서버 정지).
- `String token(String typ, String issuer, Instant expiresAt, List<String> scope)` — `RS256`, `kid` 포함, `typ` 이 null 이면 헤더에 `typ` 없음. claim: `iss`, `sub = "audit-operator"`, `aud = "audit-operator"`, `iat`, `exp`, `scope`(JSON 배열 — `token` 의 `AccessTokenIssuer` 와 같은 모양).

`security/AuditApiProtectionTest`(`webEnvironment = RANDOM_PORT`, `spring.kafka.listener.auto-startup=false`, 전용 datasource, 클래스 수준 `static final TestJwks JWKS = TestJwks.start()` + `@DynamicPropertySource` 로 `my.jwk-set-uri`, `@AfterAll` 에 `close`, `TestRestTemplate`). 경로 `/audit/users/user-sub-0001/refresh-token-families`, `ISSUER = "http://localhost:9000"`, 유효 토큰 = `token("at+jwt", ISSUER, now+300s, List.of("audit.read"))`.

| 테스트 | 요청 | 기대 |
|---|---|---|
| `noTokenIs401WithABearerChallenge` | 헤더 없음 | 401, `WWW-Authenticate` = `Bearer` |
| `operatorAccessTokenIs200` | 유효 토큰 | 200 |
| `otherTokenTypesAre401` (`@ParameterizedTest`, `"JWT"`, `"logout+jwt"`) | typ 만 다른 토큰 | 401, `WWW-Authenticate` 에 `error="invalid_token"` |
| `tokenWithoutTypIs401` | typ null | 401 `invalid_token` |
| `wrongIssuerIs401` | iss `http://evil.example` | 401 `invalid_token` |
| `tokenExpiredBeyondClockSkewIs401` | exp now-120s | 401 `invalid_token` |
| `accessTokenWithoutAuditReadIs403` | scope `["introspect"]` | 403, `WWW-Authenticate` 에 `error="insufficient_scope"` |

`security/AuditApiJwksUnavailableTest` — `my.jwk-set-uri` 를 아무것도 듣지 않는 포트로(`new ServerSocket(0)` 으로 포트를 얻고 닫는다). 토큰은 따로 띄운 `TestJwks` 로 서명하고 그 서버는 바로 닫는다. `keysUnavailableIs500NotInvalidToken`: 유효 모양의 토큰 → **500**, 그리고 `WWW-Authenticate` 에 `invalid_token` 이 없다.

`security/AuditApiJwksCacheTest` — `cachedKeysKeepWorkingWhileJwksIsDown`: 유효 토큰으로 200 → `JWKS.close()` → 같은 토큰으로 다시 200.

세 클래스는 프로퍼티가 달라 컨텍스트가 따로 뜬다(의도다 — 캐시 상태를 클래스끼리 나누지 않는다).

`ClientSeedInitializerTest.seedsAllClientsOnEmptyDatabase`: 개수 `3` → `4`, 그리고 `audit-operator` 의 `(clientScopes, grantTypes)` 가 `("audit.read", "client_credentials")`, `scopes`·`redirectUris` 가 `""`, `backchannelLogoutUri` 가 null.

- [ ] **Step 5: 구현** — `SecurityConfig`(위 코드, 주석은 위 근거를 출처와 함께), yml 두 값, `ClientSeedInitializer` 에 `article-api` 와 같은 모양의 `audit-operator` 블록(주석: 감사 조회 API 를 부르는 운영자 — 사용자 흐름에 참여하지 않고 client_credentials 로 `audit.read` 토큰만 받는다. `audit.read` 는 discovery 에 광고하지 않는다).

- [ ] **Step 6: 통과 확인과 변이**
  - `audit`·`client-registry` 전체 테스트 통과.
  - 변이 (a): `jwtProcessorCustomizer(...)` 줄을 지우고 `AuditApiProtectionTest` 를 돌린다. 코드 읽기대로라면 `operatorAccessTokenIs200` 가 401 로, `otherTokenTypesAre401[JWT]` 와 `tokenWithoutTypIs401` 이 200 으로 실패한다. 실제 결과를 그대로 보고서에 남긴다 — 이것이 "기본 디코더가 `at+jwt` 를 어떻게 다루는가"(스펙 5절)의 실측이다.
  - 변이 (b): `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` 을 지우고 `AuditApiJwksUnavailableTest` 를 돌린다. 실제 상태 코드와 `WWW-Authenticate` 를 남긴다. 예상(401)과 다르면 그 줄이 필요한지 다시 따지고, 필요 없으면 지운다 — 근거 없는 줄을 남기지 않는다.
  - 변이 (c): `RefreshTokenFamilyView.from` 의 REVOKED 판정을 "마지막 사건이 REVOKED 인가"로 바꾸면 `anyRevokedEventMakesTheFamilyRevokedWhateverItsPosition` 이 실패하는지.

- [ ] **Step 7: 커밋** — `audit` 과 `client-registry` 경로를 명시해 한 커밋, 제목 예: `audit: let operators read refresh token history with an audit.read token`. 본문에 typ 기본값의 함정(코드 읽기 + 변이 결과)과 500 처리. `git push origin main`.

---

## Task 6: e2e 검증, 재현 스크립트, 문서

**Files:**
- Modify: `http/e2e-login-logout.sh` — `login` 이 `refresh_token=`·`sid=` 에 더해 `sub=`(id token payload 의 `sub`)·`id_token=` 도 출력한다. 헤더 주석의 출력 설명을 고친다.
- Create: `http/e2e-refresh-audit.sh` — `e2e-login-logout.sh` 와 같은 머리(`set -euo pipefail` + ERR trap, 그 주석 문단 포함). 하위 명령:
  - `refresh <refresh_token>` — `POST :9000/oauth2/token`(`my-client` Basic, `grant_type=refresh_token`). 성공하면 `refresh_token=<새 값>`, 실패하면 응답 본문 그대로(종료 코드 1).
  - `revoke <refresh_token>` — `POST :9000/oauth2/revoke`(`my-client` Basic, `token=`), HTTP 상태 코드 출력.
  - `client-token <client_id> <scope>` — `POST :9000/oauth2/token`(`<client_id>:secret` Basic, `grant_type=client_credentials`, `scope=`), `access_token` 만 출력. Basic 값은 스크립트 안에서 `printf '%s' "$id:secret" | base64` 로 만든다.
  - `families <sub> [<bearer>]` — `GET :8089/audit/users/<sub>/refresh-token-families`, 첫 줄에 상태 코드, 이어서 `WWW-Authenticate` 헤더(있으면), 본문(JSON 이면 `python3 -m json.tool`).
- Modify: `README.md`

**Interfaces:**
- Consumes: Task 1~5 전부. 특히 Task 3 의 InnoDB 테스트 결과(실행 여부·반복·변이 (b) 결과)와 Task 5 의 변이 (a)·(b) 결과를 보고서에서 읽어 README 에 옮긴다.

- [ ] **Step 1: 콜드 스타트**

```bash
cd oauth-2/authorization-server/practice/microservice/docker-compose
docker compose -p microservice-as -f docker-compose.yml down -v
docker compose -p microservice-as -f docker-compose.yml up -d
docker ps --filter name=microservice-as --format '{{.Names}} {{.Status}}'
```

10개 서비스를 빌드하고 README "기동 방법" 순서로 띄운다(`audit` 포함). 확인:
- `kafka-topics.sh --list` 에 `oidc.refresh-token.lifecycle.v1` 과 `oidc.refresh-token.lifecycle.v1.audit.dlt` 가 있고, `--describe` 로 둘 다 파티션 3.
- `SHOW CREATE TABLE ms_token_state.outbox`(svc_token_state) 의 `payload` 가 `mediumtext`, `SHOW CREATE TABLE ms_audit.refresh_token_events`(svc_audit) 의 `payload` 가 `mediumtext` 이고 유니크·인덱스가 있다.

- [ ] **Step 2: 운영자 토큰이 코드 변경 없이 나오는가(스펙 8절)**
  - `client-token audit-operator audit.read` 의 토큰 header·payload 를 디코드해 `typ = at+jwt`, `scope = ["audit.read"]`, `sub = aud = audit-operator` 를 보인다.
  - discovery 의 `scopes_supported` 가 그대로 5개(`audit.read` 없음).

- [ ] **Step 3: 성공 기준 1·2** — jar A 로 로그인 → `refresh` 두 번 → `families` 가 계열 하나에 `ISSUED, ROTATED, ROTATED`, `ACTIVE`. 첫 refresh token 을 다시 `refresh` → `invalid_grant` → `families` 에 `REVOKED(REUSE_DETECTED)`, `reuseDetected: true`. 같은 계열을 `SELECT status, revoked_reason, COUNT(*) FROM refresh_tokens WHERE family_id = '…' GROUP BY status, revoked_reason`(svc_token_state)로 직접 조회해 `revokedTokens`·`activeTokensRevoked`·`sub`·`clientId` 와 대조한다.

- [ ] **Step 4: 성공 기준 5·2c** — jar B 로 로그인 → `refresh` 한 번(B 계열에 CONSUMED 1 + ACTIVE 1). jar C 로 로그인(다른 sid). jar B 로그아웃 → `families`: B 는 `REVOKED(SESSION_LOGGED_OUT, 1, 1)`, C 는 `ACTIVE`. B 의 첫 refresh token 으로 `refresh` → `invalid_grant` → B 에 `REVOKED(REUSE_DETECTED, 1, 0)` 추가, 요약 사유 `SESSION_LOGGED_OUT` 그대로, `reuseDetected: true`.

- [ ] **Step 5: 성공 기준 6·4(일부)** — C 의 토큰을 `revoke` → 200, C 가 `REVOKED(CLIENT_REVOKED)`. 같은 토큰을 다시 `revoke` → 200, C 의 사건 수가 그대로.

- [ ] **Step 6: 성공 기준 7**
  - `families user-sub-0001`(토큰 없음) → 401 `Bearer`. jar A 의 `id_token` → 401 `invalid_token`. `client-token article-api introspect` → 403 `insufficient_scope`. `client-token audit-operator audit.read` → 200.
  - 캐시: 200 을 받은 직후 signing 을 내린다(`kill $(lsof -nP -tiTCP:8083 -sTCP:LISTEN)`) → 같은 토큰으로 200.
  - 캐시 없음: signing 이 내려간 채 `audit` 을 재기동 → 같은 토큰(만료 300초 안인지 확인, 아니면 signing 이 살아 있을 때 새로 받아 둔다)으로 500. signing 을 다시 띄우고 200 으로 돌아오는지.

- [ ] **Step 7: 성공 기준 8** — `audit` 을 내린다 → jar D 로 로그인 → `refresh` → 로그아웃, 모두 정상(로그아웃 200, `refresh_tokens` 에서 D 계열 `REVOKED`). `SELECT COUNT(*) FROM refresh_token_events`(svc_audit) → `audit` 기동 → 같은 쿼리로 D 의 사건 3건(ISSUED, ROTATED, REVOKED)이 들어왔는지.

- [ ] **Step 8: 전 모듈 테스트와 무변경 확인**
  - MySQL 이 떠 있는 상태로 10개 모듈 전부 빌드 명령. 모듈별 통과·건너뜀 수와 합계(슬라이스 8 끝 284개)를 남긴다. `token-state` 는 `RefreshTokenServiceMySqlLockSemanticsTest` 가 실행됐다는 XML 근거.
  - `git diff --stat a98d192 -- oauth-2/authorization-server/practice/microservice/session/src` → 비어 있다(성공 기준 10).
  - `git diff --stat a98d192 -- ` 뒤에 `token/src auth/src signing/src consent/src user-directory/src demo-rp/src gateway` 각 경로 → 비어 있다.

- [ ] **Step 9: README** — 측정 출력만 옮기고, 명령을 줄여 적었으면 슬라이스 8 절처럼 맨 앞에 그렇게 적는다.
  - "서비스별 책임과 소유 데이터": `token-state` 행에 사건 발행(outbox, 500ms, 토픽 이름)과 소유 데이터 `outbox`, `audit` 행의 "발행·REST API 없음"을 조회 API(`audit.read`)와 `refresh_token_events` 로.
  - "기동 방법": seed 목록에 `audit-operator`, `audit` 줄에 "조회 API 의 첫 호출은 signing 의 jwks 가 필요하다".
  - "관통 flow": refresh 이력 감사 한 항목.
  - API 시퀀스: `### GET /audit/users/{sub}/refresh-token-families — 운영자의 refresh 이력 조회 (슬라이스 9)` — 사건 흐름(`token-state` outbox → Kafka → `audit`)과 조회(운영자 → token client_credentials → audit → signing jwks) mermaid 두 개, 재구성 규칙, 오류 응답, jwks 캐시(코드 읽기 값과 실측 범위를 구분), 시계 오차 60초.
  - "검증된 성공 기준 > 슬라이스 9": Step 1~8 의 실측. 2b·3·4·9 는 테스트 이름과 결과, InnoDB 테스트는 실행 여부와 반복 횟수, Task 5 변이 (a)·(b) 결과.
  - "알려진 한계": 슬라이스 7 의 "회전-폐기 경쟁" 항목을 Task 3 결과에 맞춰 고친다(보인 것만 쓴다 — 못 보였으면 못 보였다고). 슬라이스 8 의 "감사는 로그아웃만 안다" 를 "로그인은 모른다"로. 새 절 `### 슬라이스 9 (refresh token 이력 감사) 이 남기는 것` = 스펙 9절 그대로 + jwks 캐시 창(코드 읽기) + 로그아웃 처리 중 새로 생긴 계열(Task 3 javadoc) + `aud` 미검증 포인터 + 시계 오차 60초.
  - "설계/계획 문서": 슬라이스 9 설계·계획 링크.
  - 스펙의 문장과 실제가 달라진 곳이 있으면(예: 2절 표) 스펙도 고치고 그 사실을 커밋 본문에 적는다.

- [ ] **Step 10: 커밋** — `README.md`, 두 스크립트(그리고 고쳤다면 스펙)를 경로 명시로. 제목 예: `microservice: verify slice 9 end to end and document it`. `git push origin main`.
