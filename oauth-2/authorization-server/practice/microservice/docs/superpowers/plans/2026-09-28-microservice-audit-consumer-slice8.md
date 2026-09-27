# 마이크로서비스 인가 서버 슬라이스 8 — 감사 소비자 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `oidc.session.logged-out.v1` 에 두 번째 소비자(`audit`)를 붙여, 슬라이스 7의 이벤트 계약이 발행자 무변경으로 그것을 견디는지와 `eventId` 기반 멱등을 실증한다.

**Architecture:** 새 서비스 `audit:8089`(11번째 바이너리)가 전용 스키마 `ms_audit` 에 "공통 머리 + 원문" 레코드를 append-only 로 쌓는다. `event_type` 은 수신 토픽 이름에서 유도하고, `sid` 는 사실(`subject_sid`)·`sub` 는 전문(`reported_sub`)으로 구분해 보관한다. 멱등은 `SessionService.register` 가 확립한 "catch 후 재확인, 행이 있을 때만 흡수" 방식을 따르고, DLT 는 컨슈머 그룹 단위로 나눈다(`token-state` 것도 이름을 바꾼다).

**Tech Stack:** Spring Boot 3.4.5, Java 21, Spring for Apache Kafka, MySQL 8, Apache Kafka 3.9.2(KRaft), h2(테스트), EmbeddedKafka(테스트)

**Spec:** [2026-09-28-microservice-audit-consumer-slice8-design.md](../specs/2026-09-28-microservice-audit-consumer-slice8-design.md)

## Global Constraints

- Spring Boot **3.4.5**, `io.spring.dependency-management` **1.1.7**, Java toolchain **21**, gradle wrapper **8.13**. 새 모듈도 이 값을 그대로 쓴다.
- **`session/src` 는 이 슬라이스에서 한 줄도 바뀌면 안 된다.** 스펙 성공 기준 6번이자 이 슬라이스의 핵심 증거다. 주석 한 줄도 건드리지 않는다.
- **빌드 명령**: `./gradlew` 가 SIGKILL(exit 137) 되면 각 모듈 디렉토리에서 `JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn` 를 설정하고 `$JAVA_HOME/bin/java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain <task> --no-daemon` 로 우회한다. PATH 의 java 는 17이다.
- **주석은 한국어**, 기존 파일 문체를 따른다. 함정·판단 근거는 `주의.` 로 시작하는 문단으로 적는다. **코드가 하는 일보다 한 칸이라도 세게 주장하지 않는다** — 슬라이스 7에서만 여덟 번 걸린 실패 모드다("보장한다"·"수렴한다"·"막아준다").
- **`git add` 는 반드시 경로를 명시한다.** `-A`, `-a`, `.` 는 금지다 — 저장소에 영구 미커밋으로 두는 자격증명 파일이 있다.
- **새 모듈에는 반드시 `.gitignore` 를 먼저 복사한다.** `build/` 를 무시하는 규칙이 루트가 아니라 모듈별 `.gitignore` 에 있다. 없으면 `git add audit/...` 에 빌드 산출물이 섞인다.
- **커밋마다 `git push origin main` 까지** 한 흐름으로 한다. 강제 푸시는 하지 않는다.
- 커밋 메시지 말미에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` 를 넣는다.
- **검증하지 않은 것을 검증했다고 쓰지 않는다.** 브로커가 관련된 측정 전에는 `docker ps` 로 실제 컨테이너 상태를 확인하고 측정 조건을 명시한다 — 슬라이스 7에서 "브로커 없이 8회 통과"가 실제로는 컨테이너가 떠 있던 상태였던 일이 있었다.
- 새 테스트를 추가하면 구현을 일부러 되돌려 그 테스트가 실패하는 것을 확인하고, **되돌린 코드와 깨진 테스트 사이의 인과관계를 스스로 따져** `git diff` 와 함께 보고서에 남긴 뒤 복구한다. 슬라이스 7에서 서로 독립인 두 줄을 하나로 착각해 인과를 잘못 보고한 일이 있었다.

## Review Focus

스펙이 암시하지만 성공 기준 표에 직접 적히지 않은 입력·실패 모드 중, 실제로 물 가능성이 높은 순서다. 각각의 테스트는 소유 태스크에 들어 있다.

1. **`sub` 가 없는 로그아웃 이벤트**(등록된 RP 가 없는 세션) — 실패하지 않고 `reported_sub = NULL` 로 정상 기록돼야 한다. → Task 1 `recordsNullReportedSub`, Task 2 `nullSubIsRecorded`
2. **발행자가 모르는 필드를 더한 페이로드** — 감사는 역직렬화에 실패하지 않고, `payload` 에 그 필드까지 원문 그대로 남아야 한다. → Task 2 `unknownFieldIsPreservedInPayload`
3. **JSON 이 아닌 편지(poison)** — 무한 재시도도, 조용한 유실도 아니고 감사 DLT 로 가야 한다. → Task 2 `poisonGoesToAuditDlt`
4. **같은 `eventId` 가 거의 동시에 두 번 도착**(선조회는 둘 다 통과, 한쪽이 유니크 위반) — 위반을 흡수하고 한 줄만 남아야 한다. 순차 중복 테스트는 선조회에서 끝나 catch 경로를 전혀 타지 않는다. → Task 1 `AuditRecorderBranchTest`
5. **`eventId` 가 빠진 편지** — "없는 값끼리는 같은 이벤트"로 흡수되면 안 되고 실패해야 한다. → Task 1 `missingEventIdIsNotAbsorbed`

---

## File Structure

### 새로 만드는 파일 (`audit/` 모듈)

| 경로 | 책임 |
|---|---|
| `audit/.gitignore` | `token-state/.gitignore` 그대로 복사 — `build/` 무시 |
| `audit/gradlew`, `audit/gradle/wrapper/gradle-wrapper.jar`, `audit/gradle/wrapper/gradle-wrapper.properties` | `token-state` 에서 그대로 복사 |
| `audit/settings.gradle` | `rootProject.name = 'audit'` |
| `audit/build.gradle` | 의존성 |
| `audit/src/main/java/dev/starryeye/audit/AuditApplication.java` | 진입점 |
| `audit/src/main/java/dev/starryeye/audit/AuditRecord.java` | 기록 입력 record |
| `audit/src/main/java/dev/starryeye/audit/AuditRecorder.java` | 멱등 기록 |
| `audit/src/main/java/dev/starryeye/audit/jpa/AuditEventEntity.java` | `audit_events` 행 |
| `audit/src/main/java/dev/starryeye/audit/jpa/AuditEventEntityRepository.java` | `existsByEventId` |
| `audit/src/main/java/dev/starryeye/audit/event/AuditEventConsumer.java` | Kafka 소비 → 기록 (Task 2) |
| `audit/src/main/java/dev/starryeye/audit/event/KafkaConsumerConfig.java` | 토픽 선언·DLT (Task 2) |
| `audit/src/main/resources/application.yml` | 운영 설정 |
| `audit/src/test/resources/application.yml` | 테스트 설정 |
| `audit/src/test/java/dev/starryeye/audit/AuditRecorderTest.java` | h2 통합 테스트 |
| `audit/src/test/java/dev/starryeye/audit/AuditRecorderBranchTest.java` | 스프링 없는 분기 단위 테스트 |
| `audit/src/test/java/dev/starryeye/audit/event/AuditEventConsumerTest.java` | EmbeddedKafka 테스트 (Task 2) |
| `http/e2e-login-logout.sh` | 로그인→토큰→로그아웃 재현 스크립트 (Task 4) |

### 고치는 파일

| 경로 | 무엇을 | 태스크 |
|---|---|---|
| `docker-compose/mysql-init/01-schemas-and-accounts.sql` | `ms_audit` / `svc_audit` | 1 |
| `token-state/.../event/KafkaConsumerConfig.java` | DLT 이름 `...v1.token-state.dlt` | 3 |
| `token-state/.../jpa/RefreshTokenEntity.java` | 슬라이스 7 잔여: `sid` 주석 자기모순 | 3 |
| `token-state/src/test/.../event/DeadLetterTopicTest.java` | 슬라이스 7 잔여: 브로커 클래스 이름 오기 | 3 |
| `docs/superpowers/specs/2026-08-08-...-slice7-design.md` | DLT 이름 변경 주석, 교차 참조 방향 | 3 |
| `docker-compose/docker-compose.yml` | 헤더(10개 서비스), 교차 참조 방향 | 4 |
| `README.md` | 서비스 표·기동 절차·검증 결과·한계·문서 링크 | 4 |

---

## Task 1: `audit` 모듈과 멱등 기록

**Files:**
- Create: `audit/.gitignore`, `audit/gradlew`, `audit/gradle/wrapper/gradle-wrapper.jar`, `audit/gradle/wrapper/gradle-wrapper.properties` (복사)
- Create: `audit/settings.gradle`, `audit/build.gradle`
- Create: `audit/src/main/java/dev/starryeye/audit/AuditApplication.java`
- Create: `audit/src/main/java/dev/starryeye/audit/AuditRecord.java`
- Create: `audit/src/main/java/dev/starryeye/audit/AuditRecorder.java`
- Create: `audit/src/main/java/dev/starryeye/audit/jpa/AuditEventEntity.java`
- Create: `audit/src/main/java/dev/starryeye/audit/jpa/AuditEventEntityRepository.java`
- Create: `audit/src/main/resources/application.yml`, `audit/src/test/resources/application.yml`
- Test: `audit/src/test/java/dev/starryeye/audit/AuditRecorderTest.java`
- Test: `audit/src/test/java/dev/starryeye/audit/AuditRecorderBranchTest.java`
- Modify: `docker-compose/mysql-init/01-schemas-and-accounts.sql`

**Interfaces:**
- Consumes: 없음 (첫 태스크)
- Produces:
  - `record AuditRecord(String eventId, String eventType, Instant occurredAt, String subjectSid, String reportedSub, String payload)`
  - `AuditRecorder.record(AuditRecord record)` → `boolean` (`true` = 새로 기록, `false` = 이미 있어 흡수). 제약 위반이면 `DataIntegrityViolationException` 을 그대로 던진다
  - `AuditEventEntityRepository.existsByEventId(String eventId)` → `boolean`
  - 스키마 `ms_audit`, 계정 `svc_audit` / `pw_audit`

**이 태스크에는 Kafka 가 없다.** 소비자는 Task 2 가 붙인다. 그래서 `build.gradle` 에도 `spring-kafka` 를 넣지 않는다(Task 2 가 더한다).

- [ ] **Step 1: 모듈 뼈대 복사**

```bash
cd oauth-2/authorization-server/practice/microservice
mkdir -p audit/gradle/wrapper
cp token-state/.gitignore audit/.gitignore
cp token-state/gradlew audit/gradlew
cp token-state/gradle/wrapper/gradle-wrapper.jar token-state/gradle/wrapper/gradle-wrapper.properties audit/gradle/wrapper/
chmod +x audit/gradlew
```

`audit/settings.gradle`:

```groovy
rootProject.name = 'audit'
```

`audit/build.gradle`:

```groovy
plugins {
	id 'java'
	id 'org.springframework.boot' version '3.4.5'
	id 'io.spring.dependency-management' version '1.1.7'
}

group = 'dev.starryeye'
version = '0.0.1-SNAPSHOT'

java {
	toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

configurations {
	compileOnly { extendsFrom annotationProcessor }
}

repositories { mavenCentral() }

dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-web'
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	runtimeOnly 'com.mysql:mysql-connector-j'
	compileOnly 'org.projectlombok:lombok'
	annotationProcessor 'org.projectlombok:lombok'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testRuntimeOnly 'com.h2database:h2'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') { useJUnitPlatform() }
```

> `spring-boot-starter-web` 은 REST API 를 만들려는 게 아니라 스펙이 정한 포트 8089 로 떠 있게 하려는 것이다(다른 서비스와 같은 모양). 컨트롤러는 하나도 만들지 않는다.

`audit/src/main/java/dev/starryeye/audit/AuditApplication.java`:

```java
package dev.starryeye.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuditApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuditApplication.class, args);
	}
}
```

`audit/src/main/resources/application.yml`:

```yaml
server:
  port: 8089

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ms_audit?useSSL=false&allowPublicKeyRetrieval=true
    username: svc_audit
    password: pw_audit
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

logging:
  level:
    dev.starryeye: DEBUG
```

`audit/src/test/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:audit;DB_CLOSE_DELAY=-1;MODE=MySQL
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
```

- [ ] **Step 2: 실패하는 테스트 작성 — 분기 단위 테스트(Review Focus 4)**

`audit/src/test/java/dev/starryeye/audit/AuditRecorderBranchTest.java`:

```java
package dev.starryeye.audit;

import dev.starryeye.audit.jpa.AuditEventEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditRecorderBranchTest {

	/**
	 * catch 경로의 두 갈래를 스프링 없이 고정한다.
	 *
	 * 주의. 같은 이벤트를 순차로 두 번 넣는 통합 테스트는 두 번째가 선조회(existsByEventId)에서 끝나
	 *      catch 경로를 한 번도 타지 않는다. 그 경로가 실제로 쓰이는 것은 두 전달이 거의 동시에 도착해
	 *      둘 다 선조회를 통과한 뒤 한쪽이 유니크 위반을 맞는 경우뿐이라, 통합 테스트로는 재현하기 어렵다.
	 *      그래서 선조회 결과와 save 의 예외를 직접 지정해 두 갈래를 각각 고정한다.
	 */

	private static final AuditRecord RECORD = new AuditRecord(
			"E-1", "oidc.session.logged-out.v1", Instant.parse("2026-09-28T00:00:00Z"),
			"SID-A", "user-sub-0001", "{\"eventId\":\"E-1\"}");

	@Test
	@DisplayName("선조회는 통과했는데 저장이 위반을 맞고, 재확인하니 행이 있다 — 다른 전달이 먼저 넣은 것이므로 흡수한다")
	void absorbsWhenRowAppearedConcurrently() {
		AuditEventEntityRepository repository = mock(AuditEventEntityRepository.class);
		when(repository.existsByEventId("E-1")).thenReturn(false, true);
		when(repository.save(any())).thenThrow(new DataIntegrityViolationException("uk_audit_events_event_id"));

		boolean recorded = new AuditRecorder(repository).record(RECORD);

		assertThat(recorded).isFalse();
	}

	@Test
	@DisplayName("저장이 위반을 맞았는데 재확인해도 행이 없다 — 다른 제약 위반이므로 다시 던진다")
	void rethrowsWhenRowStillAbsent() {
		AuditEventEntityRepository repository = mock(AuditEventEntityRepository.class);
		when(repository.existsByEventId("E-1")).thenReturn(false, false);
		when(repository.save(any())).thenThrow(new DataIntegrityViolationException("not-null subject_sid"));

		assertThatThrownBy(() -> new AuditRecorder(repository).record(RECORD))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
```

- [ ] **Step 3: 실패하는 테스트 작성 — h2 통합 테스트**

`audit/src/test/java/dev/starryeye/audit/AuditRecorderTest.java`:

```java
package dev.starryeye.audit;

import dev.starryeye.audit.jpa.AuditEventEntity;
import dev.starryeye.audit.jpa.AuditEventEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:audit-recorder;DB_CLOSE_DELAY=-1;MODE=MySQL")
class AuditRecorderTest {

	/**
	 * 감사 레코드가 이벤트의 사실을 그대로 받아 적는지, 같은 이벤트를 두 번 받아도 한 줄인지,
	 *      그리고 흡수하면 안 되는 위반은 흡수하지 않는지를 실제 DB 제약으로 확인한다.
	 *
	 * 주의. datasource url 을 이 클래스 전용으로 준다. 이 저장소의 테스트는 이름 있는 h2 인메모리 DB 를
	 *      여러 @SpringBootTest 컨텍스트가 공유하는 함정을 슬라이스 7에서 겪었다(프로퍼티가 달라 컨텍스트
	 *      캐시가 갈려도 같은 이름이면 같은 테이블이다).
	 */

	private static final String TOPIC = "oidc.session.logged-out.v1";

	@Autowired
	private AuditRecorder recorder;

	@Autowired
	private AuditEventEntityRepository repository;

	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	@Test
	@DisplayName("이벤트의 사실을 공통 머리와 원문으로 받아 적는다")
	void recordsFacts() {
		Instant occurredAt = Instant.parse("2026-09-28T01:02:03.456Z");
		String payload = "{\"eventId\":\"E-1\",\"sid\":\"SID-A\",\"sub\":\"user-sub-0001\",\"occurredAt\":\"2026-09-28T01:02:03.456Z\"}";

		boolean recorded = recorder.record(new AuditRecord("E-1", TOPIC, occurredAt, "SID-A", "user-sub-0001", payload));

		assertThat(recorded).isTrue();
		List<AuditEventEntity> rows = repository.findAll();
		assertThat(rows).hasSize(1);
		AuditEventEntity row = rows.get(0);
		assertThat(row.getEventId()).isEqualTo("E-1");
		assertThat(row.getEventType()).isEqualTo(TOPIC);
		assertThat(row.getOccurredAt()).isEqualTo(occurredAt);
		assertThat(row.getSubjectSid()).isEqualTo("SID-A");
		assertThat(row.getReportedSub()).isEqualTo("user-sub-0001");
		assertThat(row.getPayload()).isEqualTo(payload);
		assertThat(row.getRecordedAt()).isNotNull();
	}

	@Test
	@DisplayName("같은 eventId 를 두 번 받으면 한 줄이다")
	void sameEventIdIsRecordedOnce() {
		AuditRecord record = new AuditRecord("E-2", TOPIC, Instant.now(), "SID-A", "user-sub-0001", "{}");

		assertThat(recorder.record(record)).isTrue();
		assertThat(recorder.record(record)).isFalse();

		assertThat(repository.findAll()).hasSize(1);
	}

	@Test
	@DisplayName("sub 가 없는 이벤트도 기록된다 — reported_sub 는 NULL 이다(Review Focus 1)")
	void recordsNullReportedSub() {
		boolean recorded = recorder.record(new AuditRecord("E-3", TOPIC, Instant.now(), "SID-NO-RP", null, "{}"));

		assertThat(recorded).isTrue();
		assertThat(repository.findAll().get(0).getReportedSub()).isNull();
	}

	@Test
	@DisplayName("sid 가 빠진 기형 이벤트는 흡수되지 않고 실패한다 — 조용히 흡수하면 감사가 기록을 잃는다")
	void missingSidIsNotAbsorbed() {
		AuditRecord record = new AuditRecord("E-4", TOPIC, Instant.now(), null, "user-sub-0001", "{}");

		assertThatThrownBy(() -> recorder.record(record))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(repository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("eventId 가 빠진 편지는 흡수되지 않고 실패한다 — 없는 값끼리를 같은 이벤트로 치면 안 된다(Review Focus 5)")
	void missingEventIdIsNotAbsorbed() {
		AuditRecord record = new AuditRecord(null, TOPIC, Instant.now(), "SID-A", "user-sub-0001", "{}");

		assertThatThrownBy(() -> recorder.record(record))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(repository.findAll()).isEmpty();
	}
}
```

- [ ] **Step 4: 테스트가 컴파일 실패하는 것 확인**

```bash
cd audit
JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn \
  $JAVA_HOME/bin/java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon
```

Expected: 컴파일 에러 — `AuditRecord`, `AuditRecorder`, `AuditEventEntity`, `AuditEventEntityRepository` 가 없다.

- [ ] **Step 5: 기록 입력 record 작성**

`audit/src/main/java/dev/starryeye/audit/AuditRecord.java`:

```java
package dev.starryeye.audit;

import java.time.Instant;

/**
 * 감사 레코드 한 줄에 들어갈 값. 소비자가 이벤트에서 뽑아 만든다.
 *
 * 주의. subjectSid 와 reportedSub 는 성격이 다르다. subjectSid 는 발행자가 확실히 아는 값(로그아웃은 sid 로
 *      요청되고 발행자는 그 인자를 그대로 싣는다)이고, reportedSub 는 발행자가 그렇다고 보고한 값일 뿐이다 —
 *      session 은 한 sid 아래 여러 sub 가 섞이는 것을 막지 않고 첫 행의 값을 싣는다(슬라이스 7 설계 5절).
 *      이름이 그 차이를 말하게 했다.
 */
public record AuditRecord(
		String eventId,
		String eventType,
		Instant occurredAt,
		String subjectSid,
		String reportedSub,
		String payload
) {
}
```

- [ ] **Step 6: 엔티티와 레포지토리 작성**

`audit/src/main/java/dev/starryeye/audit/jpa/AuditEventEntity.java`:

```java
package dev.starryeye.audit.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "audit_events",
		uniqueConstraints = @UniqueConstraint(name = "uk_audit_events_event_id", columnNames = "event_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEventEntity {

	/**
	 * 감사 레코드 한 줄. append-only 다 — 한 번 쓰면 고치지도 지우지도 않는다.
	 *
	 * 주의. 모든 이벤트가 공유하는 것만 컬럼으로 뽑고 나머지는 payload 원문으로 보관한다. 이벤트 타입이
	 *      늘어도(후속 슬라이스) 스키마가 바뀌지 않고, 발행자가 필드를 더해도 손실 없이 받아 적는다.
	 *
	 * 주의. event_id 의 UNIQUE 가 멱등의 근거다. append-only 소비자는 같은 이벤트를 두 번 받으면 그대로
	 *      두 줄이 되므로(outbox 재전송, 오프셋 커밋 전 재기동), 폐기처럼 조건부 갱신으로 멱등이 공짜가
	 *      되지 않는다.
	 *
	 * 주의. occurred_at 은 발행자가 기록한 사건 시각, recorded_at 은 감사가 받아 적은 시각이다. 둘의 차이가
	 *      전달 지연이다.
	 *
	 * 주의. subject_sid 는 지금 not null 이다 — 로그아웃 이벤트의 sid 는 항상 있다. refresh 계열 이벤트를
	 *      받게 되면 그 원천(refresh_tokens.sid)이 nullable 이라 이 제약을 완화해야 한다(스펙 3절). 미리 열어두지
	 *      않는 것은 도달 불가능한 상태를 허용하지 않기 위해서다.
	 *
	 * 주의. payload 는 @Lob 이라 MySQL 에서 longtext 가 된다. 길이 초과로는 제약 위반에 도달하지 않는다.
	 */

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, length = 36)
	private String eventId;

	@Column(name = "event_type", nullable = false, length = 255)
	private String eventType;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "recorded_at", nullable = false)
	private Instant recordedAt;

	@Column(name = "subject_sid", nullable = false, length = 64)
	private String subjectSid;

	@Column(name = "reported_sub")
	private String reportedSub;

	@Lob
	@Column(nullable = false)
	private String payload;

	@Builder
	private AuditEventEntity(String eventId, String eventType, Instant occurredAt, Instant recordedAt,
			String subjectSid, String reportedSub, String payload) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.occurredAt = occurredAt;
		this.recordedAt = recordedAt;
		this.subjectSid = subjectSid;
		this.reportedSub = reportedSub;
		this.payload = payload;
	}
}
```

`audit/src/main/java/dev/starryeye/audit/jpa/AuditEventEntityRepository.java`:

```java
package dev.starryeye.audit.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventEntityRepository extends JpaRepository<AuditEventEntity, Long> {

	boolean existsByEventId(String eventId);
}
```

- [ ] **Step 7: 기록기 작성**

`audit/src/main/java/dev/starryeye/audit/AuditRecorder.java`:

```java
package dev.starryeye.audit;

import dev.starryeye.audit.jpa.AuditEventEntity;
import dev.starryeye.audit.jpa.AuditEventEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuditRecorder {

	/**
	 * 감사 레코드 한 줄을 남긴다. 같은 eventId 가 다시 오면 아무것도 하지 않는다(멱등).
	 *
	 * 주의. 선조회(existsByEventId)만으로는 멱등이 아니다. 같은 eventId 가 거의 동시에 두 번 도착하면 둘 다
	 *      "없음"을 보고 둘 다 INSERT 를 시도하고, 진 쪽이 uk_audit_events_event_id 위반을 맞는다. 그 위반을
	 *      흡수해야 호출자(소비자) 관점에서도 멱등이다 — 흡수하지 않으면 멀쩡한 중복 전달이 실패로 취급돼
	 *      재시도 끝에 DLT 로 간다.
	 *
	 * 주의. 다만 DataIntegrityViolationException 을 무조건 흡수하면 안 된다. 이 예외는 유니크 위반뿐 아니라
	 *      not null 위반에서도 난다(sid 나 eventId 가 빠진 기형 이벤트). 무조건 흡수하면 기록에 실패한 이벤트가
	 *      성공으로 넘어가 감사가 그 기록을 조용히 잃는다. 그래서 잡은 뒤 existsByEventId 로 재확인한다 —
	 *      행이 실제로 있으면(다른 전달이 먼저 넣었으면) 흡수하고, 없으면 다시 던진다.
	 *
	 * 주의. 이 메서드에는 @Transactional 을 두지 않는다. 감싸면 IDENTITY 채번이 save 시점에 즉시 flush 하다
	 *      실패하면서 그 바깥 트랜잭션을 rollback-only 로 오염시켜, 예외를 잡아도 커밋 시점에
	 *      UnexpectedRollbackException 이 새로 터진다. existsByEventId 와 save 를 각각 레포지토리 자신의
	 *      트랜잭션으로 두어야 catch 가 실제로 유효하다. session 의 SessionService.register 와 같은 이유·같은 모양이다.
	 *
	 * 주의. 같은 eventId 인데 payload 가 다른 편지가 오면 조용히 흡수된다. 정상 경로에서는 불가능하고(outbox 는
	 *      같은 행을 재전송한다), 일어난다면 발행자가 eventId 를 재사용한 버그다. 어느 쪽이 진짜인지 판정할
	 *      근거가 감사에게 없어 비교하지 않는다(스펙 4절).
	 *
	 * @return 새로 기록했으면 true, 이미 있어 흡수했으면 false
	 */

	private final AuditEventEntityRepository repository;

	public boolean record(AuditRecord record) {
		if (repository.existsByEventId(record.eventId())) {
			return false;
		}
		try {
			repository.save(AuditEventEntity.builder()
					.eventId(record.eventId())
					.eventType(record.eventType())
					.occurredAt(record.occurredAt())
					.recordedAt(Instant.now())
					.subjectSid(record.subjectSid())
					.reportedSub(record.reportedSub())
					.payload(record.payload())
					.build());
			return true;
		} catch (DataIntegrityViolationException e) {
			// 유니크 위반으로 진 쪽이면 다른 전달이 같은 eventId 행을 먼저 만든 것이다 — 원하던 결과가 이미 있으므로 흡수한다.
			// 없으면(not null 등 다른 제약 위반) 기록이 진짜로 실패한 것이므로 다시 던진다.
			if (!repository.existsByEventId(record.eventId())) {
				throw e;
			}
			return false;
		}
	}
}
```

- [ ] **Step 8: 테스트 통과 확인**

```bash
cd audit
JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn \
  $JAVA_HOME/bin/java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon
```

Expected: BUILD SUCCESSFUL, 7개 테스트 통과.

> `missingSidIsNotAbsorbed`·`missingEventIdIsNotAbsorbed` 에서 실제로 나는 예외는 Hibernate 의 not-null 검사(`PropertyValueException`)가 Spring 의 예외 변환을 거친 `DataIntegrityViolationException` 일 것이다(Bean Validation 이 클래스패스에 없으면 Hibernate 가 persist 시점에 직접 검사한다). DB 까지 가서 나는 제약 위반이든 Hibernate 가 먼저 막든 기록기 입장에서는 같은 예외다. **실제로 어느 쪽에서 났는지 스택트레이스로 확인해 보고서에 적어라** — 추정으로 적지 마라.

- [ ] **Step 9: 테스트가 실제로 무는지 확인 — 두 변이**

**변이 A — 재확인을 빼고 무조건 흡수한다.** `AuditRecorder` 의 catch 블록을 `return false;` 한 줄로 바꾼다.

Expected: `AuditRecorderBranchTest.rethrowsWhenRowStillAbsent`, `AuditRecorderTest.missingSidIsNotAbsorbed`, `AuditRecorderTest.missingEventIdIsNotAbsorbed` 세 개가 실패한다.

**변이 B — 흡수를 없앤다.** catch 블록을 `throw e;` 한 줄로 바꾼다.

Expected: `AuditRecorderBranchTest.absorbsWhenRowAppearedConcurrently` 가 실패한다. `sameEventIdIsRecordedOnce` 는 **깨지지 않는다** — 순차 중복은 선조회에서 끝나 catch 에 도달하지 않기 때문이다. 이 차이가 분기 단위 테스트가 필요한 이유다.

각 변이의 `git diff`, 실패 출력, 그리고 **왜 그 테스트가 깨지고 왜 저 테스트는 안 깨지는지** 인과를 보고서에 적고 원복한다.

- [ ] **Step 10: 스키마·계정 추가**

`docker-compose/mysql-init/01-schemas-and-accounts.sql` 에 세 줄을 기존 목록 끝에 맞춰 넣는다.

```sql
CREATE DATABASE IF NOT EXISTS ms_audit           CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

(`ms_session` 줄 바로 아래)

```sql
CREATE USER IF NOT EXISTS 'svc_audit'@'%'           IDENTIFIED BY 'pw_audit';
```

(`svc_session` 줄 바로 아래)

```sql
GRANT ALL PRIVILEGES ON ms_audit.*           TO 'svc_audit'@'%';
```

(`ms_session` GRANT 줄 바로 아래)

- [ ] **Step 11: 실제 MySQL 로 분리 확인 — 스펙 성공 기준 1**

초기화 SQL 은 데이터 디렉토리가 비어 있을 때만 돈다. 볼륨까지 지우고 띄운다.

```bash
cd oauth-2/authorization-server/practice/microservice
docker compose -p microservice-as -f docker-compose/docker-compose.yml down -v
docker compose -p microservice-as -f docker-compose/docker-compose.yml up -d mysql
sleep 15
docker exec -i microservice-as-mysql-1 mysql -usvc_audit -ppw_audit -e "SHOW DATABASES"
docker exec -i microservice-as-mysql-1 mysql -usvc_audit -ppw_audit -e "SELECT 1 FROM ms_token_state.refresh_tokens LIMIT 1"
docker exec -i microservice-as-mysql-1 mysql -usvc_audit -ppw_audit -e "SELECT 1 FROM ms_session.oidc_sessions LIMIT 1"
```

Expected:
- `SHOW DATABASES` 에 `ms_audit` 은 보이고 다른 `ms_*` 는 보이지 않는다
- 두 조회 모두 **접근 거부 오류로 실패한다**(`ERROR 1142` 또는 `ERROR 1044` — 슬라이스 7에서는 1142 가 났다). 성공하면 이 태스크는 실패다

그다음 `audit` 을 실제 MySQL 로 띄워 테이블이 자기 스키마에 생기는지 확인한다.

```bash
cd audit && JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn \
  $JAVA_HOME/bin/java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain bootJar --no-daemon -q
nohup /Users/starryeye/.sdkman/candidates/java/21.0.6-amzn/bin/java -jar build/libs/audit-0.0.1-SNAPSHOT.jar > /tmp/audit.log 2>&1 &
sleep 20
docker exec -i microservice-as-mysql-1 mysql -usvc_audit -ppw_audit -e "SHOW CREATE TABLE ms_audit.audit_events\G"
pkill -f 'audit-0.0.1-SNAPSHOT.jar'
```

Expected: `audit_events` 가 `ms_audit` 에 있고 `UNIQUE KEY uk_audit_events_event_id (event_id)` 가 보인다. 명령과 출력을 그대로 보고서에 옮긴다.

- [ ] **Step 12: 커밋**

```bash
cd /Users/starryeye/study/spring-security
git status --porcelain oauth-2/authorization-server/practice/microservice/audit   # build/ 가 안 보여야 한다
git add oauth-2/authorization-server/practice/microservice/audit/.gitignore \
        oauth-2/authorization-server/practice/microservice/audit/gradlew \
        oauth-2/authorization-server/practice/microservice/audit/gradle \
        oauth-2/authorization-server/practice/microservice/audit/settings.gradle \
        oauth-2/authorization-server/practice/microservice/audit/build.gradle \
        oauth-2/authorization-server/practice/microservice/audit/src \
        oauth-2/authorization-server/practice/microservice/docker-compose/mysql-init/01-schemas-and-accounts.sql
git commit -m "$(cat <<'EOF'
audit: add the audit service with idempotent append-only records

11번째 바이너리. 같은 토픽에 두 번째 소비자를 붙이기 위한 저장소와 기록기를
먼저 만든다(소비자는 다음 커밋).

- ms_audit / svc_audit — 자기 스키마에만 GRANT. 남의 스키마 조회는 권한 오류
- audit_events: 공통 머리(event_id, event_type, occurred_at, recorded_at,
  subject_sid, reported_sub) + payload 원문
- 멱등은 SessionService.register 방식 그대로 — 메서드에 @Transactional 없이
  catch 후 재확인, 행이 있을 때만 흡수. 무조건 흡수하면 sid 가 빠진 기형
  이벤트가 성공으로 넘어가 감사가 기록을 조용히 잃는다

순차 중복 테스트는 선조회에서 끝나 catch 경로를 타지 않는다. 그래서 동시
도착의 두 갈래(흡수 / 다시 던짐)를 스프링 없는 분기 테스트로 따로 고정했다.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 2: Kafka 소비자와 감사 DLT

**Files:**
- Modify: `audit/build.gradle` (spring-kafka 의존성)
- Modify: `audit/src/main/resources/application.yml`, `audit/src/test/resources/application.yml` (kafka 블록)
- Create: `audit/src/main/java/dev/starryeye/audit/event/AuditEventConsumer.java`
- Create: `audit/src/main/java/dev/starryeye/audit/event/KafkaConsumerConfig.java`
- Modify: `audit/src/test/java/dev/starryeye/audit/AuditRecorderTest.java` (리스너 자동 시작 끄기)
- Test: `audit/src/test/java/dev/starryeye/audit/event/AuditEventConsumerTest.java`

**Interfaces:**
- Consumes: Task 1 의 `AuditRecorder.record(AuditRecord)` → `boolean`, `AuditEventEntityRepository.existsByEventId`
- Produces:
  - `AuditEventConsumer.LOGGED_OUT_TOPIC = "oidc.session.logged-out.v1"`
  - `KafkaConsumerConfig.dltFor(String sourceTopic)` → `sourceTopic + ".audit.dlt"` (현재 유일한 값은 `oidc.session.logged-out.v1.audit.dlt`)
  - 컨슈머 그룹: 운영 `audit`, 테스트 `audit-test`

- [ ] **Step 1: 의존성과 설정 추가**

`audit/build.gradle` 의 `dependencies` 에 추가한다(버전은 Boot BOM 이 관리하므로 적지 않는다).

```groovy
	implementation 'org.springframework.kafka:spring-kafka'
	testImplementation 'org.springframework.kafka:spring-kafka-test'
	testImplementation 'org.awaitility:awaitility'
```

`audit/src/main/resources/application.yml` 의 `spring` 아래에 추가하고, 파일 끝에 `my` 블록을 둔다.

```yaml
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: ${my.consumer-group-id}
      auto-offset-reset: earliest
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
    listener:
      ack-mode: record

my:
  consumer-retry-attempts: 2
  consumer-retry-interval-ms: 200
  consumer-group-id: audit                # token-state 와 완전히 별개인 그룹. 테스트는 audit-test
```

> `auto-offset-reset: earliest` 이므로 이 그룹을 처음 띄우면 토픽 처음부터 읽는다. 감사를 나중에 띄워도 그동안의 로그아웃을 소급해 채운다(스펙 성공 기준 3).
>
> `producer` 설정이 필요한 이유 — 이 서비스는 발행을 하지 않지만, 재시도가 소진된 편지를 DLT 로 옮기는 `DeadLetterPublishingRecoverer` 가 `KafkaTemplate` 을 쓴다.

`audit/src/test/resources/application.yml` 의 `spring` 아래에 추가하고 파일 끝에 `my` 블록을 둔다. **테스트 yml 은 main 을 병합하지 않고 대체하므로 kafka 블록 전체가 필요하다.**

```yaml
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: ${my.consumer-group-id}
      auto-offset-reset: earliest
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
    listener:
      ack-mode: record
    # 주의. token-state 의 테스트 yml 에서 옮겨온 설정이다. NewTopic 빈이 있으면 Kafka 를 안 띄운 채 도는
    #      컨텍스트도 기동 시 KafkaAdmin 이 토픽 생성을 시도해, 이 네 줄이 없으면 컨텍스트마다 수십 초씩
    #      멈춘다(token-state 에서 실측: 브로커 없이 컨텍스트당 약 40초). spring.kafka.admin.operation-timeout
    #      한 줄로 줄이는 방법은 session 쪽에서 해보고 되돌렸다 — 브로커가 없을 때 오히려 느려졌다.
    admin:
      properties:
        request.timeout.ms: 2000
        default.api.timeout.ms: 2000
        socket.connection.setup.timeout.ms: 1000
        socket.connection.setup.timeout.max.ms: 1000

my:
  consumer-retry-attempts: 2
  consumer-retry-interval-ms: 200
  consumer-group-id: audit-test           # 운영 그룹("audit")과 분리 — 스택을 띄워 둔 채 테스트를 돌려도
                                           # 테스트 JVM 이 운영 그룹에 조인해 실제 이벤트를 가져가지 않게
```

- [ ] **Step 2: Task 1 의 통합 테스트에서 리스너를 끈다**

Task 2 부터는 `@SpringBootTest` 가 리스너 컨테이너도 띄운다. `AuditRecorderTest` 는 Kafka 와 무관한데, 로컬에 실제 브로커가 떠 있으면 그 리스너가 `localhost:9092` 로 붙어 **실제 로그아웃 이벤트를 테스트 h2 에 기록해** 행 수 단언을 깨뜨린다. 리스너 자동 시작을 끈다.

`AuditRecorderTest` 의 애노테이션을 바꾼다.

```java
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:audit-recorder;DB_CLOSE_DELAY=-1;MODE=MySQL",
		"spring.kafka.listener.auto-startup=false"   // Kafka 와 무관한 테스트다. 실제 브로커가 떠 있어도 이벤트를 받지 않게
})
```

클래스 주석에 이 한 줄을 넣은 이유를 `주의.` 문단으로 덧붙인다.

- [ ] **Step 3: 실패하는 테스트 작성**

`audit/src/test/java/dev/starryeye/audit/event/AuditEventConsumerTest.java`:

```java
package dev.starryeye.audit.event;

import dev.starryeye.audit.jpa.AuditEventEntity;
import dev.starryeye.audit.jpa.AuditEventEntityRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
		"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
		"spring.datasource.url=jdbc:h2:mem:audit-consumer;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
@EmbeddedKafka(partitions = 3, topics = {
		AuditEventConsumer.LOGGED_OUT_TOPIC,
		AuditEventConsumer.LOGGED_OUT_TOPIC + ".audit.dlt"
})
class AuditEventConsumerTest {

	/**
	 * 로그아웃 이벤트가 감사 레코드로 받아 적히는지, 중복 전달이 한 줄로 흡수되는지, 기형 편지가 감사 DLT 로
	 *      가는지를 실제 브로커(임베디드)로 확인한다.
	 *
	 * 주의. @EmbeddedKafka 의 파티션 수(3)를 KafkaConsumerConfig 의 NewTopic 선언과 같게 둔다. 슬라이스 7에서
	 *      둘이 어긋나 KafkaAdmin 이 토픽을 늘리는 바람에 테스트 전제가 조용히 깨진 일이 있었다.
	 *
	 * 주의. 순서가 필요한 단언은 모든 편지를 같은 키로 보낸다. 같은 키는 같은 파티션에 떨어지고, 한 파티션
	 *      안에서는 순서대로 처리되므로 "나중에 보낸 표지 편지가 기록됐다"가 "앞의 편지들도 처리가 끝났다"를
	 *      뜻하게 된다. 기다림을 시간에 맡기지 않고 표지로 확정하는 방법이다.
	 *
	 * 주의. DLT 소비자는 consumeFromAnEmbeddedTopic 의 3인자(seekToEnd=true) 오버로드로 구독한다. 2인자는
	 *      항상 맨 앞으로 가서 같은 클래스의 앞 테스트가 남긴 레코드를 다시 읽는다(슬라이스 7에서 겪었다).
	 */

	private static final String TOPIC = AuditEventConsumer.LOGGED_OUT_TOPIC;
	private static final String DLT = TOPIC + ".audit.dlt";

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private AuditEventEntityRepository repository;

	@Autowired
	private EmbeddedKafkaBroker broker;

	private Consumer<String, String> dltConsumer;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
		Map<String, Object> props = KafkaTestUtils.consumerProps("audit-dlt-probe-" + UUID.randomUUID(), "true", broker);
		dltConsumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer());
		broker.consumeFromAnEmbeddedTopic(dltConsumer, true, DLT);
	}

	@AfterEach
	void tearDown() {
		dltConsumer.close();
	}

	@Test
	@DisplayName("로그아웃 이벤트가 감사 레코드로 받아 적힌다 — event_type 은 수신 토픽 이름 그대로다")
	void logoutEventIsRecorded() throws Exception {
		String eventId = UUID.randomUUID().toString();
		String payload = event(eventId, "SID-A", "\"user-sub-0001\"");

		send("SID-A", payload);

		AuditEventEntity row = awaitRow(eventId);
		assertThat(row.getEventType()).isEqualTo(TOPIC);
		assertThat(row.getSubjectSid()).isEqualTo("SID-A");
		assertThat(row.getReportedSub()).isEqualTo("user-sub-0001");
		assertThat(row.getOccurredAt()).isEqualTo(java.time.Instant.parse("2026-09-28T01:02:03.456Z"));
		assertThat(row.getPayload()).isEqualTo(payload);
	}

	@Test
	@DisplayName("같은 이벤트가 두 번 전달돼도 한 줄이다")
	void duplicateDeliveryIsRecordedOnce() throws Exception {
		String eventId = UUID.randomUUID().toString();
		String payload = event(eventId, "SID-B", "\"user-sub-0001\"");
		String markerId = UUID.randomUUID().toString();

		send("SID-B", payload);
		send("SID-B", payload);
		send("SID-B", event(markerId, "SID-B", "\"user-sub-0001\""));  // 같은 키 → 앞의 둘이 처리된 뒤 처리된다

		awaitRow(markerId);
		assertThat(rowsOf(eventId)).hasSize(1);
	}

	@Test
	@DisplayName("sub 가 없는 이벤트도 기록된다 — reported_sub 는 NULL(Review Focus 1)")
	void nullSubIsRecorded() throws Exception {
		String eventId = UUID.randomUUID().toString();

		send("SID-NO-RP", event(eventId, "SID-NO-RP", "null"));

		assertThat(awaitRow(eventId).getReportedSub()).isNull();
	}

	@Test
	@DisplayName("발행자가 모르는 필드를 더해도 기록되고, 원문에 그 필드가 남는다(Review Focus 2)")
	void unknownFieldIsPreservedInPayload() throws Exception {
		String eventId = UUID.randomUUID().toString();
		String payload = "{\"eventId\":\"" + eventId + "\",\"sid\":\"SID-C\",\"sub\":\"user-sub-0001\","
				+ "\"occurredAt\":\"2026-09-28T01:02:03.456Z\",\"addedLater\":\"v1-compatible\"}";

		send("SID-C", payload);

		assertThat(awaitRow(eventId).getPayload()).contains("\"addedLater\":\"v1-compatible\"");
	}

	@Test
	@DisplayName("sid 가 빠진 기형 이벤트는 기록되지 않고 감사 DLT 로 간다 — 조용히 흡수되지 않는다")
	void missingSidGoesToAuditDlt() throws Exception {
		String eventId = UUID.randomUUID().toString();
		String payload = "{\"eventId\":\"" + eventId + "\",\"sub\":\"user-sub-0001\",\"occurredAt\":\"2026-09-28T01:02:03.456Z\"}";

		send("SID-D", payload);

		assertThat(KafkaTestUtils.getSingleRecord(dltConsumer, DLT, Duration.ofSeconds(20)).value()).isEqualTo(payload);
		assertThat(rowsOf(eventId)).isEmpty();
	}

	@Test
	@DisplayName("JSON 이 아닌 편지는 감사 DLT 로 가고, 뒤 편지는 기록된다(Review Focus 3)")
	void poisonGoesToAuditDlt() throws Exception {
		String goodId = UUID.randomUUID().toString();

		send("SID-E", "{not json");
		send("SID-E", event(goodId, "SID-E", "\"user-sub-0001\""));   // 같은 키 → poison 뒤에 선다

		assertThat(KafkaTestUtils.getSingleRecord(dltConsumer, DLT, Duration.ofSeconds(20)).value()).isEqualTo("{not json");
		awaitRow(goodId);
	}

	private void send(String key, String payload) throws Exception {
		kafkaTemplate.send(TOPIC, key, payload).get(5, TimeUnit.SECONDS);
	}

	private static String event(String eventId, String sid, String subJson) {
		return "{\"eventId\":\"" + eventId + "\",\"sid\":\"" + sid + "\",\"sub\":" + subJson
				+ ",\"occurredAt\":\"2026-09-28T01:02:03.456Z\"}";
	}

	private AuditEventEntity awaitRow(String eventId) {
		await().atMost(20, TimeUnit.SECONDS).until(() -> !rowsOf(eventId).isEmpty());
		return rowsOf(eventId).get(0);
	}

	private List<AuditEventEntity> rowsOf(String eventId) {
		return repository.findAll().stream().filter(row -> eventId.equals(row.getEventId())).toList();
	}
}
```

- [ ] **Step 4: 테스트가 컴파일 실패하는 것 확인**

```bash
cd audit
JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn \
  $JAVA_HOME/bin/java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon
```

Expected: `AuditEventConsumer` 가 없어 컴파일 에러.

- [ ] **Step 5: 소비자 작성**

`audit/src/main/java/dev/starryeye/audit/event/AuditEventConsumer.java`:

```java
package dev.starryeye.audit.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.audit.AuditRecord;
import dev.starryeye.audit.AuditRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventConsumer {

	/**
	 * 이벤트를 감사 레코드로 받아 적는다.
	 *
	 * 주의. event_type 은 페이로드가 아니라 수신한 토픽 이름에서 온다. 지금 페이로드에는 타입 필드가 없고,
	 *      토픽 이름이 곧 타입이다. 이렇게 하면 발행자(session)를 한 줄도 고치지 않고 이 소비자를 붙일 수 있다 —
	 *      그것이 이 슬라이스가 증명하려는 것이다. 토픽 이름을 버전(.v1)까지 그대로 넣으므로, 판단이 바뀌어
	 *      .v2 토픽이 생기면 감사 기록에서 두 계약이 자동으로 구분된다.
	 *
	 * 주의. 페이로드를 발행자의 record 타입으로 역직렬화하지 않고 JsonNode 로 읽는다. 감사는 이벤트마다
	 *      모양을 알 필요가 없고 공통 머리(eventId·sid·sub·occurredAt)만 뽑으면 된다. 원문은 받은 문자열을
	 *      그대로 저장하므로 발행자가 필드를 더해도 손실이 없다.
	 *
	 * 주의. 없는 필드는 null 로 넘긴다. sid 나 eventId 가 빠진 기형 이벤트는 기록기에서 not null 위반을 내고,
	 *      기록기는 그것을 흡수하지 않고 다시 던진다 — 그러면 컨테이너가 재시도하다 감사 DLT 로 보낸다.
	 *      여기서 기본값을 채우거나 예외를 삼키면 감사가 기록을 조용히 잃는다.
	 *
	 * 주의. 예외를 잡지 않는다(token-state 의 소비자와 같은 이유). 삼키면 처리하지 못한 이벤트의 오프셋이
	 *      커밋돼 그 기록이 영원히 사라진다.
	 *
	 * 주의. groupId 는 my.consumer-group-id 로 뺀다. 운영 "audit", 테스트 "audit-test" — token-state 의 그룹과
	 *      다르므로 두 소비자의 오프셋은 서로 독립이다. 감사가 멈춰도 폐기는 계속된다.
	 */

	public static final String LOGGED_OUT_TOPIC = "oidc.session.logged-out.v1";

	private final AuditRecorder recorder;
	private final ObjectMapper objectMapper;

	@KafkaListener(topics = LOGGED_OUT_TOPIC, groupId = "${my.consumer-group-id}")
	public void onEvent(@Payload String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws Exception {
		JsonNode node = objectMapper.readTree(payload);
		String occurredAt = text(node, "occurredAt");
		AuditRecord record = new AuditRecord(
				text(node, "eventId"),
				topic,
				occurredAt == null ? null : Instant.parse(occurredAt),
				text(node, "sid"),
				text(node, "sub"),
				payload);
		boolean recorded = recorder.record(record);
		log.debug("audit event: type={} eventId={} recorded={}", topic, record.eventId(), recorded);
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return (value == null || value.isNull()) ? null : value.asText();
	}
}
```

> `readTree("{not json")` 는 `JsonProcessingException` 을 던진다 → 재시도 → 감사 DLT. JSON 이지만 객체가 아닌 값(예: `[]`)이면 `node.get` 이 `null` 이라 모든 필드가 `null` 이 되고 → not null 위반 → 감사 DLT. 둘 다 조용히 사라지지 않는다.

- [ ] **Step 6: 토픽 선언과 DLT 설정 작성**

`audit/src/main/java/dev/starryeye/audit/event/KafkaConsumerConfig.java`:

```java
package dev.starryeye.audit.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

	/**
	 * 감사 소비자의 토픽 선언과 실패 처리.
	 *
	 * 주의. DLT 이름에 컨슈머 그룹(audit)을 넣는다. 소비자가 하나일 때는 "<토픽>.dlt" 가 모호하지 않았지만,
	 *      같은 토픽을 두 그룹이 소비하면 한 DLT 에 두 소비자의 실패가 섞여 "이 편지는 누가 처리 못 했나"를
	 *      알 수 없게 된다 — 재처리할 때 폐기를 다시 돌려야 할지 감사를 다시 돌려야 할지 모르는데, 한쪽은
	 *      멀쩡히 처리했다. 그래서 token-state 도 같은 슬라이스에서 "...v1.token-state.dlt" 로 이름을 바꾼다.
	 *
	 * 주의. DLT 목적지를 원래 토픽 이름에서 만든다(dltFor(record.topic())). 지금 구독하는 토픽은 하나지만,
	 *      후속 슬라이스에서 토픽이 늘면 각 토픽의 실패가 자기 이름의 DLT 로 간다. 상수 하나로 두면 새 토픽의
	 *      실패가 로그아웃 이름의 DLT 에 섞인다. 다만 브로커의 토픽 자동 생성이 꺼져 있으므로
	 *      (docker-compose.yml 의 KAFKA_AUTO_CREATE_TOPICS_ENABLE=false) 토픽을 더할 때는 그 DLT 의 NewTopic 도
	 *      반드시 함께 선언해야 한다. 없으면 DLT 발행이 실패한다.
	 *
	 * 주의. 본 토픽도 선언한다. 슬라이스 7에서 컨슈머가 토픽 생성 전에 구독하면 파티션 0에만 배정되는
	 *      기동 순서 함정을 겪어 session·token-state 가 둘 다 선언하게 됐고, 감사가 세 번째 선언자다.
	 *      KafkaAdmin 은 파티션을 늘리기만 하므로 선언이 어긋나도 줄어들지는 않지만, 세 곳(session 의
	 *      KafkaTopicConfig, token-state·audit 의 KafkaConsumerConfig)의 파티션 수·replicas 를 함께 맞춰야 한다.
	 *      이 선언의 효력은 기동 시점에 브로커가 응답 가능하다는 전제 위에서만 성립한다 — KafkaAdmin 은
	 *      브로커 미가용이면 로그만 남기고 재시도하지 않는다(token-state 의 KafkaConsumerConfig 주석 참고).
	 *
	 * 주의. 재시도 2회/200ms 후 DLT. 감사 DLT 에 간 편지는 아무도 자동으로 재처리하지 않는다 — 그 이벤트는
	 *      감사에서 빠진 채 남는다.
	 */

	public static String dltFor(String sourceTopic) {
		return sourceTopic + ".audit.dlt";
	}

	@Bean
	NewTopic sessionLoggedOutTopic() {
		return TopicBuilder.name(AuditEventConsumer.LOGGED_OUT_TOPIC).partitions(3).replicas(1).build();
	}

	@Bean
	NewTopic sessionLoggedOutAuditDlt() {
		return TopicBuilder.name(dltFor(AuditEventConsumer.LOGGED_OUT_TOPIC)).partitions(3).replicas(1).build();
	}

	@Bean
	DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
			@Value("${my.consumer-retry-attempts}") long retryAttempts,
			@Value("${my.consumer-retry-interval-ms}") long retryIntervalMs) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
				(record, exception) -> new TopicPartition(dltFor(record.topic()), -1));
		return new DefaultErrorHandler(recoverer, new FixedBackOff(retryIntervalMs, retryAttempts));
	}
}
```

- [ ] **Step 7: 테스트 통과 확인 — 브로커 유무 둘 다**

`docker ps` 로 로컬 Kafka 컨테이너 상태를 확인해 기록한 뒤 돌린다. **브로커가 없는 상태와 있는 상태에서 각각 한 번씩** 돌려 둘 다 통과하는지, 소요 시간이 얼마인지 기록한다(`AuditEventConsumerTest` 는 임베디드 브로커를 쓰므로 외부 브로커와 무관해야 한다).

```bash
docker ps --filter name=microservice-as --format '{{.Names}} {{.Status}}'
cd audit
JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn \
  $JAVA_HOME/bin/java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon
```

Expected: BUILD SUCCESSFUL. Task 1 의 7개 + 이 태스크의 6개.

- [ ] **Step 8: 테스트가 실제로 무는지 확인 — 두 변이**

**변이 A — `kafkaErrorHandler` 빈을 통째로 주석 처리한다.**

Expected: `missingSidGoesToAuditDlt`, `poisonGoesToAuditDlt` 의 **DLT 단언**이 실패한다(DLT 에 아무것도 오지 않는다).

그다음 **통제 실험을 하나 더 한다** — 변이 A 를 유지한 채 `poisonGoesToAuditDlt` 에서 DLT 단언 한 줄만 임시로 빼고 돌린다. 한 테스트 안에서는 DLT 단언이 먼저 실패해 뒤 단언까지 가지 않으므로, 이렇게 해야 뒤 편지 기록 단언을 따로 관찰할 수 있다. Expected: **뒤 편지 기록 단언은 통과한다** — Spring Boot 기본 에러 핸들러도 유한 재시도(9회/0ms) 뒤 로그만 남기고 다음으로 넘어가기 때문이다(슬라이스 7 Task 8 의 발견). 이 빈이 검증하는 것이 "DLT 로 간다"뿐이라는 사실을 보고서에 적어라.

**변이 B — 소비자에서 `topic` 대신 상수를 넣는다.** `AuditRecord` 생성의 두 번째 인자를 `"hardcoded"` 로 바꾼다.

Expected: `logoutEventIsRecorded` 가 실패한다(`event_type` 불일치).

각 변이의 `git diff`·실패 출력·인과를 보고서에 적고 원복한다.

- [ ] **Step 9: 커밋**

```bash
cd /Users/starryeye/study/spring-security
git status --porcelain oauth-2/authorization-server/practice/microservice/audit   # build/ 가 안 보여야 한다
git add oauth-2/authorization-server/practice/microservice/audit/build.gradle \
        oauth-2/authorization-server/practice/microservice/audit/src
git commit -m "$(cat <<'EOF'
audit: consume logout events as a second consumer group

같은 토픽에 두 번째 컨슈머 그룹을 붙인다. 발행자(session)는 한 줄도 바뀌지
않았다 — 슬라이스 7이 페이로드를 "일어난 사실"로 설계한 게 옳았다는 증거다.

- event_type 은 페이로드가 아니라 수신 토픽 이름에서, 버전(.v1)까지 그대로
- 페이로드를 발행자 타입이 아니라 JsonNode 로 읽고 원문은 받은 문자열 그대로
  저장 — 발행자가 필드를 더해도 손실이 없다
- sid·eventId 가 빠진 기형 이벤트는 기록기가 흡수하지 않아 재시도 후 감사
  DLT 로 간다
- DLT 에 컨슈머 그룹을 넣는다(...v1.audit.dlt). 한 토픽을 두 그룹이 소비하면
  그룹 없는 DLT 에 두 소비자의 실패가 섞인다

주의. 이 커밋부터 @SpringBootTest 가 리스너도 띄우므로, Kafka 와 무관한
AuditRecorderTest 는 리스너 자동 시작을 껐다 — 로컬 브로커가 떠 있으면 실제
이벤트를 테스트 h2 에 받아 적어 행 수 단언이 깨진다.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 3: `token-state` DLT 이름 변경과 슬라이스 7 잔여 정리

**Files:**
- Modify: `token-state/src/main/java/dev/starryeye/token_state/event/KafkaConsumerConfig.java`
- Modify: `token-state/src/main/java/dev/starryeye/token_state/jpa/RefreshTokenEntity.java` (주석만)
- Modify: `token-state/src/test/java/dev/starryeye/token_state/event/DeadLetterTopicTest.java` (주석만)
- Modify: `docs/superpowers/specs/2026-08-08-microservice-db-per-service-outbox-slice7-design.md`

**Interfaces:**
- Consumes: Task 2 의 감사 DLT 이름 규칙(`<토픽>.<그룹>.dlt`)
- Produces: `token-state` 의 `KafkaConsumerConfig.LOGGED_OUT_DLT = "oidc.session.logged-out.v1.token-state.dlt"`

이 태스크는 **동작 변경이 DLT 이름 하나뿐**이다. 나머지는 슬라이스 7이 park 해둔 주석 결함을, 같은 모듈을 건드리는 김에 닫는 것이다.

> **`session` 에 park 된 결함 하나(자동 생성 차단이 `OutboxPublisher` 쪽에 만든 60초 정지 미문서화)는 이 슬라이스에서 닫지 않는다.** `session/src` diff 0줄이 이 슬라이스의 핵심 증거라 주석 한 줄도 건드리지 않는다. 다음 슬라이스로 넘긴다.

- [ ] **Step 1: DLT 이름 변경**

`token-state/.../event/KafkaConsumerConfig.java` 의 상수를 바꾼다.

```java
	public static final String LOGGED_OUT_DLT = SessionLoggedOutConsumer.LOGGED_OUT_TOPIC + ".token-state.dlt";
```

같은 파일 javadoc 에서 `(oidc.session.logged-out.v1.dlt)` 로 적힌 곳을 `(oidc.session.logged-out.v1.token-state.dlt)` 로 고치고, 상수 바로 위 javadoc 끝에 다음 문단을 더한다.

```java
	 * 주의. DLT 이름에 컨슈머 그룹(token-state)을 넣는다. 슬라이스 7에서는 이 토픽의 소비자가 하나뿐이라
	 *      "<토픽>.dlt" 가 모호하지 않았다. 슬라이스 8에서 감사(audit)가 같은 토픽을 두 번째 그룹으로 소비하면서,
	 *      그룹 없는 DLT 에는 두 소비자의 실패가 섞여 "누가 처리 못 했나"를 알 수 없게 됐다. 그래서 두 소비자
	 *      모두 "<토픽>.<그룹>.dlt" 를 쓴다. 옛 토픽(oidc.session.logged-out.v1.dlt)은 볼륨을 지우지 않은 환경에서는
	 *      브로커에 남아 쓰이지 않는다.
```

`DeadLetterTopicTest` 는 이 상수를 참조하므로 코드 변경이 필요 없다.

- [ ] **Step 2: 슬라이스 7 잔여 — `RefreshTokenEntity.sid` 주석**

현재 주석은 "nullable 인 이유는 client_credentials 하나뿐이다 — 그 grant 는 애초에 refresh token 을 내지 않으므로 이 컬럼을 채울 행 자체가 생기지 않는다" 류로 적혀 있어 **자기모순**이다(행이 안 생긴다면 그 grant 는 nullable 의 이유가 될 수 없다). 그리고 **실제로 도달 가능한 null-sid 경로를 빠뜨렸다** — `token` 의 `TokenEndpointController` 는 code 레코드의 `sid` 를 게이트 없이 그대로 넘기므로, `sid` 가 없는 구버전 code 레코드(롤링 배포 스큐)로 `offline_access` 를 교환하면 `sid = null` 행이 생긴다. 기존 테스트 `TokenEndpointControllerTest` 의 `offline_access` 테스트들이 정확히 그 조합(`sid = null`)을 돌린다. README 한계 목록에도 "`sid` 가 `null` 인 옛 `refresh_tokens` 행"이 별도 항목으로 있다.

`sid` 필드 위 javadoc 을 아래로 **통째로 교체**한다.

```java
	/**
	 * 이 refresh token 이 속한 OP 세션이다. 로그아웃 폐기의 범위를 세션 단위로 잡기 위해 보관한다.
	 *
	 * 주의. 정상 경로에서는 항상 채워진다. auth 의 AuthorizeController 가 openid 유무와 무관하게 code 에 sid 를
	 *      싣고, token 의 TokenEndpointController 가 그 값을 token-state 의 issue 요청에 그대로 넘긴다 —
	 *      openid 없이 offline_access 만 받은 경로도 sid 를 갖고 세션 단위 폐기에 걸린다.
	 *      client_credentials 는 refresh token 을 내지 않으므로 이 컬럼에 행을 만들지 않는다 — nullable 의
	 *      이유가 아니다.
	 *
	 * 주의. nullable 인 이유는 sid 가 없는 code 레코드다. code 계약에 sid 가 들어가기 전(슬라이스 5 이전)의
	 *      auth 가 발급한 code 를 롤링 배포 중에 새 token 이 교환하면, TokenEndpointController 는 그 null 을
	 *      거르지 않고 그대로 넘긴다(같은 메서드에서 RP 세션 등록은 StringUtils.hasText 로 거르지만 refresh 발급은
	 *      거르지 않는다). 그렇게 생긴 sid = null 행은 WHERE sid = ? 에 걸리지 않아 로그아웃 폐기에서 빠진다.
	 *      TokenEndpointControllerTest 의 offline_access 테스트들이 이 조합(sid = null)을 실제로 돈다.
	 */
```

**쓰기 전에 세 가지를 직접 확인하고 보고서에 파일·줄을 적어라** — (1) `TokenEndpointController` 의 refresh 발급 호출이 `data.sid()` 를 거르지 않고 넘기는 줄과, 같은 메서드의 `sessionClient.register` 가 `StringUtils.hasText(data.sid())` 로 거르는 줄, (2) `TokenEndpointControllerTest` 에서 `offline_access` 를 `sid = null` 인 code 로 도는 테스트, (3) code 계약에 `sid` 가 추가된 것이 슬라이스 5 라는 사실(`git log` 로). 셋 중 하나라도 위 문장과 다르면 **문장을 사실에 맞게 고치고** 무엇이 달랐는지 보고해라.

- [ ] **Step 3: 슬라이스 7 잔여 — `DeadLetterTopicTest` 브로커 클래스 이름**

클래스 주석이 `EmbeddedKafkaKraftBroker(InitializingBean, ...)` 라고 적는데, 슬라이스 7 재리뷰어가 spring-kafka-test 3.3.x 의 `@EmbeddedKafka.kraft()` 기본값이 `false` 임을 바이트코드로 확인했고 실행 로그도 ZooKeeper 기반이었다 — 실제로 쓰이는 것은 `EmbeddedKafkaZKBroker` 다. **직접 확인한 뒤**(소스·실행 로그 중 하나로) 이름을 고쳐라. 순서 논증(`InitializingBean` 이 `SmartInitializingSingleton` 보다 먼저)은 두 구현 모두 성립하므로 그대로 둔다.

- [ ] **Step 4: 슬라이스 7 설계 문서 정리**

`docs/superpowers/specs/2026-08-08-microservice-db-per-service-outbox-slice7-design.md` 에서 두 곳을 고친다.

1. 5절(소비 실패 — DLT)에서 `oidc.session.logged-out.v1.dlt` 를 언급하는 곳 바로 뒤에 한 줄을 덧붙인다: `> 슬라이스 8에서 두 번째 소비자(audit)가 붙으면서 이 이름은 oidc.session.logged-out.v1.token-state.dlt 로 바뀌었다 — 그룹 없는 DLT 는 소비자가 둘이 되면 모호해진다.` 원래 문장은 지우지 않는다(슬라이스 7 당시의 기록이다).
2. 8절에서 `아래 §6` 처럼 §6 을 "아래"로 가리키는 교차 참조를 찾아 방향을 바로잡는다(§6 은 §8 보다 위에 있다).

- [ ] **Step 5: 테스트**

```bash
docker ps --filter name=microservice-as --format '{{.Names}} {{.Status}}'
cd token-state
JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn \
  $JAVA_HOME/bin/java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon
```

Expected: BUILD SUCCESSFUL. `DeadLetterTopicTest` 가 새 이름의 DLT 로 통과한다.

- [ ] **Step 6: 변이 확인**

`DeadLetterTopicTest` 의 `@EmbeddedKafka(topics = ...)` 에서 `KafkaConsumerConfig.LOGGED_OUT_DLT` 대신 옛 이름 문자열 `"oidc.session.logged-out.v1.dlt"` 을 직접 넣고, DLT 구독(`consumeFromAnEmbeddedTopic`)도 옛 이름으로 바꿔 돌린다.

Expected: DLT 단언이 실패한다 — 에러 핸들러는 새 이름으로 보내는데 테스트는 옛 이름을 보고 있으므로. 이것이 이름 변경이 실제로 목적지를 바꿨다는 증거다. `git diff`·출력을 남기고 원복한다.

- [ ] **Step 7: `session` 무변경 확인과 커밋**

```bash
cd /Users/starryeye/study/spring-security
git diff --stat 03ded07 -- 'oauth-2/authorization-server/practice/microservice/session/src/*'   # 비어 있어야 한다
git add oauth-2/authorization-server/practice/microservice/token-state/src/main/java/dev/starryeye/token_state/event/KafkaConsumerConfig.java \
        oauth-2/authorization-server/practice/microservice/token-state/src/main/java/dev/starryeye/token_state/jpa/RefreshTokenEntity.java \
        oauth-2/authorization-server/practice/microservice/token-state/src/test/java/dev/starryeye/token_state/event/DeadLetterTopicTest.java \
        oauth-2/authorization-server/practice/microservice/docs/superpowers/specs/2026-08-08-microservice-db-per-service-outbox-slice7-design.md
git commit -m "$(cat <<'EOF'
token-state: qualify the DLT with its consumer group

소비자가 하나일 때 oidc.session.logged-out.v1.dlt 는 모호하지 않았다. 감사가
같은 토픽을 두 번째 그룹으로 소비하면서 그룹 없는 DLT 에는 두 소비자의
실패가 섞이게 됐다 — 재처리할 때 폐기를 다시 돌려야 할지 감사를 다시 돌려야
할지 모르는데, 한쪽은 멀쩡히 처리했다. ...v1.token-state.dlt 로 바꾼다.

같은 모듈을 건드리는 김에 슬라이스 7이 park 한 주석 결함 셋을 닫았다.
- RefreshTokenEntity.sid 주석의 자기모순과 빠진 실제 null-sid 경로
- DeadLetterTopicTest 가 지목한 브로커 클래스 이름(Kraft → ZK)
- 슬라이스 7 설계 문서의 DLT 이름 후속 표시와 교차 참조 방향

session 에 park 된 하나는 닫지 않았다 — session/src diff 0줄이 이
슬라이스의 핵심 증거다.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 4: e2e 검증, 재현 스크립트, 문서

**Files:**
- Create: `http/e2e-login-logout.sh`
- Modify: `docker-compose/docker-compose.yml` (헤더, 교차 참조)
- Modify: `README.md`

**Interfaces:**
- Consumes: Task 1~3 전부
- Produces: 없음

**이 태스크의 산출물은 실제로 실행해서 얻은 출력이다.** README 에는 실측만 옮긴다. 실행하지 못한 기준은 못 했다고 적는다 — 억지로 통과시키지 마라.

- [ ] **Step 1: 재현 스크립트 작성**

슬라이스 7 의 e2e 는 로그인 흐름을 손으로 짠 스크립트로 돌렸는데 커밋하지 않아, 최종 리뷰가 "재현하려면 스크립트를 다시 짜야 한다"고 지적했다. 이번엔 커밋한다.

`http/e2e-login-logout.sh`:

```bash
#!/usr/bin/env bash
# 로그인 → 인가(PKCE) → 동의 → code 교환 → (선택) 로그아웃 을 curl 로 재현한다. 모든 요청은 gateway(:9000).
#
# 사용:
#   ./e2e-login-logout.sh login  <jar>   # 로그인하고 토큰을 받는다. refresh_token 과 sid 를 출력한다
#   ./e2e-login-logout.sh logout <jar>   # 그 쿠키 항아리의 세션으로 로그아웃한다
#
# 주의. 로그인 redirect 의 Location 이 게이트웨이 포트(:9000)를 잃는 알려진 한계(README 참고)가 있어,
#      redirect 를 따라가지 않고(-L 없음) 매번 절대 URL 로 다음 요청을 보낸다.
# 주의. 로그인 폼의 _csrf, 동의 화면의 pending_id·_csrf 는 응답 본문에서 매번 새로 읽는다.
set -euo pipefail

BASE=http://localhost:9000
CMD=${1:?login|logout}
JAR=${2:?cookie jar path}
REDIRECT_URI=http://127.0.0.1:8080/callback
BASIC=bXktY2xpZW50OnNlY3JldA==   # my-client:secret (seed)

csrf() { grep -o 'name="_csrf"[^>]*value="[^"]*"' | sed -E 's/.*value="([^"]*)".*/\1/' | head -1; }

if [ "$CMD" = "logout" ]; then
  curl -s -o /dev/null -w '%{http_code}\n' -b "$JAR" -c "$JAR" "$BASE/oauth2/logout"
  exit 0
fi

rm -f "$JAR"

# 1. 로그인
TOKEN=$(curl -s -b "$JAR" -c "$JAR" "$BASE/login" | csrf)
curl -s -o /dev/null -b "$JAR" -c "$JAR" -X POST "$BASE/login" \
  --data-urlencode "username=user" --data-urlencode "password=1111" --data-urlencode "_csrf=$TOKEN"

# 2. 인가 요청 (PKCE S256)
VERIFIER=$(openssl rand -base64 48 | tr -d '=+/\n' | cut -c1-64)
CHALLENGE=$(printf '%s' "$VERIFIER" | openssl dgst -sha256 -binary | openssl base64 | tr '+/' '-_' | tr -d '=\n')
AUTHZ="$BASE/oauth2/authorize?response_type=code&client_id=my-client&redirect_uri=$REDIRECT_URI"
AUTHZ="$AUTHZ&scope=openid%20profile%20email%20offline_access&state=s1&nonce=n1"
AUTHZ="$AUTHZ&code_challenge=$CHALLENGE&code_challenge_method=S256"
RESP=$(curl -s -i -b "$JAR" -c "$JAR" "$AUTHZ")

# 3. 동의 화면이 나오면 제출한다(이미 같은 scope 를 승인한 세션이면 바로 302 로 code 가 온다)
LOCATION=$(printf '%s' "$RESP" | grep -i '^location:' | tr -d '\r' | sed 's/^[Ll]ocation: //')
if [ -z "$LOCATION" ]; then
  PENDING=$(printf '%s' "$RESP" | grep -o 'name="pending_id"[^>]*value="[^"]*"' | sed -E 's/.*value="([^"]*)".*/\1/')
  CTOKEN=$(printf '%s' "$RESP" | csrf)
  LOCATION=$(curl -s -i -b "$JAR" -c "$JAR" -X POST "$BASE/oauth2/consent" \
      --data-urlencode "pending_id=$PENDING" --data-urlencode "_csrf=$CTOKEN" \
      --data-urlencode "scope=openid" --data-urlencode "scope=profile" \
      --data-urlencode "scope=email" --data-urlencode "scope=offline_access" \
    | grep -i '^location:' | tr -d '\r' | sed 's/^[Ll]ocation: //')
fi
CODE=$(printf '%s' "$LOCATION" | sed -E 's/.*[?&]code=([^&]*).*/\1/')

# 4. code 교환
TOKENS=$(curl -s -X POST "$BASE/oauth2/token" -H "Authorization: Basic $BASIC" \
  --data-urlencode "grant_type=authorization_code" --data-urlencode "code=$CODE" \
  --data-urlencode "redirect_uri=$REDIRECT_URI" --data-urlencode "code_verifier=$VERIFIER")
REFRESH=$(printf '%s' "$TOKENS" | python3 -c 'import sys,json; print(json.load(sys.stdin)["refresh_token"])')
ID_TOKEN=$(printf '%s' "$TOKENS" | python3 -c 'import sys,json; print(json.load(sys.stdin)["id_token"])')
SID=$(printf '%s' "$ID_TOKEN" | cut -d. -f2 | tr '_-' '/+' | python3 -c \
  'import sys,base64,json; s=sys.stdin.read().strip(); s+="="*(-len(s)%4); print(json.loads(base64.b64decode(s))["sid"])')

echo "refresh_token=$REFRESH"
echo "sid=$SID"
```

```bash
chmod +x http/e2e-login-logout.sh
```

> **이 스크립트는 계획 작성 시점에 실행해 본 것이 아니다.** 로그인 폼(Spring 기본 폼)과 동의 폼(`pending_id`, `scope`)의 필드 이름은 소스에서 확인했지만, 실제로 돌려 보고 다른 곳이 있으면 **실제에 맞게 고치고 무엇이 달랐는지 보고서에 적어라.**

- [ ] **Step 2: 전체 기동 — `audit` 은 나중에**

성공 기준 3(소급)을 보이려고 `audit` 을 **빼고** 먼저 띄운다.

```bash
cd oauth-2/authorization-server/practice/microservice
docker compose -p microservice-as -f docker-compose/docker-compose.yml down -v
docker compose -p microservice-as -f docker-compose/docker-compose.yml up -d
sleep 20
docker ps --filter name=microservice-as --format '{{.Names}} {{.Status}}'
export JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn
for m in signing user-directory client-registry consent session token-state token auth audit; do
  (cd $m && $JAVA_HOME/bin/java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain bootJar --no-daemon -q)
done
for m in signing user-directory client-registry consent session token-state token auth; do
  (cd $m && nohup $JAVA_HOME/bin/java -jar build/libs/$m-0.0.1-SNAPSHOT.jar > /tmp/$m.log 2>&1 &)
  sleep 8
done
```

- [ ] **Step 3: 기준 1 — 분리**

Task 1 Step 11 의 권한 오류 명령 두 개를 다시 실행해 출력을 기록한다.

- [ ] **Step 4: 기준 3 — `audit` 이 없을 때 일어난 로그아웃을 소급해 기록한다**

```bash
http/e2e-login-logout.sh login /tmp/jar-a      # sid 를 기록해 둔다 (SID_A)
http/e2e-login-logout.sh logout /tmp/jar-a
sleep 2
(cd audit && nohup $JAVA_HOME/bin/java -jar build/libs/audit-0.0.1-SNAPSHOT.jar > /tmp/audit.log 2>&1 &)
sleep 20
docker exec -i microservice-as-mysql-1 mysql -usvc_audit -ppw_audit -e \
  "SELECT event_id, event_type, subject_sid, reported_sub, occurred_at, recorded_at FROM ms_audit.audit_events"
```

Expected: `audit` 을 띄우기 **전에** 일어난 `SID_A` 의 로그아웃이 한 줄로 기록돼 있다. `recorded_at` 이 `occurred_at` 보다 수십 초 뒤인 것이 소급의 증거다.

- [ ] **Step 5: 기준 2 — 여섯 컬럼이 이벤트와 일치한다**

`session` 의 outbox 원문과 감사 레코드를 나란히 조회한다.

```bash
docker exec -i microservice-as-mysql-1 mysql -usvc_session -ppw_session -e \
  "SELECT event_id, partition_key, payload FROM ms_session.outbox ORDER BY id"
docker exec -i microservice-as-mysql-1 mysql -usvc_audit -ppw_audit -e \
  "SELECT event_id, subject_sid, reported_sub, payload FROM ms_audit.audit_events ORDER BY id"
```

Expected: 같은 `event_id`, `payload` 가 **바이트 단위로 같다**, `subject_sid` = outbox 의 `partition_key`.

- [ ] **Step 6: 기준 7·9 — `audit` 을 내려도 폐기는 되고, 올리면 따라잡는다**

```bash
pkill -f 'audit-0.0.1-SNAPSHOT.jar'; sleep 3
http/e2e-login-logout.sh login /tmp/jar-b       # SID_B, REFRESH_B 기록
http/e2e-login-logout.sh logout /tmp/jar-b
sleep 2
# 폐기는 됐다
docker exec -i microservice-as-mysql-1 mysql -usvc_token_state -ppw_token_state -e \
  "SELECT sid, status, revoked_reason FROM ms_token_state.refresh_tokens WHERE sid='<SID_B>'"
# 두 그룹의 오프셋이 따로 움직인다 — audit 만 lag 가 있다
docker exec -i microservice-as-kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group token-state
docker exec -i microservice-as-kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group audit
# audit 을 올리면 따라잡는다
(cd audit && nohup $JAVA_HOME/bin/java -jar build/libs/audit-0.0.1-SNAPSHOT.jar > /tmp/audit.log 2>&1 &)
sleep 20
docker exec -i microservice-as-mysql-1 mysql -usvc_audit -ppw_audit -e \
  "SELECT subject_sid FROM ms_audit.audit_events WHERE subject_sid='<SID_B>'"
docker exec -i microservice-as-kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group audit
```

Expected: `SID_B` 는 `REVOKED/SESSION_LOGGED_OUT`. `audit` 이 내려가 있는 동안 `audit` 그룹의 `LAG` 는 0이 아니고 `token-state` 그룹은 0. 재기동 후 `SID_B` 의 감사 레코드가 생기고 `audit` 의 `LAG` 가 0이 된다.

- [ ] **Step 7: 기준 8 — `token-state` 를 내려도 감사는 기록한다**

```bash
pkill -f 'token-state-0.0.1-SNAPSHOT.jar'; sleep 3
http/e2e-login-logout.sh login /tmp/jar-c       # SID_C 기록
http/e2e-login-logout.sh logout /tmp/jar-c
sleep 3
docker exec -i microservice-as-mysql-1 mysql -usvc_audit -ppw_audit -e \
  "SELECT subject_sid FROM ms_audit.audit_events WHERE subject_sid='<SID_C>'"
docker exec -i microservice-as-mysql-1 mysql -usvc_token_state -ppw_token_state -e \
  "SELECT sid, status FROM ms_token_state.refresh_tokens WHERE sid='<SID_C>'"
(cd token-state && nohup $JAVA_HOME/bin/java -jar build/libs/token-state-0.0.1-SNAPSHOT.jar > /tmp/token-state.log 2>&1 &)
sleep 20
docker exec -i microservice-as-mysql-1 mysql -usvc_token_state -ppw_token_state -e \
  "SELECT sid, status, revoked_reason FROM ms_token_state.refresh_tokens WHERE sid='<SID_C>'"
```

Expected: `token-state` 가 내려가 있는 동안에도 `SID_C` 의 감사 레코드는 생긴다. 그 순간 refresh 는 아직 `ACTIVE`. `token-state` 를 올리면 밀린 이벤트를 소비해 `REVOKED` 가 된다.

- [ ] **Step 8: 기준 10 — 한 편지의 실패가 각자의 DLT 로 간다**

JSON 이 아닌 편지 하나를 본 토픽에 넣는다. 두 소비자가 **각자** 실패하고 **각자의** DLT 로 보내야 한다.

```bash
echo 'e2e-poison:{not json' | docker exec -i microservice-as-kafka-1 /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic oidc.session.logged-out.v1 \
  --property parse.key=true --property key.separator=:
sleep 5
for t in oidc.session.logged-out.v1.token-state.dlt oidc.session.logged-out.v1.audit.dlt; do
  echo "== $t"
  docker exec -i microservice-as-kafka-1 /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 --topic "$t" --from-beginning --timeout-ms 5000 2>/dev/null
done
```

Expected: 두 DLT 에 `{not json` 이 **한 건씩** 있다. 그룹 없는 옛 이름(`...v1.dlt`)은 `down -v` 로 시작했으므로 존재하지 않아야 한다(`kafka-topics.sh --list` 로 확인).

- [ ] **Step 9: 기준 6 — 발행자 무변경**

```bash
cd /Users/starryeye/study/spring-security
git diff --stat 03ded07 -- 'oauth-2/authorization-server/practice/microservice/session/src/*'
```

Expected: **출력 없음.** 이 슬라이스의 커밋 전부에서 `session/src` 가 한 줄도 바뀌지 않았다.

- [ ] **Step 10: 기준 4·5 는 테스트로 확인됐음을 명시한다**

멱등(4)과 흡수하지 않는 위반(5)은 e2e 로 재현하기 어려워(중복 전달을 인위적으로 만들어야 한다) Task 1·2 의 테스트가 증명한다. README 에 **그 테스트 이름과, e2e 로는 확인하지 않았다는 사실**을 함께 적는다.

- [ ] **Step 11: `docker-compose.yml` 정리**

1. 헤더의 `9개 Spring 서비스(auth·client-registry·consent·demo-rp·session·signing·token·token-state·user-directory)` 를 `audit` 을 더한 **10개**로 고친다.
2. kafka 블록의 주석에서 `(아래 "어느 서비스가 먼저 떠도 파티션 3개가 보장된다" 주석의 전제가 깨진 경우)` 처럼 "아래"로 가리키는 교차 참조를 찾아 바로잡는다 — 그 주석은 이 파일 아래가 아니라 `token-state` 의 `KafkaConsumerConfig` 에 있다(슬라이스 7 잔여).

- [ ] **Step 12: README 갱신**

아래를 추가·수정한다. **실측으로 얻은 출력만 옮긴다.**

1. **서비스별 책임과 소유 데이터** 표에 `audit` 행을 `session` 행 아래에 넣는다: 포트 `8089`, 책임 "로그아웃 이벤트를 두 번째 컨슈머 그룹(`audit`)으로 소비해 공통 머리 + 원문으로 append-only 기록, `eventId` 멱등, 소비 실패는 `...v1.audit.dlt` — 발행·REST API 없음", 스키마 `ms_audit`, 계정 `svc_audit`, 소유 데이터 `audit_events (MySQL)`. 표 아래 문단의 "5개 서비스" 표현이 있으면 6개로 고친다.
2. **기동 방법** 2번(빌드 목록)과 3번(기동 목록)에 `audit` 을 넣는다. 3번 목록 끝에 `java -jar audit/build/libs/*.jar  # 8089 — 언제 띄워도 된다(earliest 로 소급)` 를 더한다.
3. **검증된 성공 기준** 에 `### 슬라이스 8` 절을 만들고 Step 3~10 의 명령과 출력을 그대로 옮긴다. 기준 4·5 는 테스트 이름으로 적고 "e2e 로는 확인하지 않았다"를 명시한다.
4. **알려진 한계** 에 스펙 9절의 일곱 항목을 옮긴다.
5. **설계/계획 문서** 절에 슬라이스 8 설계·계획 링크 두 줄을 더한다.

- [ ] **Step 13: 전체 회귀**

```bash
pkill -f 'build/libs/.*-0.0.1-SNAPSHOT.jar' || true
cd oauth-2/authorization-server/practice/microservice
docker compose -p microservice-as -f docker-compose/docker-compose.yml down
docker ps --filter name=microservice-as --format '{{.Names}}'   # 비어 있음을 기록
export JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn
for m in audit auth client-registry consent demo-rp session signing token-state token user-directory; do
  (cd $m && $JAVA_HOME/bin/java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon > /tmp/test-$m.log 2>&1) \
    && echo "PASS $m" || echo "FAIL $m"
done
```

Expected: 10개 모듈 전부 PASS. 테스트 총수를 결과 XML 에서 세어 기록한다.

- [ ] **Step 14: 커밋**

```bash
cd /Users/starryeye/study/spring-security
git add oauth-2/authorization-server/practice/microservice/http/e2e-login-logout.sh \
        oauth-2/authorization-server/practice/microservice/docker-compose/docker-compose.yml \
        oauth-2/authorization-server/practice/microservice/README.md
git commit -m "$(cat <<'EOF'
microservice: verify slice 8 end to end and document it

성공 기준을 실제로 실행해 확인했다. README 에는 실행해서 얻은 출력만 옮겼다.

- 감사를 띄우기 전에 일어난 로그아웃도 소급해 기록된다(earliest)
- 감사 레코드의 event_id·payload 가 session outbox 원문과 바이트 단위로 같다
- 감사를 내려도 폐기는 되고, 올리면 따라잡는다 — 두 그룹의 lag 가 따로 움직인다
- token-state 를 내려도 감사는 기록한다
- poison 편지 하나가 각 그룹의 DLT 로 한 건씩 간다
- session/src diff 0줄 — 발행자를 안 고치고 두 번째 소비자가 붙었다

슬라이스 7 e2e 는 로그인 흐름 스크립트를 커밋하지 않아 재현이 어려웠다.
이번엔 http/e2e-login-logout.sh 로 남긴다.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## 부록: 계획 자체 검토

**스펙 커버리지**

| 스펙 | 태스크 |
|---|---|
| §2 서비스·스키마·계정 | Task 1 Step 1·10·11 |
| §3 공통 머리 + 원문, `event_type` 토픽 유래, `sid` 사실·`sub` 전문, `subject_sid` not null | Task 1 Step 6, Task 2 Step 5 |
| §4 멱등(재확인 흡수, `@Transactional` 없음), `INSERT IGNORE` 기각 | Task 1 Step 7 |
| §5 발행자 무변경 | Task 3 Step 7, Task 4 Step 9 |
| §5 DLT 그룹 단위, `token-state` 이름 변경 | Task 2 Step 6, Task 3 Step 1 |
| §5 세 번째 토픽 선언자 | Task 2 Step 6 |
| §5 슬라이스 7 테스트 교훈 적용 | Task 2 Step 1·2·3 |
| §6 성공 기준 1~10 | 1: T1 S11·T4 S3 / 2: T1 S3·T4 S5 / 3: T4 S4 / 4·5: T1 S3·T2 S3 / 6: T4 S9 / 7·9: T4 S6 / 8: T4 S7 / 10: T4 S8 |
| §9 한계 | Task 4 Step 12 |

**설계와 달라진 점**

- 스펙 §5 는 감사 DLT 를 `oidc.session.logged-out.v1.audit.dlt` 하나로 적었다. 계획은 목적지를 **원래 토픽 이름에서 만든다**(`dltFor(record.topic())`). 지금 값은 스펙과 같지만, 후속 슬라이스에서 토픽이 늘 때 실패가 로그아웃 이름의 DLT 에 섞이지 않는다. Task 2 가 설계 문서 §5 에 이 사실을 한 줄 덧붙일 필요는 없다 — 결과 값이 같고, 일반화는 후속 슬라이스가 토픽을 더할 때 드러난다. 다만 그 슬라이스에서 **DLT 의 `NewTopic` 을 함께 선언해야 한다**는 전제를 `KafkaConsumerConfig` 주석에 남겼다.

**추정으로 남긴 것 (구현자가 확인하고 보고할 것)**

- Task 1 Step 8: not null 위반이 Hibernate 검사에서 나는지 DB 에서 나는지
- Task 3 Step 3: `EmbeddedKafkaZKBroker` 가 실제 쓰이는 구현인지
- Task 4 Step 1: 재현 스크립트가 실제 폼과 맞는지
