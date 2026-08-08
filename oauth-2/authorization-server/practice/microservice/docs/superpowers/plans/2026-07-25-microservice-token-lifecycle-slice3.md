# 마이크로서비스 인가 서버 슬라이스 3 — 토큰 수명 관리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** refresh token 회전과 재사용 탐지, introspection, revocation 을 추가해 이 인가 서버에 토큰 수명 관리를 만든다.

**Architecture:** `token-state`(8087) 를 신설해 refresh token 계열(family)과 폐기 상태를 소유하게 한다. 회전은 "조회 → 판단 → 갱신"을 네트워크로 쪼개지 않고 **한 번의 호출(`/rotate`)로 표현**하며, 정확성은 행 잠금(`PESSIMISTIC_WRITE`)이 만든다. token(8082)은 무상태를 유지한 채 REST 로 위임하고 `/oauth2/introspect` · `/oauth2/revoke` 를 호스팅한다.

**Tech Stack:** Java 21, Spring Boot 3.4.5, Spring Data JPA, MySQL, nimbus-jose-jwt, nginx

## Global Constraints

- Java 21 (gradle toolchain). 로컬 `java -jar` 는 `/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn/bin/java` 사용 (PATH java 는 17).
- Spring Boot **3.4.5**, io.spring.dependency-management **1.1.7**, gradle wrapper 8.13. 버전 하드코딩 오타 주의(전 서비스 동일해야 함).
- **SAS starter(`spring-boot-starter-oauth2-authorization-server`) 금지.** OAuth/OIDC 로직은 직접 구현.
- gradle 명령은 반드시 `--no-daemon` (이 환경은 gradle 데몬이 SIGKILL 되는 이슈가 있음). `./gradlew` 가 exit 137 이면 우회: `/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn/bin/java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon --rerun-tasks`
- 새 서비스 디렉토리에는 **`.gitignore`·gradle wrapper 를 반드시 복사**한다(`cp -r consent/.gitignore consent/gradlew consent/gradlew.bat consent/gradle consent/settings.gradle <new>/`). 누락하면 `build/`·`.gradle/` 산출물이 커밋된다.
- 패키지 `dev.starryeye.<service_name>`(underscore), 메인 클래스 PascalCase + `Application`.
- 위치: `oauth-2/authorization-server/practice/microservice/<service>/`.
- 포트: gateway 9000, auth 8081, token 8082, signing 8083, user-directory 8084, client-registry 8085, consent 8086, **token-state 8087**.
- MySQL `jdbc:mysql://localhost:3306/microservice_as` root/1111, Redis localhost:6379.
- 테스트에서 mock 빈은 `@MockitoBean` 사용(`@MockBean` 은 Boot 3.4 deprecated — 출력 pristine 유지).
- cross-service 수신 record 에는 `@JsonIgnoreProperties(ignoreUnknown = true)` 를 붙인다.
- 주석: 클래스 설명 javadoc 은 **클래스 바디 안**(여는 중괄호 아래). 경험담 서술 금지 — 함정은 "주의." 항목으로 현재형 지식 서술.
- DB 저장은 comma 구분, OAuth 와이어 포맷은 공백 구분. 변환은 경계에서 한 번만.
- 커밋 메시지 말미: `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`

## 공유 계약 (이 슬라이스에서 신규)

```
token-state 서비스 (신규, 8087, 내부 전용)
  POST /internal/refresh-tokens
       { clientId, sub, scope, authTime }
       200 { refreshToken, expiresAt, familyId }

  POST /internal/refresh-tokens/rotate
       { refreshToken, clientId }
       200 { status, sub, scope, authTime, refreshToken, expiresAt }
       status ∈ ROTATED | REUSE_DETECTED | REVOKED | EXPIRED | NOT_FOUND | CLIENT_MISMATCH

  POST /internal/refresh-tokens/revoke
       { refreshToken, clientId }
       200 { revoked: bool }

  POST /internal/refresh-tokens/introspect
       { refreshToken }
       200 { active, sub, clientId, scope, exp, iat }

gateway 신규 외부 경로 (둘 다 token 으로)
  POST /oauth2/introspect
  POST /oauth2/revoke
```

**scope 표기 규약** — token-state 의 **API 는 공백 구분**(OAuth 와이어 포맷과 동일), **DB 는 comma 구분**. 변환은 `RefreshTokenService` 안에서 한다.

## File Structure

**신규 서비스 `token-state/`**

| 파일 | 책임 |
|---|---|
| `TokenStateApplication.java` | 부트 진입점 |
| `jpa/RefreshTokenEntity.java` | 행 하나 = refresh token 하나. 상태 전이 메서드 보유 |
| `jpa/RefreshTokenStatus.java` | ACTIVE / CONSUMED / REVOKED |
| `jpa/RefreshTokenEntityRepository.java` | 해시 조회(잠금) · 계열 조회 |
| `TokenGenerator.java` | 난수 토큰 생성 + SHA-256 해시. 순수 함수라 단독 테스트 |
| `RefreshTokenService.java` | 발급 · 회전 · 폐기 · 조회의 **트랜잭션 경계와 판정** |
| `RefreshTokenController.java` | 내부 API 4개. 판정은 하지 않고 위임만 |
| `dto/*.java` | 요청 · 응답 record |

**token 서비스 변경**

| 파일 | 책임 |
|---|---|
| `ClientAuthenticator.java` (신규) | Basic 파싱 + client 조회 + secret 대조. 세 엔드포인트가 공유 |
| `client/TokenStateClient.java` (신규) | token-state 호출 |
| `client/IssuedRefreshToken.java` · `RotateResult.java` · `RefreshTokenInfo.java` (신규) | 수신 record |
| `RefreshTokenGrantService.java` (신규) | refresh grant 의 판정과 조립 |
| `IntrospectionController.java` (신규) | `/oauth2/introspect` |
| `RevocationController.java` (신규) | `/oauth2/revoke` |
| `TokenEndpointController.java` (수정) | grant 분기, code 교환 시 refresh 발급, ClientAuthenticator 사용, discovery |
| `dto/TokenResponse.java` (수정) | `refresh_token` 추가 |
| `AccessTokenVerifier.java` (수정) | `VerifiedToken` 에 introspection 이 필요한 claim 추가 |

**그 외** — `client-registry/ClientSeedInitializer.java`(seed), `gateway/nginx.conf`(라우팅), `README.md`, `http/token-lifecycle.http`

## auth 서비스는 변경하지 않는다

`offline_access` 는 auth 입장에서 **다른 scope 와 완전히 동일**하다. `AuthorizeController` 는 요청 scope 를 client 허용 목록과 대조하고 미승인분을 동의 화면에 렌더할 뿐, scope 이름을 특별 취급하지 않는다. 따라서 client seed 의 `scopes` 에 `offline_access` 를 넣는 것만으로 동의 화면에 체크박스가 뜨고 code 에 실린다.

**auth 에 코드 변경이 필요하다고 판단되면 그것은 신호다** — 어딘가에 scope 이름을 하드코딩했다는 뜻이므로 그 지점을 먼저 확인하라.

---

## Task 1: token-state 서비스 스캐폴딩 + 엔티티

**Files:**
- Create: `microservice/token-state/build.gradle`, `settings.gradle`, `.gitignore`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*` (consent 에서 복사)
- Create: `microservice/token-state/src/main/java/dev/starryeye/token_state/TokenStateApplication.java`
- Create: `microservice/token-state/src/main/java/dev/starryeye/token_state/jpa/RefreshTokenStatus.java`
- Create: `microservice/token-state/src/main/java/dev/starryeye/token_state/jpa/RefreshTokenEntity.java`
- Create: `microservice/token-state/src/main/java/dev/starryeye/token_state/jpa/RefreshTokenEntityRepository.java`
- Create: `microservice/token-state/src/main/resources/application.yml`
- Test: `microservice/token-state/src/test/java/dev/starryeye/token_state/jpa/RefreshTokenEntityTest.java`

**Interfaces:**
- Produces: `RefreshTokenEntity`(builder: tokenHash, familyId, clientId, sub, scopes, authTime, issuedAt, expiresAt, familyExpiresAt), `entity.consume(Instant)`, `entity.revoke(Instant, String reason)`, `RefreshTokenStatus.{ACTIVE,CONSUMED,REVOKED}`, `RefreshTokenEntityRepository.findByTokenHashForUpdate(String)`, `findByFamilyId(String)`

- [ ] **Step 1: consent 에서 gradle 골격 복사**

```bash
cd oauth-2/authorization-server/practice/microservice
mkdir -p token-state
cp -r consent/gradlew consent/gradlew.bat consent/gradle consent/.gitignore token-state/
mkdir -p token-state/src/main/java/dev/starryeye/token_state/jpa
mkdir -p token-state/src/main/resources
mkdir -p token-state/src/test/java/dev/starryeye/token_state/jpa
```

- [ ] **Step 2: build.gradle 작성**

`token-state/build.gradle`:

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
	runtimeOnly 'com.h2database:h2'
	compileOnly 'org.projectlombok:lombok'
	annotationProcessor 'org.projectlombok:lombok'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') { useJUnitPlatform() }
```

`token-state/settings.gradle`:

```groovy
rootProject.name = 'token-state'
```

주의. 다른 서비스와 달리 **h2 를 `runtimeOnly` 로 넣는다.** 이 서비스의 핵심 로직(회전 · 재사용 탐지 · 계열 폐기)은 실제 DB 트랜잭션과 행 잠금 위에서만 의미가 있어, mock 저장소로는 검증할 수 없다. 테스트는 h2 인메모리 DB 로 돌린다.

- [ ] **Step 3: 메인 클래스와 application.yml**

`TokenStateApplication.java`:

```java
package dev.starryeye.token_state;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TokenStateApplication {

	public static void main(String[] args) {
		SpringApplication.run(TokenStateApplication.class, args);
	}
}
```

`application.yml`:

```yaml
server:
  port: 8087

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/microservice_as
    username: root
    password: 1111
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

my:
  refresh-token-ttl-seconds: 1209600      # 14일. 회전마다 갱신된다
  refresh-family-max-seconds: 2592000     # 30일. 회전으로 연장되지 않는 절대 상한

logging:
  level:
    dev.starryeye: DEBUG
```

- [ ] **Step 4: 실패하는 엔티티 테스트 작성**

`RefreshTokenEntityTest.java`:

```java
package dev.starryeye.token_state.jpa;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenEntityTest {

	private RefreshTokenEntity newEntity() {
		Instant now = Instant.parse("2026-07-25T00:00:00Z");
		return RefreshTokenEntity.builder()
				.tokenHash("hash-1")
				.familyId("family-1")
				.clientId("my-client")
				.sub("user-sub-0001")
				.scopes("openid,offline_access")
				.authTime(1700000000L)
				.issuedAt(now)
				.expiresAt(now.plusSeconds(60))
				.familyExpiresAt(now.plusSeconds(600))
				.build();
	}

	@Test
	void newEntityIsActive() {
		assertThat(newEntity().getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
	}

	@Test
	void consumeMarksConsumedAndRecordsTime() {
		RefreshTokenEntity entity = newEntity();
		Instant at = Instant.parse("2026-07-25T00:00:30Z");

		entity.consume(at);

		assertThat(entity.getStatus()).isEqualTo(RefreshTokenStatus.CONSUMED);
		assertThat(entity.getConsumedAt()).isEqualTo(at);
	}

	@Test
	void revokeMarksRevokedWithReason() {
		RefreshTokenEntity entity = newEntity();
		Instant at = Instant.parse("2026-07-25T00:00:30Z");

		entity.revoke(at, "REUSE_DETECTED");

		assertThat(entity.getStatus()).isEqualTo(RefreshTokenStatus.REVOKED);
		assertThat(entity.getRevokedAt()).isEqualTo(at);
		assertThat(entity.getRevokedReason()).isEqualTo("REUSE_DETECTED");
	}

	// 이미 소진된 토큰도 폐기 대상이다. 계열 폐기는 CONSUMED 행까지 REVOKED 로 바꿔
	// "이 계열은 끝났다"를 한 가지 상태로 표현한다.
	@Test
	void consumedEntityCanStillBeRevoked() {
		RefreshTokenEntity entity = newEntity();
		entity.consume(Instant.parse("2026-07-25T00:00:30Z"));

		entity.revoke(Instant.parse("2026-07-25T00:01:00Z"), "REUSE_DETECTED");

		assertThat(entity.getStatus()).isEqualTo(RefreshTokenStatus.REVOKED);
	}
}
```

- [ ] **Step 5: 테스트 실패 확인**

Run: `cd token-state && ./gradlew test --no-daemon`
Expected: FAIL — `RefreshTokenEntity` 없음(컴파일 오류)

- [ ] **Step 6: enum · 엔티티 · 저장소 구현**

`RefreshTokenStatus.java`:

```java
package dev.starryeye.token_state.jpa;

public enum RefreshTokenStatus {
	ACTIVE, CONSUMED, REVOKED
}
```

`RefreshTokenEntity.java`:

```java
package dev.starryeye.token_state.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_tokens_family", columnList = "family_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenEntity {

	/**
	 * refresh token 한 개를 나타낸다. 회전하면 기존 행은 CONSUMED 가 되고 같은 family_id 로 새 행이 생기므로,
	 *      한 계열(family)은 여러 행으로 남아 발급 이력이 그대로 감사 기록이 된다.
	 *
	 * 주의. 토큰 원문을 저장하지 않고 SHA-256 해시만 보관한다. DB 가 유출돼도 쓸 수 있는 토큰이 나오지 않는다.
	 *      salt 를 쓰지 않는 이유는 해시로 행을 찾아야 해서 조회가 결정적이어야 하기 때문이며,
	 *      원문이 256비트 난수라 사전 공격 대상이 아니어서 성립한다. 사용자 비밀번호에는 같은 논리를 적용할 수 없다.
	 *
	 * 주의. family_expires_at 은 회전 때 그대로 복사한다. expires_at 만 갱신되므로 회전을 반복해도
	 *      계열 자체의 수명은 늘어나지 않는다.
	 */

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "family_id", nullable = false, length = 36)
	private String familyId;

	@Column(name = "client_id", nullable = false)
	private String clientId;

	@Column(name = "sub", nullable = false)
	private String sub;

	@Column(nullable = false, length = 1000)
	private String scopes; // comma 구분

	@Column(name = "auth_time", nullable = false)
	private long authTime;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RefreshTokenStatus status;

	@Column(name = "issued_at", nullable = false)
	private Instant issuedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "family_expires_at", nullable = false)
	private Instant familyExpiresAt;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "revoked_reason", length = 30)
	private String revokedReason;

	@Builder
	private RefreshTokenEntity(String tokenHash, String familyId, String clientId, String sub, String scopes,
			long authTime, Instant issuedAt, Instant expiresAt, Instant familyExpiresAt) {
		this.tokenHash = tokenHash;
		this.familyId = familyId;
		this.clientId = clientId;
		this.sub = sub;
		this.scopes = scopes;
		this.authTime = authTime;
		this.issuedAt = issuedAt;
		this.expiresAt = expiresAt;
		this.familyExpiresAt = familyExpiresAt;
		this.status = RefreshTokenStatus.ACTIVE;
	}

	public void consume(Instant at) {
		this.status = RefreshTokenStatus.CONSUMED;
		this.consumedAt = at;
	}

	public void revoke(Instant at, String reason) {
		this.status = RefreshTokenStatus.REVOKED;
		this.revokedAt = at;
		this.revokedReason = reason;
	}
}
```

`RefreshTokenEntityRepository.java`:

```java
package dev.starryeye.token_state.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenEntityRepository extends JpaRepository<RefreshTokenEntity, Long> {

	/**
	 * 회전 경로 전용 조회다. PESSIMISTIC_WRITE 로 행을 잠가야 같은 토큰의 동시 요청이 직렬화된다.
	 *      잠금이 없으면 두 트랜잭션이 모두 ACTIVE 를 읽고 둘 다 회전에 성공하며,
	 *      새로 만드는 행의 token_hash 는 서로 다른 난수라 unique 제약에도 걸리지 않아 조용히 통과한다.
	 *      그러면 재사용 탐지가 아무것도 잡지 못한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from RefreshTokenEntity r where r.tokenHash = :tokenHash")
	Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

	List<RefreshTokenEntity> findByFamilyId(String familyId);
}
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `cd token-state && ./gradlew test --no-daemon --rerun-tasks`
Expected: PASS — 4 tests

- [ ] **Step 8: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/token-state
git commit -m "microservice: scaffold token-state service with refresh token entity

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 2: 토큰 생성기

**Files:**
- Create: `microservice/token-state/src/main/java/dev/starryeye/token_state/TokenGenerator.java`
- Test: `microservice/token-state/src/test/java/dev/starryeye/token_state/TokenGeneratorTest.java`

**Interfaces:**
- Produces: `TokenGenerator.generate() → String`(원문), `TokenGenerator.hash(String) → String`(64자 hex)

- [ ] **Step 1: 실패하는 테스트 작성**

`TokenGeneratorTest.java`:

```java
package dev.starryeye.token_state;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TokenGeneratorTest {

	private final TokenGenerator generator = new TokenGenerator();

	@Test
	void generatesUrlSafeTokenWithoutPadding() {
		String token = generator.generate();

		assertThat(token).matches("[A-Za-z0-9_-]+"); // base64url, padding 없음
		assertThat(token).hasSizeGreaterThanOrEqualTo(43); // 256비트 = 43자
	}

	@Test
	void generatesDistinctTokens() {
		Set<String> tokens = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			tokens.add(generator.generate());
		}
		assertThat(tokens).hasSize(1000);
	}

	// 해시로 행을 찾아야 하므로 같은 입력은 반드시 같은 출력이어야 한다 (salt 를 쓸 수 없는 이유)
	@Test
	void hashIsDeterministic() {
		assertThat(generator.hash("abc")).isEqualTo(generator.hash("abc"));
	}

	// SHA-256 의 알려진 벡터. 직접 구현한 해시가 실제로 SHA-256 인지 못 박는다.
	@Test
	void hashMatchesKnownSha256Vector() {
		assertThat(generator.hash("abc"))
				.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void differentInputsProduceDifferentHashes() {
		assertThat(generator.hash("abc")).isNotEqualTo(generator.hash("abd"));
	}
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd token-state && ./gradlew test --tests TokenGeneratorTest --no-daemon`
Expected: FAIL — `TokenGenerator` 없음

- [ ] **Step 3: 구현**

`TokenGenerator.java`:

```java
package dev.starryeye.token_state;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class TokenGenerator {

	/**
	 * refresh token 원문을 만들고 저장용 해시를 계산한다.
	 *      원문은 256비트 난수라 추측할 수 없고, 저장은 SHA-256 해시만 한다.
	 *
	 * 주의. bcrypt 계열을 쓸 수 없다. 해시로 행을 찾아야 하므로 같은 입력이 항상 같은 출력이어야 하는데,
	 *      bcrypt 는 salt 를 섞어 매번 다른 값을 낸다. 원문이 고엔트로피 난수라 사전 공격 대상이 아니어서
	 *      salt 없는 단순 해시로 충분하다. 사용자 비밀번호에는 같은 논리를 적용할 수 없다.
	 */

	private static final int TOKEN_BYTES = 32; // 256비트

	private final SecureRandom secureRandom = new SecureRandom();

	public String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (Exception e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd token-state && ./gradlew test --tests TokenGeneratorTest --no-daemon --rerun-tasks`
Expected: PASS — 5 tests

- [ ] **Step 5: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/token-state
git commit -m "microservice: add refresh token generator with sha-256 hashing

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 3: 발급 · 조회 (RefreshTokenService 1/2)

**Files:**
- Create: `microservice/token-state/src/main/java/dev/starryeye/token_state/RefreshTokenService.java`
- Create: `microservice/token-state/src/main/java/dev/starryeye/token_state/IssueResult.java`
- Create: `microservice/token-state/src/main/java/dev/starryeye/token_state/IntrospectResult.java`
- Create: `microservice/token-state/src/test/resources/application.yml`
- Test: `microservice/token-state/src/test/java/dev/starryeye/token_state/RefreshTokenServiceIssueTest.java`

**Interfaces:**
- Consumes: `RefreshTokenEntity`, `RefreshTokenEntityRepository`, `TokenGenerator` (Task 1·2)
- Produces: `RefreshTokenService.issue(String clientId, String sub, String scope, long authTime) → IssueResult`, `RefreshTokenService.introspect(String refreshToken) → IntrospectResult`, `IssueResult(String refreshToken, long expiresAt, String familyId)`, `IntrospectResult(boolean active, String sub, String clientId, String scope, long exp, long iat)`

- [ ] **Step 1: 테스트용 application.yml 작성**

`src/test/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:token-state;DB_CLOSE_DELAY=-1;MODE=MySQL
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false

my:
  refresh-token-ttl-seconds: 60
  refresh-family-max-seconds: 300
```

주의. 테스트의 TTL 을 짧게 둬야 만료 경로를 실제로 태울 수 있다. 운영값(14일 · 30일)으로는 만료 테스트를 쓸 수 없다.

- [ ] **Step 2: 실패하는 테스트 작성**

`RefreshTokenServiceIssueTest.java`:

```java
package dev.starryeye.token_state;

import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RefreshTokenServiceIssueTest {

	@Autowired
	private RefreshTokenService service;

	@Autowired
	private RefreshTokenEntityRepository repository;

	@Autowired
	private TokenGenerator tokenGenerator;

	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	@Test
	void issueStoresHashNotRawToken() {
		IssueResult result = service.issue("my-client", "user-sub-0001", "openid offline_access", 1700000000L);

		assertThat(result.refreshToken()).isNotBlank();
		assertThat(repository.findByTokenHash(result.refreshToken())).isEmpty(); // 원문으로는 찾을 수 없다
		Optional<RefreshTokenEntity> found = repository.findByTokenHash(tokenGenerator.hash(result.refreshToken()));
		assertThat(found).isPresent();
		assertThat(found.get().getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
	}

	// API 는 공백 구분, DB 는 comma 구분이다
	@Test
	void issueConvertsScopeToCommaForStorage() {
		IssueResult result = service.issue("my-client", "user-sub-0001", "openid offline_access", 1700000000L);

		RefreshTokenEntity entity = repository.findByTokenHash(tokenGenerator.hash(result.refreshToken())).orElseThrow();
		assertThat(entity.getScopes()).isEqualTo("openid,offline_access");
	}

	@Test
	void issueStartsNewFamilyEachTime() {
		IssueResult first = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);
		IssueResult second = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);

		assertThat(first.familyId()).isNotEqualTo(second.familyId());
	}

	@Test
	void issueSetsFamilyExpiryFromConfiguredMaximum() {
		IssueResult result = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);

		RefreshTokenEntity entity = repository.findByTokenHash(tokenGenerator.hash(result.refreshToken())).orElseThrow();
		// 테스트 설정: ttl 60초, family 최대 300초
		assertThat(entity.getFamilyExpiresAt()).isAfter(entity.getExpiresAt());
	}

	@Test
	void introspectReturnsActiveWithClaims() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1700000000L);

		IntrospectResult result = service.introspect(issued.refreshToken());

		assertThat(result.active()).isTrue();
		assertThat(result.sub()).isEqualTo("user-sub-0001");
		assertThat(result.clientId()).isEqualTo("my-client");
		assertThat(result.scope()).isEqualTo("openid offline_access"); // 응답은 공백 구분으로 되돌린다
	}

	@Test
	void introspectReturnsInactiveForUnknownToken() {
		IntrospectResult result = service.introspect("no-such-token");

		assertThat(result.active()).isFalse();
		assertThat(result.sub()).isNull();
		assertThat(result.clientId()).isNull();
		assertThat(result.scope()).isNull();
	}
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd token-state && ./gradlew test --tests RefreshTokenServiceIssueTest --no-daemon`
Expected: FAIL — `RefreshTokenService` 없음

- [ ] **Step 4: 결과 record 작성**

`IssueResult.java`:

```java
package dev.starryeye.token_state;

public record IssueResult(String refreshToken, long expiresAt, String familyId) {
}
```

`IntrospectResult.java`:

```java
package dev.starryeye.token_state;

public record IntrospectResult(boolean active, String sub, String clientId, String scope, long exp, long iat) {

	public static IntrospectResult inactive() {
		return new IntrospectResult(false, null, null, null, 0L, 0L);
	}
}
```

- [ ] **Step 5: 서비스 구현 (발급 · 조회만)**

`RefreshTokenService.java`:

```java
package dev.starryeye.token_state;

import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

	/**
	 * refresh token 의 발급 · 회전 · 폐기 · 조회를 담당한다. 상태 전이 판정이 전부 여기 모여 있고,
	 *      컨트롤러는 위임만 한다.
	 *
	 * 주의. scope 는 API 경계에서 공백 구분(OAuth 와이어 포맷), DB 에서 comma 구분이다. 변환은 이 클래스에서만 한다.
	 */

	private final RefreshTokenEntityRepository repository;
	private final TokenGenerator tokenGenerator;
	private final long ttlSeconds;
	private final long familyMaxSeconds;

	public RefreshTokenService(
			RefreshTokenEntityRepository repository,
			TokenGenerator tokenGenerator,
			@Value("${my.refresh-token-ttl-seconds}") long ttlSeconds,
			@Value("${my.refresh-family-max-seconds}") long familyMaxSeconds
	) {
		this.repository = repository;
		this.tokenGenerator = tokenGenerator;
		this.ttlSeconds = ttlSeconds;
		this.familyMaxSeconds = familyMaxSeconds;
	}

	@Transactional
	public IssueResult issue(String clientId, String sub, String scope, long authTime) {
		Instant now = Instant.now();
		String familyId = UUID.randomUUID().toString();
		String token = tokenGenerator.generate();

		RefreshTokenEntity entity = RefreshTokenEntity.builder()
				.tokenHash(tokenGenerator.hash(token))
				.familyId(familyId)
				.clientId(clientId)
				.sub(sub)
				.scopes(toCommaDelimited(scope))
				.authTime(authTime)
				.issuedAt(now)
				.expiresAt(now.plusSeconds(ttlSeconds))
				.familyExpiresAt(now.plusSeconds(familyMaxSeconds))
				.build();
		repository.save(entity);

		return new IssueResult(token, entity.getExpiresAt().getEpochSecond(), familyId);
	}

	@Transactional(readOnly = true)
	public IntrospectResult introspect(String refreshToken) {
		Optional<RefreshTokenEntity> found = repository.findByTokenHash(tokenGenerator.hash(refreshToken));
		if (found.isEmpty()) {
			return IntrospectResult.inactive();
		}
		RefreshTokenEntity entity = found.get();
		if (entity.getStatus() != RefreshTokenStatus.ACTIVE || isExpired(entity, Instant.now())) {
			return IntrospectResult.inactive();
		}
		return new IntrospectResult(
				true,
				entity.getSub(),
				entity.getClientId(),
				toSpaceDelimited(entity.getScopes()),
				entity.getExpiresAt().getEpochSecond(),
				entity.getIssuedAt().getEpochSecond()
		);
	}

	private boolean isExpired(RefreshTokenEntity entity, Instant now) {
		return entity.getExpiresAt().isBefore(now) || entity.getFamilyExpiresAt().isBefore(now);
	}

	private String toCommaDelimited(String spaceDelimited) {
		return String.join(",", spaceDelimited.trim().split("\\s+"));
	}

	private String toSpaceDelimited(String commaDelimited) {
		return String.join(" ", commaDelimited.split(","));
	}
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `cd token-state && ./gradlew test --no-daemon --rerun-tasks`
Expected: PASS — 엔티티 4 + 생성기 5 + 발급/조회 6 = 15 tests

- [ ] **Step 7: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/token-state
git commit -m "microservice: issue and introspect refresh tokens

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 4: 회전과 재사용 탐지 (RefreshTokenService 2/2)

이 슬라이스의 핵심이다. 판정과 전이가 **한 트랜잭션 안**에서 일어나야 하고, 행 잠금이 없으면 재사용 탐지가 아무것도 잡지 못한다.

**Files:**
- Modify: `microservice/token-state/src/main/java/dev/starryeye/token_state/RefreshTokenService.java`
- Create: `microservice/token-state/src/main/java/dev/starryeye/token_state/RotateStatus.java`
- Create: `microservice/token-state/src/main/java/dev/starryeye/token_state/RotateResult.java`
- Test: `microservice/token-state/src/test/java/dev/starryeye/token_state/RefreshTokenServiceRotateTest.java`

**Interfaces:**
- Consumes: `RefreshTokenService.issue(...)`, `IssueResult`, `TokenGenerator`, `RefreshTokenEntityRepository` (Task 1~3)
- Produces: `RefreshTokenService.rotate(String refreshToken, String clientId) → RotateResult`, `RotateStatus.{ROTATED,REUSE_DETECTED,REVOKED,EXPIRED,NOT_FOUND,CLIENT_MISMATCH}`, `RotateResult(RotateStatus status, String sub, String scope, long authTime, String refreshToken, long expiresAt)`

- [ ] **Step 1: 실패하는 테스트 작성**

`RefreshTokenServiceRotateTest.java`:

```java
package dev.starryeye.token_state;

import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RefreshTokenServiceRotateTest {

	@Autowired
	private RefreshTokenService service;

	@Autowired
	private RefreshTokenEntityRepository repository;

	@Autowired
	private TokenGenerator tokenGenerator;

	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	@Test
	void rotateIssuesNewTokenInSameFamilyAndConsumesOld() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1700000000L);

		RotateResult result = service.rotate(issued.refreshToken(), "my-client");

		assertThat(result.status()).isEqualTo(RotateStatus.ROTATED);
		assertThat(result.refreshToken()).isNotEqualTo(issued.refreshToken());
		assertThat(result.sub()).isEqualTo("user-sub-0001");
		assertThat(result.scope()).isEqualTo("openid offline_access");
		assertThat(result.authTime()).isEqualTo(1700000000L);

		RefreshTokenEntity old = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken())).orElseThrow();
		assertThat(old.getStatus()).isEqualTo(RefreshTokenStatus.CONSUMED);
		assertThat(old.getConsumedAt()).isNotNull();

		RefreshTokenEntity fresh = repository.findByTokenHash(tokenGenerator.hash(result.refreshToken())).orElseThrow();
		assertThat(fresh.getFamilyId()).isEqualTo(old.getFamilyId());
		assertThat(fresh.getFamilyExpiresAt()).isEqualTo(old.getFamilyExpiresAt()); // 절대 상한은 복사, 연장되지 않는다
	}

	// 이 슬라이스의 핵심 보안 동작. 응답 status 만 보지 말고 DB 상태로 계열 전체를 확인한다.
	@Test
	void reusingConsumedTokenRevokesEntireFamily() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);
		RotateResult first = service.rotate(issued.refreshToken(), "my-client");

		RotateResult reuse = service.rotate(issued.refreshToken(), "my-client"); // 이미 소진된 토큰

		assertThat(reuse.status()).isEqualTo(RotateStatus.REUSE_DETECTED);
		assertThat(reuse.refreshToken()).isNull();

		String familyId = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken()))
				.orElseThrow().getFamilyId();
		List<RefreshTokenEntity> family = repository.findByFamilyId(familyId);
		assertThat(family).hasSize(2);
		assertThat(family).allMatch(e -> e.getStatus() == RefreshTokenStatus.REVOKED);
		assertThat(family).allMatch(e -> "REUSE_DETECTED".equals(e.getRevokedReason()));

		// 계열이 죽었으므로 방금 발급된 정상 토큰도 더는 쓸 수 없다
		RotateResult afterRevoke = service.rotate(first.refreshToken(), "my-client");
		assertThat(afterRevoke.status()).isEqualTo(RotateStatus.REVOKED);
	}

	@Test
	void rotateWithUnknownTokenReturnsNotFound() {
		RotateResult result = service.rotate("no-such-token", "my-client");

		assertThat(result.status()).isEqualTo(RotateStatus.NOT_FOUND);
	}

	// client 가 다르면 상태를 바꾸지 않는다. 남의 토큰을 제출해 계열을 죽이는 공격을 막는다.
	@Test
	void rotateWithMismatchedClientChangesNothing() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);

		RotateResult result = service.rotate(issued.refreshToken(), "other-client");

		assertThat(result.status()).isEqualTo(RotateStatus.CLIENT_MISMATCH);
		RefreshTokenEntity entity = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken())).orElseThrow();
		assertThat(entity.getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
	}

	// 개별 토큰은 아직 유효하지만 계열 절대 상한을 넘긴 경우를 격리해 검증한다.
	// (테스트 설정: ttl 60초, family 최대 300초)
	@Test
	void rotateAfterFamilyAbsoluteExpiryReturnsExpired() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);
		RefreshTokenEntity entity = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken())).orElseThrow();
		// 계열 상한만 과거로 옮긴다. expires_at 은 그대로 미래다.
		repository.save(expireFamily(entity));

		RotateResult result = service.rotate(issued.refreshToken(), "my-client");

		assertThat(result.status()).isEqualTo(RotateStatus.EXPIRED);
	}

	private RefreshTokenEntity expireFamily(RefreshTokenEntity entity) {
		RefreshTokenEntity replaced = RefreshTokenEntity.builder()
				.tokenHash(entity.getTokenHash())
				.familyId(entity.getFamilyId())
				.clientId(entity.getClientId())
				.sub(entity.getSub())
				.scopes(entity.getScopes())
				.authTime(entity.getAuthTime())
				.issuedAt(entity.getIssuedAt())
				.expiresAt(entity.getExpiresAt())
				.familyExpiresAt(entity.getIssuedAt().minusSeconds(1))
				.build();
		repository.delete(entity);
		repository.flush();
		return replaced;
	}
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd token-state && ./gradlew test --tests RefreshTokenServiceRotateTest --no-daemon`
Expected: FAIL — `RotateStatus` · `RotateResult` · `rotate` 없음

- [ ] **Step 3: 결과 타입 작성**

`RotateStatus.java`:

```java
package dev.starryeye.token_state;

public enum RotateStatus {
	ROTATED, REUSE_DETECTED, REVOKED, EXPIRED, NOT_FOUND, CLIENT_MISMATCH
}
```

`RotateResult.java`:

```java
package dev.starryeye.token_state;

public record RotateResult(
		RotateStatus status,
		String sub,
		String scope,
		long authTime,
		String refreshToken,
		long expiresAt
) {

	public static RotateResult failed(RotateStatus status) {
		return new RotateResult(status, null, null, 0L, null, 0L);
	}
}
```

- [ ] **Step 4: rotate 구현 (RefreshTokenService 에 추가)**

`RefreshTokenService.java` 의 `introspect` 위에 다음 메서드를 추가한다:

```java
	/**
	 * 회전을 한 트랜잭션 안에서 끝낸다. 조회 · 판정 · 전이를 호출자에게 쪼개 주면 왕복 사이에 경쟁 창이 생겨
	 *      재사용 탐지가 무력해지므로, 연산 하나를 한 번의 호출로 표현한다.
	 *
	 * 주의. 조회는 반드시 findByTokenHashForUpdate 로 한다. 행 잠금이 없으면 같은 토큰의 동시 요청 두 건이
	 *      모두 ACTIVE 를 읽고 둘 다 회전에 성공한다.
	 *
	 * 주의. 이미 소진된(CONSUMED) 토큰이 다시 오면 계열 전체를 폐기한다. 정상 사용자와 공격자 중 누가 먼저
	 *      회전했든 다른 쪽이 CONSUMED 를 만나므로, 양쪽을 모두 재인증으로 떨어뜨려 조용한 지속 접근을 끊는다.
	 *      정상 client 의 단순 재시도까지 계열을 죽이는 것은 회전의 알려진 대가다.
	 */
	@Transactional
	public RotateResult rotate(String refreshToken, String clientId) {

		Instant now = Instant.now();
		Optional<RefreshTokenEntity> found = repository.findByTokenHashForUpdate(tokenGenerator.hash(refreshToken));
		if (found.isEmpty()) {
			return RotateResult.failed(RotateStatus.NOT_FOUND);
		}
		RefreshTokenEntity entity = found.get();

		if (!entity.getClientId().equals(clientId)) {
			return RotateResult.failed(RotateStatus.CLIENT_MISMATCH);
		}
		if (entity.getStatus() == RefreshTokenStatus.REVOKED) {
			return RotateResult.failed(RotateStatus.REVOKED);
		}
		if (entity.getStatus() == RefreshTokenStatus.CONSUMED) {
			revokeFamily(entity.getFamilyId(), now, "REUSE_DETECTED");
			return RotateResult.failed(RotateStatus.REUSE_DETECTED);
		}
		if (isExpired(entity, now)) {
			return RotateResult.failed(RotateStatus.EXPIRED);
		}

		entity.consume(now);

		String token = tokenGenerator.generate();
		RefreshTokenEntity rotated = RefreshTokenEntity.builder()
				.tokenHash(tokenGenerator.hash(token))
				.familyId(entity.getFamilyId())
				.clientId(entity.getClientId())
				.sub(entity.getSub())
				.scopes(entity.getScopes()) // 축소 요청이 있어도 저장 scope 는 그대로다
				.authTime(entity.getAuthTime())
				.issuedAt(now)
				.expiresAt(now.plusSeconds(ttlSeconds))
				.familyExpiresAt(entity.getFamilyExpiresAt()) // 절대 상한은 복사만, 연장하지 않는다
				.build();
		repository.save(rotated);

		return new RotateResult(
				RotateStatus.ROTATED,
				entity.getSub(),
				toSpaceDelimited(entity.getScopes()),
				entity.getAuthTime(),
				token,
				rotated.getExpiresAt().getEpochSecond()
		);
	}

	private void revokeFamily(String familyId, Instant at, String reason) {
		List<RefreshTokenEntity> family = repository.findByFamilyId(familyId);
		for (RefreshTokenEntity member : family) {
			member.revoke(at, reason);
		}
		repository.saveAll(family);
	}
```

`import java.util.List;` 를 추가한다.

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd token-state && ./gradlew test --no-daemon --rerun-tasks`
Expected: PASS — 15 + 5 = 20 tests

- [ ] **Step 6: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/token-state
git commit -m "microservice: rotate refresh tokens with reuse detection

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 5: 폐기 + 내부 API 컨트롤러

**Files:**
- Modify: `microservice/token-state/src/main/java/dev/starryeye/token_state/RefreshTokenService.java`
- Create: `microservice/token-state/src/main/java/dev/starryeye/token_state/RefreshTokenController.java`
- Create: `microservice/token-state/src/main/java/dev/starryeye/token_state/dto/IssueRequest.java`
- Create: `microservice/token-state/src/main/java/dev/starryeye/token_state/dto/RotateRequest.java`
- Create: `microservice/token-state/src/main/java/dev/starryeye/token_state/dto/RevokeRequest.java`
- Create: `microservice/token-state/src/main/java/dev/starryeye/token_state/dto/RevokeResponse.java`
- Create: `microservice/token-state/src/main/java/dev/starryeye/token_state/dto/IntrospectRequest.java`
- Test: `microservice/token-state/src/test/java/dev/starryeye/token_state/RefreshTokenServiceRevokeTest.java`
- Test: `microservice/token-state/src/test/java/dev/starryeye/token_state/RefreshTokenControllerTest.java`

**Interfaces:**
- Consumes: `RefreshTokenService.issue/rotate/introspect`, `IssueResult`, `RotateResult`, `IntrospectResult` (Task 3·4)
- Produces: `RefreshTokenService.revoke(String refreshToken, String clientId) → boolean`, 내부 HTTP API 4개(공유 계약 절 참고)

- [ ] **Step 1: 실패하는 폐기 테스트 작성**

`RefreshTokenServiceRevokeTest.java`:

```java
package dev.starryeye.token_state;

import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RefreshTokenServiceRevokeTest {

	@Autowired
	private RefreshTokenService service;

	@Autowired
	private RefreshTokenEntityRepository repository;

	@Autowired
	private TokenGenerator tokenGenerator;

	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	// refresh token 하나는 하나의 grant 를 대표하므로, 폐기는 그 grant 를 끝내는 것이다 (RFC 7009 2.1)
	@Test
	void revokeKillsEntireFamily() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);
		RotateResult rotated = service.rotate(issued.refreshToken(), "my-client");

		boolean revoked = service.revoke(rotated.refreshToken(), "my-client");

		assertThat(revoked).isTrue();
		String familyId = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken()))
				.orElseThrow().getFamilyId();
		List<RefreshTokenEntity> family = repository.findByFamilyId(familyId);
		assertThat(family).hasSize(2);
		assertThat(family).allMatch(e -> e.getStatus() == RefreshTokenStatus.REVOKED);
		assertThat(family).allMatch(e -> "CLIENT_REVOKED".equals(e.getRevokedReason()));
	}

	@Test
	void revokeAfterRevokeStillRotatesToRevoked() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);
		service.revoke(issued.refreshToken(), "my-client");

		RotateResult result = service.rotate(issued.refreshToken(), "my-client");

		assertThat(result.status()).isEqualTo(RotateStatus.REVOKED);
	}

	@Test
	void revokeUnknownTokenReturnsFalse() {
		assertThat(service.revoke("no-such-token", "my-client")).isFalse();
	}

	// 남의 토큰으로는 아무것도 폐기할 수 없다
	@Test
	void revokeWithMismatchedClientChangesNothing() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);

		boolean revoked = service.revoke(issued.refreshToken(), "other-client");

		assertThat(revoked).isFalse();
		RefreshTokenEntity entity = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken())).orElseThrow();
		assertThat(entity.getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
	}
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd token-state && ./gradlew test --tests RefreshTokenServiceRevokeTest --no-daemon`
Expected: FAIL — `revoke` 없음

- [ ] **Step 3: revoke 구현 (RefreshTokenService 에 추가)**

```java
	/**
	 * 계열 전체를 폐기한다. 요청 client 의 토큰이 아니면 아무것도 하지 않는다.
	 */
	@Transactional
	public boolean revoke(String refreshToken, String clientId) {
		Optional<RefreshTokenEntity> found = repository.findByTokenHashForUpdate(tokenGenerator.hash(refreshToken));
		if (found.isEmpty()) {
			return false;
		}
		RefreshTokenEntity entity = found.get();
		if (!entity.getClientId().equals(clientId)) {
			return false;
		}
		revokeFamily(entity.getFamilyId(), Instant.now(), "CLIENT_REVOKED");
		return true;
	}
```

- [ ] **Step 4: 폐기 테스트 통과 확인**

Run: `cd token-state && ./gradlew test --tests RefreshTokenServiceRevokeTest --no-daemon --rerun-tasks`
Expected: PASS — 4 tests

- [ ] **Step 5: 요청·응답 record 작성**

`dto/IssueRequest.java`:

```java
package dev.starryeye.token_state.dto;

public record IssueRequest(String clientId, String sub, String scope, long authTime) {
}
```

`dto/RotateRequest.java`:

```java
package dev.starryeye.token_state.dto;

public record RotateRequest(String refreshToken, String clientId) {
}
```

`dto/RevokeRequest.java`:

```java
package dev.starryeye.token_state.dto;

public record RevokeRequest(String refreshToken, String clientId) {
}
```

`dto/RevokeResponse.java`:

```java
package dev.starryeye.token_state.dto;

public record RevokeResponse(boolean revoked) {
}
```

`dto/IntrospectRequest.java`:

```java
package dev.starryeye.token_state.dto;

public record IntrospectRequest(String refreshToken) {
}
```

- [ ] **Step 6: 실패하는 컨트롤러 테스트 작성**

`RefreshTokenControllerTest.java`:

```java
package dev.starryeye.token_state;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RefreshTokenEntityRepository repository;

	@Autowired
	private RefreshTokenService service;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	private String json(Map<String, Object> body) throws Exception {
		return objectMapper.writeValueAsString(body);
	}

	@Test
	void issueReturnsTokenAndFamily() throws Exception {
		mockMvc.perform(post("/internal/refresh-tokens")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("clientId", "my-client", "sub", "user-sub-0001",
								"scope", "openid offline_access", "authTime", 1700000000L))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.familyId").isNotEmpty())
				.andExpect(jsonPath("$.expiresAt").isNumber());
	}

	@Test
	void rotateReturnsRotatedStatusAndNewToken() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);

		mockMvc.perform(post("/internal/refresh-tokens/rotate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("refreshToken", issued.refreshToken(), "clientId", "my-client"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ROTATED"))
				.andExpect(jsonPath("$.sub").value("user-sub-0001"))
				.andExpect(jsonPath("$.scope").value("openid"))
				.andExpect(jsonPath("$.authTime").value(1700000000L))
				.andExpect(jsonPath("$.refreshToken").isNotEmpty());
	}

	@Test
	void rotateWithReusedTokenReturnsReuseDetected() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);
		service.rotate(issued.refreshToken(), "my-client");

		mockMvc.perform(post("/internal/refresh-tokens/rotate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("refreshToken", issued.refreshToken(), "clientId", "my-client"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REUSE_DETECTED"))
				.andExpect(jsonPath("$.refreshToken").doesNotExist());
	}

	@Test
	void revokeReturnsRevokedFlag() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);

		mockMvc.perform(post("/internal/refresh-tokens/revoke")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("refreshToken", issued.refreshToken(), "clientId", "my-client"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.revoked").value(true));
	}

	@Test
	void introspectReturnsActiveClaims() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1700000000L);

		mockMvc.perform(post("/internal/refresh-tokens/introspect")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("refreshToken", issued.refreshToken()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.sub").value("user-sub-0001"))
				.andExpect(jsonPath("$.clientId").value("my-client"))
				.andExpect(jsonPath("$.scope").value("openid offline_access"));
	}

	// 비활성 응답에서 나머지 필드가 새지 않아야 한다
	@Test
	void introspectInactiveOmitsClaims() throws Exception {
		mockMvc.perform(post("/internal/refresh-tokens/introspect")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("refreshToken", "no-such-token"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false))
				.andExpect(jsonPath("$.sub").doesNotExist())
				.andExpect(jsonPath("$.clientId").doesNotExist())
				.andExpect(jsonPath("$.scope").doesNotExist());
	}
}
```

- [ ] **Step 7: 테스트 실패 확인**

Run: `cd token-state && ./gradlew test --tests RefreshTokenControllerTest --no-daemon`
Expected: FAIL — `RefreshTokenController` 없음(404)

- [ ] **Step 8: 컨트롤러 구현**

`RefreshTokenController.java`:

```java
package dev.starryeye.token_state;

import dev.starryeye.token_state.dto.IntrospectRequest;
import dev.starryeye.token_state.dto.IssueRequest;
import dev.starryeye.token_state.dto.RevokeRequest;
import dev.starryeye.token_state.dto.RevokeResponse;
import dev.starryeye.token_state.dto.RotateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RefreshTokenController {

	/**
	 * refresh token 상태 API 다. (내부 전용.. gateway 에 노출하지 않는다)
	 *      판정은 하지 않고 RefreshTokenService 에 위임하기만 한다. 상태 전이 규칙이 두 곳에 흩어지면
	 *      트랜잭션 경계 밖에서 판단이 일어나 원자성이 깨진다.
	 *
	 * 주의. 회전 실패 사유(REUSE_DETECTED / EXPIRED / NOT_FOUND ...)를 그대로 돌려주지만, 이것을 클라이언트에게
	 *      전달하는 것은 token 서비스의 몫이 아니다. token 은 전부 invalid_grant 로 뭉갠다.
	 *      사유를 구분해 주면 "이건 이미 소진됐다" 와 "이건 없다" 를 알려주는 셈이라 탐색을 돕는다.
	 */

	private final RefreshTokenService refreshTokenService;

	@PostMapping("/internal/refresh-tokens")
	public IssueResult issue(@RequestBody IssueRequest request) {
		return refreshTokenService.issue(request.clientId(), request.sub(), request.scope(), request.authTime());
	}

	@PostMapping("/internal/refresh-tokens/rotate")
	public RotateResult rotate(@RequestBody RotateRequest request) {
		return refreshTokenService.rotate(request.refreshToken(), request.clientId());
	}

	@PostMapping("/internal/refresh-tokens/revoke")
	public RevokeResponse revoke(@RequestBody RevokeRequest request) {
		return new RevokeResponse(refreshTokenService.revoke(request.refreshToken(), request.clientId()));
	}

	@PostMapping("/internal/refresh-tokens/introspect")
	public IntrospectResult introspect(@RequestBody IntrospectRequest request) {
		return refreshTokenService.introspect(request.refreshToken());
	}
}
```

- [ ] **Step 9: null 필드가 응답에서 빠지도록 record 에 애너테이션 추가**

`IntrospectResult.java` 와 `RotateResult.java` 의 선언 위에 `@JsonInclude(JsonInclude.Include.NON_NULL)` 를 붙이고 `import com.fasterxml.jackson.annotation.JsonInclude;` 를 추가한다. 비활성·실패 응답에서 빈 필드가 새지 않게 하기 위함이다.

- [ ] **Step 10: 전체 테스트 통과 확인**

Run: `cd token-state && ./gradlew test --no-daemon --rerun-tasks`
Expected: PASS — 20 + 4 + 6 = 30 tests

- [ ] **Step 11: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/token-state
git commit -m "microservice: revoke token families and expose internal refresh token API

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 6: token — ClientAuthenticator 추출

동작 변경이 없는 순수 리팩터링이다. 뒤따르는 세 엔드포인트(refresh grant · introspect · revoke)가 같은 client 인증을 필요로 하므로, 소비자가 셋이 되기 전에 한 곳으로 모은다.

**Files:**
- Create: `microservice/token/src/main/java/dev/starryeye/token/ClientAuthenticator.java`
- Modify: `microservice/token/src/main/java/dev/starryeye/token/TokenEndpointController.java`
- Test: `microservice/token/src/test/java/dev/starryeye/token/ClientAuthenticatorTest.java`

**Interfaces:**
- Consumes: `ClientRegistryClient.getClient(String) → ClientInfo` (throws `ClientRegistryClient.ClientNotFoundException`), `ClientInfo(clientId, redirectUris, scopes, clientSecretHash, grantTypes)`
- Produces: `ClientAuthenticator.authenticate(String authorizationHeader) → ClientInfo` (throws `ClientAuthenticator.ClientAuthenticationException` with `getDescription()`)

- [ ] **Step 1: 실패하는 테스트 작성**

`ClientAuthenticatorTest.java`:

```java
package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.ClientRegistryClient;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientAuthenticatorTest {

	private final ClientRegistryClient clientRegistryClient = mock(ClientRegistryClient.class);
	private final ClientAuthenticator authenticator = new ClientAuthenticator(clientRegistryClient);

	private String basic(String clientId, String secret) {
		return "Basic " + Base64.getEncoder()
				.encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
	}

	private ClientInfo clientInfo() {
		String hash = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("secret");
		return new ClientInfo("my-client", List.of("http://127.0.0.1:8080/callback"),
				List.of("openid"), hash, List.of("authorization_code"));
	}

	@Test
	void authenticatesValidCredentials() {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());

		ClientInfo result = authenticator.authenticate(basic("my-client", "secret"));

		assertThat(result.clientId()).isEqualTo("my-client");
	}

	@Test
	void missingHeaderIsRejected() {
		assertThatThrownBy(() -> authenticator.authenticate(null))
				.isInstanceOf(ClientAuthenticator.ClientAuthenticationException.class)
				.hasMessage("missing client credentials");
	}

	@Test
	void nonBasicSchemeIsRejected() {
		assertThatThrownBy(() -> authenticator.authenticate("Bearer abc"))
				.isInstanceOf(ClientAuthenticator.ClientAuthenticationException.class)
				.hasMessage("missing client credentials");
	}

	@Test
	void malformedBase64IsRejected() {
		assertThatThrownBy(() -> authenticator.authenticate("Basic !!!not-base64!!!"))
				.isInstanceOf(ClientAuthenticator.ClientAuthenticationException.class)
				.hasMessage("missing client credentials");
	}

	@Test
	void unknownClientIsRejected() {
		when(clientRegistryClient.getClient("ghost")).thenThrow(new ClientRegistryClient.ClientNotFoundException());

		assertThatThrownBy(() -> authenticator.authenticate(basic("ghost", "secret")))
				.isInstanceOf(ClientAuthenticator.ClientAuthenticationException.class)
				.hasMessage("unknown client");
	}

	@Test
	void wrongSecretIsRejected() {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());

		assertThatThrownBy(() -> authenticator.authenticate(basic("my-client", "wrong")))
				.isInstanceOf(ClientAuthenticator.ClientAuthenticationException.class)
				.hasMessage("bad client credentials");
	}
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd token && ./gradlew test --tests ClientAuthenticatorTest --no-daemon`
Expected: FAIL — `ClientAuthenticator` 없음

- [ ] **Step 3: ClientAuthenticator 구현**

`ClientAuthenticator.java`:

```java
package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.ClientRegistryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class ClientAuthenticator {

	/**
	 * Authorization 헤더의 Basic 자격증명으로 client 를 인증한다.
	 *      token · introspection · revocation 세 엔드포인트가 같은 절차를 쓰므로 여기 한 곳에 둔다.
	 *
	 * 주의. 실패 사유(헤더 없음 / 미등록 client / 틀린 secret)를 예외 메시지로 구분하지만,
	 *      셋 다 401 invalid_client 로 응답한다. 상태 코드를 갈라 주면 어떤 client_id 가 등록돼 있는지
	 *      알려주는 셈이라 열거 공격을 돕는다.
	 */

	private final ClientRegistryClient clientRegistryClient;
	private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

	public static class ClientAuthenticationException extends RuntimeException {
		public ClientAuthenticationException(String message) {
			super(message);
		}
	}

	public ClientInfo authenticate(String authorizationHeader) {

		String[] credentials = parseBasic(authorizationHeader);
		if (credentials == null) {
			throw new ClientAuthenticationException("missing client credentials");
		}

		ClientInfo client;
		try {
			client = clientRegistryClient.getClient(credentials[0]);
		} catch (ClientRegistryClient.ClientNotFoundException e) {
			throw new ClientAuthenticationException("unknown client");
		}
		if (client == null || !passwordEncoder.matches(credentials[1], client.clientSecretHash())) {
			throw new ClientAuthenticationException("bad client credentials");
		}
		return client;
	}

	private String[] parseBasic(String authorization) {
		if (authorization == null || !authorization.startsWith("Basic ")) {
			return null;
		}
		String decoded;
		try {
			decoded = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return null; // 잘못된 base64 -> invalid_client
		}
		int idx = decoded.indexOf(':');
		if (idx < 0) {
			return null;
		}
		return new String[]{decoded.substring(0, idx), decoded.substring(idx + 1)};
	}
}
```

- [ ] **Step 4: TokenEndpointController 를 ClientAuthenticator 로 교체**

`TokenEndpointController` 에서 다음을 수행한다.

1. 필드에 `private final ClientAuthenticator clientAuthenticator;` 를 추가하고, `clientRegistryClient` 필드와 `passwordEncoder` 필드를 **제거**한다.
2. `token(...)` 의 "1. client 인증 (Basic)" 블록(기존 `parseBasic` 호출부터 `bad client credentials` 검사까지)을 다음으로 교체한다.

```java
		// 1. client 인증 (Basic)
		ClientInfo client;
		try {
			client = clientAuthenticator.authenticate(authorization);
		} catch (ClientAuthenticator.ClientAuthenticationException e) {
			return error(HttpStatus.UNAUTHORIZED, "invalid_client", e.getMessage());
		}
```

3. 클래스 하단의 `private String[] parseBasic(String authorization)` 메서드를 **삭제**한다.
4. 더 이상 쓰이지 않는 import(`ClientRegistryClient`, `PasswordEncoderFactories`, `PasswordEncoder`, `Base64`)를 정리한다.

- [ ] **Step 5: token 전체 테스트 통과 확인**

Run: `cd token && ./gradlew test --no-daemon --rerun-tasks`
Expected: PASS — 기존 53 + 신규 6 = 59 tests

기존 `TokenEndpointControllerTest` 는 `@MockitoBean ClientRegistryClient` 를 쓰고 있다. 컨트롤러가 더 이상 그 빈을 직접 주입받지 않아도 `ClientAuthenticator` 가 주입받으므로 컨텍스트는 그대로 뜬다. 기존 단언(401 메시지 3종 포함)이 전부 통과해야 한다 — **하나라도 깨지면 리팩터링이 동작을 바꾼 것이므로 테스트를 고치지 말고 구현을 고쳐라.**

- [ ] **Step 6: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/token
git commit -m "microservice: extract client authenticator from token endpoint

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 7: token — TokenStateClient + code 교환 시 refresh 발급

**Files:**
- Create: `microservice/token/src/main/java/dev/starryeye/token/client/TokenStateClient.java`
- Create: `microservice/token/src/main/java/dev/starryeye/token/client/IssuedRefreshToken.java`
- Create: `microservice/token/src/main/java/dev/starryeye/token/client/RotateResult.java`
- Create: `microservice/token/src/main/java/dev/starryeye/token/client/RefreshTokenInfo.java`
- Modify: `microservice/token/src/main/java/dev/starryeye/token/dto/TokenResponse.java`
- Modify: `microservice/token/src/main/java/dev/starryeye/token/TokenEndpointController.java`
- Modify: `microservice/token/src/main/resources/application.yml`
- Test: `microservice/token/src/test/java/dev/starryeye/token/TokenEndpointControllerTest.java` (수정)

**Interfaces:**
- Consumes: `AuthorizationCodeData(clientId, redirectUri, scope, sub, codeChallenge, nonce, authTime)`, `ClientInfo.grantTypes()`
- Produces: `TokenStateClient.issue(String clientId, String sub, String scope, long authTime) → IssuedRefreshToken`, `TokenStateClient.rotate(String refreshToken, String clientId) → RotateResult`, `TokenStateClient.revoke(String refreshToken, String clientId) → boolean`, `TokenStateClient.introspect(String refreshToken) → RefreshTokenInfo`, `TokenResponse(access_token, token_type, expires_in, scope, id_token, refresh_token)`

- [ ] **Step 1: application.yml 에 base url 추가**

`token/src/main/resources/application.yml` 의 `my:` 블록에 한 줄 추가한다.

```yaml
  token-state-base-url: http://localhost:8087
```

- [ ] **Step 2: 수신 record 작성**

`client/IssuedRefreshToken.java`:

```java
package dev.starryeye.token.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IssuedRefreshToken(String refreshToken, long expiresAt, String familyId) {
}
```

`client/RotateResult.java`:

```java
package dev.starryeye.token.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RotateResult(
		String status,
		String sub,
		String scope,
		long authTime,
		String refreshToken,
		long expiresAt
) {

	public boolean isRotated() {
		return "ROTATED".equals(status);
	}
}
```

`client/RefreshTokenInfo.java`:

```java
package dev.starryeye.token.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RefreshTokenInfo(boolean active, String sub, String clientId, String scope, long exp, long iat) {
}
```

- [ ] **Step 3: TokenStateClient 작성**

`client/TokenStateClient.java`:

```java
package dev.starryeye.token.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class TokenStateClient {

	/**
	 * token-state 의 refresh token 상태 API 를 호출한다.
	 *
	 * 주의. 어떤 호출도 예외를 잡지 않는다(fail-closed). 상태를 바꾸지 못했거나 확인하지 못했는데 성공한 것처럼
	 *      진행하면, 폐기되지 않은 토큰을 폐기했다고 응답하거나 살아있는 토큰을 죽었다고 응답하게 된다.
	 *      전파된 예외는 OAuth2ExceptionHandler 가 server_error 로 정규화한다.
	 */

	private final RestClient restClient;

	public TokenStateClient(RestClient.Builder builder, @Value("${my.token-state-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public IssuedRefreshToken issue(String clientId, String sub, String scope, long authTime) {
		return restClient.post()
				.uri("/internal/refresh-tokens")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("clientId", clientId, "sub", sub, "scope", scope, "authTime", authTime))
				.retrieve()
				.body(IssuedRefreshToken.class);
	}

	public RotateResult rotate(String refreshToken, String clientId) {
		return restClient.post()
				.uri("/internal/refresh-tokens/rotate")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("refreshToken", refreshToken, "clientId", clientId))
				.retrieve()
				.body(RotateResult.class);
	}

	public boolean revoke(String refreshToken, String clientId) {
		Map<?, ?> response = restClient.post()
				.uri("/internal/refresh-tokens/revoke")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("refreshToken", refreshToken, "clientId", clientId))
				.retrieve()
				.body(Map.class);
		return response != null && Boolean.TRUE.equals(response.get("revoked"));
	}

	public RefreshTokenInfo introspect(String refreshToken) {
		return restClient.post()
				.uri("/internal/refresh-tokens/introspect")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("refreshToken", refreshToken))
				.retrieve()
				.body(RefreshTokenInfo.class);
	}
}
```

- [ ] **Step 4: TokenResponse 에 refresh_token 추가**

`dto/TokenResponse.java`:

```java
package dev.starryeye.token.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(String access_token, String token_type, long expires_in, String scope,
		String id_token, String refresh_token) {
}
```

- [ ] **Step 5: 실패하는 테스트 작성 (기존 테스트 파일에 추가)**

`TokenEndpointControllerTest.java` 에 `@MockitoBean TokenStateClient tokenStateClient;` 필드를 추가하고, 기존 `clientInfo()` 헬퍼의 grantTypes 를 `List.of("authorization_code", "refresh_token")` 로 바꾼 뒤 아래 두 테스트를 추가한다. 기존 테스트의 `new TokenResponse(...)` 직접 생성은 없으므로 다른 수정은 필요 없다.

```java
	// offline_access 동의 + refresh_token grant 등록, 둘 다 있어야 refresh token 이 나온다
	@Test
	void offlineAccessScopeIssuesRefreshToken() throws Exception {
		when(codeStore.consume("code-1")).thenReturn(Optional.of(new AuthorizationCodeData(
				"my-client", "http://127.0.0.1:8080/callback", "openid offline_access", "user-sub-0001",
				"E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", "nonce-1", 1700000000L)));
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(signingClient.sign(any())).thenReturn("signed-access-token");
		when(idTokenIssuer.issue(any(), any(), any(), any(), anyLong(), any())).thenReturn("signed-id-token");
		when(tokenStateClient.issue(eq("my-client"), eq("user-sub-0001"), eq("openid offline_access"), eq(1700000000L)))
				.thenReturn(new IssuedRefreshToken("refresh-token-1", 1800000000L, "family-1"));

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "authorization_code")
						.param("code", "code-1")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.refresh_token").value("refresh-token-1"));
	}

	// 동의하지 않았으면 발급하지 않는다. token-state 를 부르지도 않는다.
	@Test
	void withoutOfflineAccessScopeNoRefreshTokenIsIssued() throws Exception {
		when(codeStore.consume("code-1")).thenReturn(Optional.of(new AuthorizationCodeData(
				"my-client", "http://127.0.0.1:8080/callback", "openid profile", "user-sub-0001",
				"E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", "nonce-1", 1700000000L)));
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(signingClient.sign(any())).thenReturn("signed-access-token");
		when(idTokenIssuer.issue(any(), any(), any(), any(), anyLong(), any())).thenReturn("signed-id-token");

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "authorization_code")
						.param("code", "code-1")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.refresh_token").doesNotExist());

		verify(tokenStateClient, never()).issue(any(), any(), any(), anyLong());
	}
```

필요한 import 를 추가한다: `dev.starryeye.token.client.IssuedRefreshToken`, `dev.starryeye.token.client.TokenStateClient`, `static org.mockito.ArgumentMatchers.eq`.

- [ ] **Step 6: 테스트 실패 확인**

Run: `cd token && ./gradlew test --tests TokenEndpointControllerTest --no-daemon`
Expected: FAIL — `refresh_token` 이 응답에 없음

- [ ] **Step 7: TokenEndpointController 에 refresh 발급 추가**

`token(...)` 의 마지막 `return ResponseEntity.ok(new TokenResponse(...))` 직전에 다음을 넣고, 반환문을 6번째 인자를 갖도록 바꾼다.

```java
		// offline_access 동의 + refresh_token grant 등록, 둘 다 있어야 refresh token 을 발급한다.
		// 앞은 사용자가 준 허락이고 뒤는 관리자가 정한 client 능력이다. 서로 다른 질문이라 둘 다 본다.
		// grant 등록 없이 발급하면 client 가 평생 쓸 수 없는 토큰을 쥐게 된다.
		String refreshToken = null;
		List<String> grantedScopes = Arrays.asList(data.scope().split(" "));
		if (grantedScopes.contains("offline_access") && client.grantTypes().contains("refresh_token")) {
			refreshToken = tokenStateClient
					.issue(client.clientId(), data.sub(), data.scope(), data.authTime())
					.refreshToken();
		}

		return ResponseEntity.ok(new TokenResponse(jwt, "Bearer", accessTokenTtlSeconds, data.scope(),
				idToken, refreshToken));
```

필드에 `private final TokenStateClient tokenStateClient;` 를 추가한다.

주의. `TokenStateClient.issue` 는 `(clientId, sub, scope, authTime)` 순이고 앞의 셋이 모두 String 이라 순서를 바꿔 넘겨도 컴파일된다. Step 5 의 테스트가 `eq("my-client")` · `eq("user-sub-0001")` 로 각 자리를 고정하므로 순서를 틀리면 테스트가 잡는다.

- [ ] **Step 8: 테스트 통과 확인**

Run: `cd token && ./gradlew test --no-daemon --rerun-tasks`
Expected: PASS — 59 + 2 = 61 tests

- [ ] **Step 9: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/token
git commit -m "microservice: issue refresh token when offline_access is granted

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 8: token — refresh grant

**Files:**
- Create: `microservice/token/src/main/java/dev/starryeye/token/AccessTokenIssuer.java`
- Create: `microservice/token/src/main/java/dev/starryeye/token/RefreshTokenGrantService.java`
- Create: `microservice/token/src/main/java/dev/starryeye/token/GrantResult.java`
- Modify: `microservice/token/src/main/java/dev/starryeye/token/TokenEndpointController.java`
- Test: `microservice/token/src/test/java/dev/starryeye/token/RefreshTokenGrantServiceTest.java`

**Interfaces:**
- Consumes: `TokenStateClient.rotate(String, String) → RotateResult`, `SigningClient.sign(Map<String,Object>) → String`, `IdTokenIssuer.issue(String sub, String clientId, String scope, String nonce, long authTime, String accessToken) → String`, `ClientInfo.grantTypes()`
- Produces: `AccessTokenIssuer.issue(String sub, String clientId, String scope) → String`, `RefreshTokenGrantService.grant(ClientInfo client, String refreshToken, String requestedScope) → GrantResult`, `GrantResult(boolean success, String error, String errorDescription, TokenResponse response)`

- [ ] **Step 1: 실패하는 테스트 작성**

`RefreshTokenGrantServiceTest.java`:

```java
package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.RotateResult;
import dev.starryeye.token.client.TokenStateClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenGrantServiceTest {

	private final TokenStateClient tokenStateClient = mock(TokenStateClient.class);
	private final AccessTokenIssuer accessTokenIssuer = mock(AccessTokenIssuer.class);
	private final IdTokenIssuer idTokenIssuer = mock(IdTokenIssuer.class);

	private final RefreshTokenGrantService service =
			new RefreshTokenGrantService(tokenStateClient, accessTokenIssuer, idTokenIssuer, 300L);

	private ClientInfo client() {
		return new ClientInfo("my-client", List.of("http://127.0.0.1:8080/callback"),
				List.of("openid", "profile", "offline_access"), "{bcrypt}x",
				List.of("authorization_code", "refresh_token"));
	}

	private RotateResult rotated(String scope) {
		return new RotateResult("ROTATED", "user-sub-0001", scope, 1700000000L, "new-refresh", 1800000000L);
	}

	@Test
	void rotatedGrantReturnsNewAccessAndRefreshToken() {
		when(tokenStateClient.rotate("old-refresh", "my-client")).thenReturn(rotated("openid offline_access"));
		when(accessTokenIssuer.issue(any(), any(), any())).thenReturn("new-access");
		when(idTokenIssuer.issue(any(), any(), any(), any(), anyLong(), any())).thenReturn("new-id");

		GrantResult result = service.grant(client(), "old-refresh", null);

		assertThat(result.success()).isTrue();
		assertThat(result.response().access_token()).isEqualTo("new-access");
		assertThat(result.response().refresh_token()).isEqualTo("new-refresh");
		assertThat(result.response().id_token()).isEqualTo("new-id");
		assertThat(result.response().scope()).isEqualTo("openid offline_access");
	}

	// OIDC Core 12.2: refresh 로 낸 id token 에는 nonce 를 넣지 않고 auth_time 은 원래 인증 시각을 유지한다
	@Test
	void refreshedIdTokenHasNoNonceAndKeepsOriginalAuthTime() {
		when(tokenStateClient.rotate("old-refresh", "my-client")).thenReturn(rotated("openid"));
		when(accessTokenIssuer.issue(any(), any(), any())).thenReturn("new-access");
		when(idTokenIssuer.issue(any(), any(), any(), any(), anyLong(), any())).thenReturn("new-id");

		service.grant(client(), "old-refresh", null);

		verify(idTokenIssuer).issue(eq("user-sub-0001"), eq("my-client"), eq("openid"),
				isNull(), eq(1700000000L), eq("new-access"));
	}

	// 회전 실패 사유는 전부 invalid_grant 로 뭉갠다
	@Test
	void reuseDetectedBecomesInvalidGrant() {
		when(tokenStateClient.rotate("old-refresh", "my-client"))
				.thenReturn(new RotateResult("REUSE_DETECTED", null, null, 0L, null, 0L));

		GrantResult result = service.grant(client(), "old-refresh", null);

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("invalid_grant");
		verify(accessTokenIssuer, never()).issue(any(), any(), any());
	}

	@Test
	void notFoundBecomesInvalidGrant() {
		when(tokenStateClient.rotate("old-refresh", "my-client"))
				.thenReturn(new RotateResult("NOT_FOUND", null, null, 0L, null, 0L));

		assertThat(service.grant(client(), "old-refresh", null).error()).isEqualTo("invalid_grant");
	}

	@Test
	void expiredBecomesInvalidGrant() {
		when(tokenStateClient.rotate("old-refresh", "my-client"))
				.thenReturn(new RotateResult("EXPIRED", null, null, 0L, null, 0L));

		assertThat(service.grant(client(), "old-refresh", null).error()).isEqualTo("invalid_grant");
	}

	// RFC 6749 6: 축소 요청은 저장된 scope 의 부분집합만 허용한다
	@Test
	void narrowedScopeAppliesToThisAccessTokenOnly() {
		when(tokenStateClient.rotate("old-refresh", "my-client")).thenReturn(rotated("openid profile offline_access"));
		when(accessTokenIssuer.issue(any(), any(), any())).thenReturn("new-access");

		GrantResult result = service.grant(client(), "old-refresh", "profile");

		assertThat(result.success()).isTrue();
		assertThat(result.response().scope()).isEqualTo("profile");
		ArgumentCaptor<String> scopeCaptor = ArgumentCaptor.forClass(String.class);
		verify(accessTokenIssuer).issue(any(), any(), scopeCaptor.capture());
		assertThat(scopeCaptor.getValue()).isEqualTo("profile");
		// openid 를 뺐으므로 이번 응답에는 id token 이 없다
		assertThat(result.response().id_token()).isNull();
	}

	@Test
	void scopeBeyondStoredScopeIsRejected() {
		when(tokenStateClient.rotate("old-refresh", "my-client")).thenReturn(rotated("openid"));

		GrantResult result = service.grant(client(), "old-refresh", "openid admin");

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("invalid_scope");
		verify(accessTokenIssuer, never()).issue(any(), any(), any());
	}

	@Test
	void clientWithoutRefreshGrantIsRejected() {
		ClientInfo noRefresh = new ClientInfo("my-client", List.of(), List.of("openid"), "{bcrypt}x",
				List.of("authorization_code"));

		GrantResult result = service.grant(noRefresh, "old-refresh", null);

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("unauthorized_client");
		verify(tokenStateClient, never()).rotate(any(), any());
	}
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd token && ./gradlew test --tests RefreshTokenGrantServiceTest --no-daemon`
Expected: FAIL — `AccessTokenIssuer` · `RefreshTokenGrantService` · `GrantResult` 없음

- [ ] **Step 3: AccessTokenIssuer 추출**

`AccessTokenIssuer.java`:

```java
package dev.starryeye.token;

import dev.starryeye.token.client.SigningClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AccessTokenIssuer {

	/**
	 * access token 의 claim 을 구성해 signing 에 서명을 위임한다.
	 *      authorization_code 와 refresh_token 두 grant 가 같은 claim 집합을 내야 하므로 한 곳에 둔다.
	 *
	 * 주의. scope claim 을 JSON 배열로 낸다. RFC 9068 은 공백 구분 문자열을 요구하지만 이 서버는 슬라이스 1부터
	 *      배열을 써 왔고 AccessTokenVerifier 도 배열로 읽는다. 형식을 바꾸려면 양쪽을 함께 바꿔야 한다.
	 */

	private final SigningClient signingClient;

	@Value("${my.issuer}")
	private String issuer;

	@Value("${my.access-token-ttl-seconds}")
	private long accessTokenTtlSeconds;

	public String issue(String sub, String clientId, String scope) {
		Instant now = Instant.now();
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("iss", issuer);
		claims.put("sub", sub);
		claims.put("aud", clientId);
		claims.put("iat", now.getEpochSecond());
		claims.put("exp", now.plusSeconds(accessTokenTtlSeconds).getEpochSecond());
		claims.put("scope", Arrays.asList(scope.split(" ")));
		return signingClient.sign(claims);
	}
}
```

- [ ] **Step 4: GrantResult 와 RefreshTokenGrantService 구현**

`GrantResult.java`:

```java
package dev.starryeye.token;

import dev.starryeye.token.dto.TokenResponse;

public record GrantResult(boolean success, String error, String errorDescription, TokenResponse response) {

	public static GrantResult ok(TokenResponse response) {
		return new GrantResult(true, null, null, response);
	}

	public static GrantResult failed(String error, String errorDescription) {
		return new GrantResult(false, error, errorDescription, null);
	}
}
```

`RefreshTokenGrantService.java`:

```java
package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.RotateResult;
import dev.starryeye.token.client.TokenStateClient;
import dev.starryeye.token.dto.TokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class RefreshTokenGrantService {

	/**
	 * refresh grant 를 처리한다. 회전은 token-state 에 한 번의 호출로 위임하고, 결과로 새 access token 과
	 *      (openid 가 있으면) id token 을 조립한다.
	 *
	 * 주의. 회전 실패 사유를 전부 invalid_grant 하나로 뭉갠다. "이미 소진됐다" 와 "그런 토큰 없다" 를 구분해 주면
	 *      공격자가 토큰의 상태를 탐색할 수 있다. 구분은 로그에만 남긴다.
	 *
	 * 주의. scope 축소(RFC 6749 6)는 이번 access token 에만 적용된다. 저장된 refresh 의 scope 는 token-state 가
	 *      그대로 유지하므로, 한 번 좁혀도 다음 회전에서 원래 범위로 돌아온다.
	 */

	private final TokenStateClient tokenStateClient;
	private final AccessTokenIssuer accessTokenIssuer;
	private final IdTokenIssuer idTokenIssuer;
	private final long accessTokenTtlSeconds;

	public RefreshTokenGrantService(
			TokenStateClient tokenStateClient,
			AccessTokenIssuer accessTokenIssuer,
			IdTokenIssuer idTokenIssuer,
			@Value("${my.access-token-ttl-seconds}") long accessTokenTtlSeconds
	) {
		this.tokenStateClient = tokenStateClient;
		this.accessTokenIssuer = accessTokenIssuer;
		this.idTokenIssuer = idTokenIssuer;
		this.accessTokenTtlSeconds = accessTokenTtlSeconds;
	}

	public GrantResult grant(ClientInfo client, String refreshToken, String requestedScope) {

		if (!client.grantTypes().contains("refresh_token")) {
			return GrantResult.failed("unauthorized_client", "client not authorized for refresh_token grant");
		}
		if (!StringUtils.hasText(refreshToken)) {
			return GrantResult.failed("invalid_request", "refresh_token is required");
		}

		RotateResult rotation = tokenStateClient.rotate(refreshToken, client.clientId());
		if (rotation == null || !rotation.isRotated()) {
			String status = (rotation == null) ? "NULL" : rotation.status();
			log.info("refresh rotation rejected. clientId={} status={}", client.clientId(), status);
			return GrantResult.failed("invalid_grant", "refresh token is not valid");
		}

		List<String> storedScopes = Arrays.asList(rotation.scope().split(" "));
		String effectiveScope = rotation.scope();
		if (StringUtils.hasText(requestedScope)) {
			List<String> requested = Arrays.asList(requestedScope.trim().split("\\s+"));
			if (!storedScopes.containsAll(requested)) {
				return GrantResult.failed("invalid_scope", "requested scope exceeds the original grant");
			}
			effectiveScope = String.join(" ", requested);
		}

		String accessToken = accessTokenIssuer.issue(rotation.sub(), client.clientId(), effectiveScope);

		String idToken = null;
		if (Arrays.asList(effectiveScope.split(" ")).contains("openid")) {
			// nonce 는 넣지 않는다. 원래 authorization 요청에 묶인 값이라 재발급 토큰에 실으면 리플레이 방어가 깨진다.
			// auth_time 은 최초 인증 시각을 그대로 유지한다. (OIDC Core 12.2)
			idToken = idTokenIssuer.issue(rotation.sub(), client.clientId(), effectiveScope,
					null, rotation.authTime(), accessToken);
		}

		return GrantResult.ok(new TokenResponse(accessToken, "Bearer", accessTokenTtlSeconds,
				effectiveScope, idToken, rotation.refreshToken()));
	}
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd token && ./gradlew test --tests RefreshTokenGrantServiceTest --no-daemon --rerun-tasks`
Expected: PASS — 8 tests

- [ ] **Step 6: TokenEndpointController 에 grant 분기 추가**

`token(...)` 의 시그니처에 파라미터 두 개를 추가한다.

```java
			@RequestParam(value = "refresh_token", required = false) String refreshTokenParam,
			@RequestParam(value = "scope", required = false) String scopeParam
```

그리고 기존의 grant type 검사

```java
		if (!"authorization_code".equals(grantType)) {
			return error(HttpStatus.BAD_REQUEST, "unsupported_grant_type", "only authorization_code is supported");
		}
```

를 다음으로 교체한다. **client 인증 블록보다 뒤**에 두어야 하므로, grant type 분기를 client 인증 다음으로 옮긴다.

```java
		if (!"authorization_code".equals(grantType) && !"refresh_token".equals(grantType)) {
			return error(HttpStatus.BAD_REQUEST, "unsupported_grant_type",
					"only authorization_code and refresh_token are supported");
		}
```

client 인증 직후, `authorization_code` 전용 검사(`client.grantTypes().contains("authorization_code")`) 앞에 refresh 분기를 넣는다.

```java
		if ("refresh_token".equals(grantType)) {
			GrantResult result = refreshTokenGrantService.grant(client, refreshTokenParam, scopeParam);
			if (!result.success()) {
				// unauthorized_client · invalid_grant · invalid_scope · invalid_request 는 RFC 6749 5.2 상 모두 400 이다
				return error(HttpStatus.BAD_REQUEST, result.error(), result.errorDescription());
			}
			return ResponseEntity.ok(result.response());
		}
```

필드에 `private final RefreshTokenGrantService refreshTokenGrantService;` 를 추가한다.

주의. grant type 검사가 client 인증보다 앞에 있으면, 인증되지 않은 요청도 "이 서버가 어떤 grant 를 지원하는지" 알아낼 수 있다. 순서를 바꾸는 김에 인증을 먼저 하도록 정렬한다.

- [ ] **Step 7: 기존 테스트 확인 및 보강**

`TokenEndpointControllerTest` 에 `@MockitoBean RefreshTokenGrantService refreshTokenGrantService;` 를 추가하고 다음 테스트를 넣는다.

```java
	@Test
	void refreshGrantDelegatesToRefreshTokenGrantService() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(refreshTokenGrantService.grant(any(), eq("old-refresh"), isNull()))
				.thenReturn(GrantResult.ok(new TokenResponse("new-access", "Bearer", 300L,
						"openid", null, "new-refresh")));

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "refresh_token")
						.param("refresh_token", "old-refresh"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.access_token").value("new-access"))
				.andExpect(jsonPath("$.refresh_token").value("new-refresh"));
	}

	@Test
	void refreshGrantFailureBecomesOAuth2Error() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(refreshTokenGrantService.grant(any(), any(), any()))
				.thenReturn(GrantResult.failed("invalid_grant", "refresh token is not valid"));

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "refresh_token")
						.param("refresh_token", "old-refresh"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_grant"));
	}

	// 인증 없는 요청은 grant type 을 알아내기 전에 막힌다
	@Test
	void unsupportedGrantTypeWithoutCredentialsIsInvalidClient() throws Exception {
		mockMvc.perform(post("/oauth2/token").param("grant_type", "password"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_client"));
	}
```

`static org.mockito.ArgumentMatchers.isNull` import 를 추가한다.

- [ ] **Step 8: token 전체 테스트 통과 확인**

Run: `cd token && ./gradlew test --no-daemon --rerun-tasks`
Expected: PASS — 61 + 8 + 3 = 72 tests

기존 `unsupportedGrantTypeReturnsError` 계열 테스트가 있다면 client 인증을 먼저 통과하도록 Basic 헤더를 추가해야 한다. **단언은 바꾸지 말고 요청만 보강하라.**

- [ ] **Step 9: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/token
git commit -m "microservice: support refresh token grant with rotation

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 9: token — introspection 엔드포인트

**Files:**
- Modify: `microservice/token/src/main/java/dev/starryeye/token/AccessTokenVerifier.java`
- Create: `microservice/token/src/main/java/dev/starryeye/token/IntrospectionController.java`
- Test: `microservice/token/src/test/java/dev/starryeye/token/IntrospectionControllerTest.java`
- Test: `microservice/token/src/test/java/dev/starryeye/token/UserInfoControllerTest.java` (생성자 인자 보강)
- Test: `microservice/token/src/test/java/dev/starryeye/token/AccessTokenVerifierTest.java` (단언 보강)

**Interfaces:**
- Consumes: `ClientAuthenticator.authenticate(String) → ClientInfo`, `TokenStateClient.introspect(String) → RefreshTokenInfo`
- Produces: `AccessTokenVerifier.VerifiedToken(String sub, List<String> scopes, String clientId, long exp, long iat)`, `POST /oauth2/introspect`

- [ ] **Step 1: VerifiedToken 확장 (기존 테스트가 깨지는 변경)**

`AccessTokenVerifier.java` 의 record 를 다음으로 바꾼다.

```java
	public record VerifiedToken(String sub, List<String> scopes, String clientId, long exp, long iat) {
	}
```

`verify(...)` 의 마지막 반환문을 다음으로 바꾼다.

```java
		List<String> audience = claims.getAudience();
		String clientId = (audience == null || audience.isEmpty()) ? null : audience.get(0);
		Date issuedAt = claims.getIssueTime();

		return new VerifiedToken(
				claims.getSubject(),
				scopes,
				clientId,
				expiration.toInstant().getEpochSecond(),
				(issuedAt == null) ? 0L : issuedAt.toInstant().getEpochSecond()
		);
```

주의. `aud` 는 JWT 에서 배열이므로 첫 원소를 client_id 로 쓴다. 이 서버는 `AccessTokenIssuer` 에서 단일 client_id 만 넣는다.

- [ ] **Step 2: 기존 테스트의 VerifiedToken 생성자 보강**

`UserInfoControllerTest` 안의 모든 `new AccessTokenVerifier.VerifiedToken("user-sub-0001", List.of(...))` 호출에 인자 셋을 덧붙인다. 예:

```java
new AccessTokenVerifier.VerifiedToken("user-sub-0001", List.of("openid"), "my-client", 1800000000L, 1700000000L)
```

**단언은 하나도 바꾸지 마라.** 시그니처만 맞추는 수정이다.

`AccessTokenVerifierTest` 의 `verifiesValidToken` 에는 새 필드를 확인하는 단언을 덧붙인다.

```java
		assertThat(verified.clientId()).isEqualTo("my-client");
		assertThat(verified.exp()).isPositive();
		assertThat(verified.iat()).isPositive();
```

해당 테스트가 만드는 JWT 의 claim 에 `aud` 와 `iat` 가 없으면 추가한다.

- [ ] **Step 3: 실패하는 introspection 테스트 작성**

`IntrospectionControllerTest.java`:

```java
package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.ClientRegistryClient;
import dev.starryeye.token.client.RefreshTokenInfo;
import dev.starryeye.token.client.SigningClient;
import dev.starryeye.token.client.TokenStateClient;
import dev.starryeye.token.client.UserDirectoryClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IntrospectionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ClientRegistryClient clientRegistryClient;

	@MockitoBean
	private AccessTokenVerifier accessTokenVerifier;

	@MockitoBean
	private TokenStateClient tokenStateClient;

	@MockitoBean
	private SigningClient signingClient;

	@MockitoBean
	private UserDirectoryClient userDirectoryClient;

	@MockitoBean
	private AuthorizationCodeStore codeStore;

	private static final String BASIC = "Basic " + Base64.getEncoder()
			.encodeToString("article-api:secret".getBytes(StandardCharsets.UTF_8));

	private ClientInfo articleApi() {
		String hash = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("secret");
		return new ClientInfo("article-api", List.of(), List.of(), hash, List.of());
	}

	@Test
	void accessTokenIsIntrospectedLocally() throws Exception {
		when(clientRegistryClient.getClient("article-api")).thenReturn(articleApi());
		when(accessTokenVerifier.verify("access-token")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("user-sub-0001", List.of("openid", "profile"),
						"my-client", 1800000000L, 1700000000L));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", BASIC)
						.param("token", "access-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.sub").value("user-sub-0001"))
				.andExpect(jsonPath("$.client_id").value("my-client"))
				.andExpect(jsonPath("$.scope").value("openid profile"))
				.andExpect(jsonPath("$.token_type").value("Bearer"));

		// access token 은 폐기 대상이 아니므로 token-state 를 조회하지 않는다
		verify(tokenStateClient, never()).introspect(any());
	}

	@Test
	void nonJwtTokenFallsBackToTokenState() throws Exception {
		when(clientRegistryClient.getClient("article-api")).thenReturn(articleApi());
		when(accessTokenVerifier.verify("opaque-refresh"))
				.thenThrow(new AccessTokenVerifier.InvalidTokenException("malformed token"));
		when(tokenStateClient.introspect("opaque-refresh")).thenReturn(
				new RefreshTokenInfo(true, "user-sub-0001", "my-client", "openid offline_access",
						1800000000L, 1700000000L));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", BASIC)
						.param("token", "opaque-refresh"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.sub").value("user-sub-0001"))
				.andExpect(jsonPath("$.scope").value("openid offline_access"))
				.andExpect(jsonPath("$.token_type").doesNotExist()); // refresh 에는 token_type 이 없다
	}

	// 비활성 응답에서는 어떤 정보도 새지 않아야 한다 (RFC 7662 2.2)
	@Test
	void inactiveResponseContainsOnlyActiveFalse() throws Exception {
		when(clientRegistryClient.getClient("article-api")).thenReturn(articleApi());
		when(accessTokenVerifier.verify("dead"))
				.thenThrow(new AccessTokenVerifier.InvalidTokenException("token expired"));
		when(tokenStateClient.introspect("dead"))
				.thenReturn(new RefreshTokenInfo(false, null, null, null, 0L, 0L));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", BASIC)
						.param("token", "dead"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false))
				.andExpect(jsonPath("$.sub").doesNotExist())
				.andExpect(jsonPath("$.client_id").doesNotExist())
				.andExpect(jsonPath("$.scope").doesNotExist())
				.andExpect(jsonPath("$.exp").doesNotExist());
	}

	@Test
	void missingCredentialsReturnsInvalidClient() throws Exception {
		mockMvc.perform(post("/oauth2/introspect").param("token", "whatever"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_client"));

		verify(tokenStateClient, never()).introspect(any());
	}

	@Test
	void wrongSecretReturnsInvalidClient() throws Exception {
		when(clientRegistryClient.getClient("article-api")).thenReturn(articleApi());
		String wrong = "Basic " + Base64.getEncoder()
				.encodeToString("article-api:nope".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", wrong)
						.param("token", "whatever"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_client"));
	}

	@Test
	void missingTokenParameterReturnsInvalidRequest() throws Exception {
		when(clientRegistryClient.getClient("article-api")).thenReturn(articleApi());

		mockMvc.perform(post("/oauth2/introspect").header("Authorization", BASIC))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_request"));
	}
}
```

- [ ] **Step 4: 테스트 실패 확인**

Run: `cd token && ./gradlew test --tests IntrospectionControllerTest --no-daemon`
Expected: FAIL — `/oauth2/introspect` 없음(404)

- [ ] **Step 5: IntrospectionController 구현**

`IntrospectionController.java`:

```java
package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.RefreshTokenInfo;
import dev.starryeye.token.client.TokenStateClient;
import dev.starryeye.token.dto.OAuth2ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class IntrospectionController {

	/**
	 * 토큰의 활성 여부와 claim 을 돌려준다. (RFC 7662)
	 *
	 * 주의. token_type_hint 는 힌트일 뿐이라 틀릴 수 있다(RFC 7662 2.1). 힌트를 믿고 분기하면 잘못된 힌트가
	 *      멀쩡한 토큰을 비활성으로 만든다. 그래서 JWT 파싱을 먼저 시도하고 실패했을 때만 token-state 에 묻는다.
	 *
	 * 주의. access token 은 로컬 검증만 하고 token-state 를 조회하지 않는다. 이 서버는 폐기를 refresh 한정으로
	 *      정했으므로 access token 의 활성 여부는 서명과 exp 만으로 결정된다.
	 *
	 * 주의. 비활성 응답은 {"active": false} 하나뿐이다(RFC 7662 2.2). 만료 · 폐기 · 형식 오류를 구분해 주면
	 *      토큰을 쥔 공격자가 그 토큰의 내력을 알아낼 수 있다.
	 *
	 * 주의. 인증된 등록 client 면 누구의 토큰이든 조회할 수 있다. resource server 가 검사하는 토큰은 언제나
	 *      다른 client 에게 발급된 것이므로, "자기 토큰만" 으로 제한하면 기능이 성립하지 않는다.
	 *      권한을 좁히려면 client_credentials grant 로 받은 토큰의 introspect scope 를 보는 것이 정석이며,
	 *      이 서버에는 아직 그 grant 가 없다.
	 */

	private final ClientAuthenticator clientAuthenticator;
	private final AccessTokenVerifier accessTokenVerifier;
	private final TokenStateClient tokenStateClient;

	@Value("${my.issuer}")
	private String issuer;

	@PostMapping(value = "/oauth2/introspect", produces = "application/json")
	public ResponseEntity<?> introspect(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@RequestParam(value = "token", required = false) String token,
			@RequestParam(value = "token_type_hint", required = false) String tokenTypeHint
	) {
		ClientInfo caller;
		try {
			caller = clientAuthenticator.authenticate(authorization);
		} catch (ClientAuthenticator.ClientAuthenticationException e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new OAuth2ErrorResponse("invalid_client", e.getMessage()));
		}
		if (!StringUtils.hasText(token)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(new OAuth2ErrorResponse("invalid_request", "token is required"));
		}

		try {
			AccessTokenVerifier.VerifiedToken verified = accessTokenVerifier.verify(token);
			return ResponseEntity.ok(activeAccessToken(verified));
		} catch (AccessTokenVerifier.InvalidTokenException e) {
			// JWT 가 아니거나 무효다. refresh token 일 수 있으므로 소유자에게 묻는다.
			RefreshTokenInfo info = tokenStateClient.introspect(token);
			if (info == null || !info.active()) {
				return ResponseEntity.ok(Map.of("active", false));
			}
			return ResponseEntity.ok(activeRefreshToken(info));
		}
	}

	private Map<String, Object> activeAccessToken(AccessTokenVerifier.VerifiedToken verified) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("active", true);
		response.put("sub", verified.sub());
		response.put("client_id", verified.clientId());
		response.put("scope", String.join(" ", verified.scopes()));
		response.put("exp", verified.exp());
		response.put("iat", verified.iat());
		response.put("iss", issuer);
		response.put("token_type", "Bearer"); // RFC 6749 7.1 이 정의하는 access token 의 사용 방식
		return response;
	}

	private Map<String, Object> activeRefreshToken(RefreshTokenInfo info) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("active", true);
		response.put("sub", info.sub());
		response.put("client_id", info.clientId());
		response.put("scope", info.scope());
		response.put("exp", info.exp());
		response.put("iat", info.iat());
		response.put("iss", issuer);
		// token_type 은 넣지 않는다. refresh token 은 리소스 접근에 쓰이지 않는다.
		return response;
	}
}
```

- [ ] **Step 6: token 전체 테스트 통과 확인**

Run: `cd token && ./gradlew test --no-daemon --rerun-tasks`
Expected: PASS — 72 + 6 = 78 tests

- [ ] **Step 7: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/token
git commit -m "microservice: add token introspection endpoint

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 10: token — revocation 엔드포인트

**Files:**
- Create: `microservice/token/src/main/java/dev/starryeye/token/RevocationController.java`
- Test: `microservice/token/src/test/java/dev/starryeye/token/RevocationControllerTest.java`

**Interfaces:**
- Consumes: `ClientAuthenticator.authenticate(String) → ClientInfo`, `TokenStateClient.revoke(String, String) → boolean`
- Produces: `POST /oauth2/revoke`

- [ ] **Step 1: 실패하는 테스트 작성**

`RevocationControllerTest.java`:

```java
package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.ClientRegistryClient;
import dev.starryeye.token.client.SigningClient;
import dev.starryeye.token.client.TokenStateClient;
import dev.starryeye.token.client.UserDirectoryClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RevocationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ClientRegistryClient clientRegistryClient;

	@MockitoBean
	private TokenStateClient tokenStateClient;

	@MockitoBean
	private AccessTokenVerifier accessTokenVerifier;

	@MockitoBean
	private SigningClient signingClient;

	@MockitoBean
	private UserDirectoryClient userDirectoryClient;

	@MockitoBean
	private AuthorizationCodeStore codeStore;

	private static final String BASIC = "Basic " + Base64.getEncoder()
			.encodeToString("my-client:secret".getBytes(StandardCharsets.UTF_8));

	private ClientInfo clientInfo() {
		String hash = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("secret");
		return new ClientInfo("my-client", List.of("http://127.0.0.1:8080/callback"),
				List.of("openid"), hash, List.of("authorization_code", "refresh_token"));
	}

	@Test
	void revokingOwnRefreshTokenReturns200() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(tokenStateClient.revoke("refresh-1", "my-client")).thenReturn(true);

		mockMvc.perform(post("/oauth2/revoke")
						.header("Authorization", BASIC)
						.param("token", "refresh-1"))
				.andExpect(status().isOk());
	}

	// RFC 7009 2.2: 존재하지 않는 토큰에도 200 이다. 오류로 갈라 주면 토큰 존재 여부를 탐색할 수 있다.
	@Test
	void revokingUnknownTokenAlsoReturns200() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(tokenStateClient.revoke("ghost", "my-client")).thenReturn(false);

		mockMvc.perform(post("/oauth2/revoke")
						.header("Authorization", BASIC)
						.param("token", "ghost"))
				.andExpect(status().isOk());
	}

	// 이 서버는 access token 을 폐기하지 않는다 (RFC 7009 2 는 access token 폐기를 MAY 로 둔다)
	@Test
	void accessTokenHintIsAcceptedWithoutRevoking() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());

		mockMvc.perform(post("/oauth2/revoke")
						.header("Authorization", BASIC)
						.param("token", "some-jwt")
						.param("token_type_hint", "access_token"))
				.andExpect(status().isOk());

		verify(tokenStateClient, never()).revoke(any(), any());
	}

	@Test
	void missingCredentialsReturnsInvalidClient() throws Exception {
		mockMvc.perform(post("/oauth2/revoke").param("token", "refresh-1"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_client"));

		verify(tokenStateClient, never()).revoke(any(), any());
	}

	@Test
	void missingTokenParameterReturnsInvalidRequest() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());

		mockMvc.perform(post("/oauth2/revoke").header("Authorization", BASIC))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_request"));
	}
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd token && ./gradlew test --tests RevocationControllerTest --no-daemon`
Expected: FAIL — `/oauth2/revoke` 없음(404)

- [ ] **Step 3: RevocationController 구현**

`RevocationController.java`:

```java
package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.TokenStateClient;
import dev.starryeye.token.dto.OAuth2ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class RevocationController {

	/**
	 * refresh token 을 폐기한다. (RFC 7009)
	 *
	 * 주의. 폐기 성공 여부와 무관하게 200 을 낸다(RFC 7009 2.2). 존재하지 않는 토큰, 이미 폐기된 토큰,
	 *      다른 client 의 토큰이 전부 같은 응답이라 토큰의 존재 여부를 탐색할 수 없다.
	 *
	 * 주의. refresh token 하나를 폐기하면 그 계열 전체가 죽는다. refresh token 은 하나의 grant 를 대표하므로,
	 *      폐기는 그 grant 를 끝내는 것이다(RFC 7009 2.1).
	 *
	 * 주의. access token 은 폐기하지 않는다. RFC 7009 2 가 access token 폐기를 MAY 로 두므로 표준 위반이 아니며,
	 *      이 서버는 access token 을 짧은 TTL 로 만료시키는 쪽을 택했다. JWT 자가검증의 이점을 지키기 위함이다.
	 */

	private final ClientAuthenticator clientAuthenticator;
	private final TokenStateClient tokenStateClient;

	@PostMapping(value = "/oauth2/revoke", produces = "application/json")
	public ResponseEntity<?> revoke(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@RequestParam(value = "token", required = false) String token,
			@RequestParam(value = "token_type_hint", required = false) String tokenTypeHint
	) {
		ClientInfo client;
		try {
			client = clientAuthenticator.authenticate(authorization);
		} catch (ClientAuthenticator.ClientAuthenticationException e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new OAuth2ErrorResponse("invalid_client", e.getMessage()));
		}
		if (!StringUtils.hasText(token)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(new OAuth2ErrorResponse("invalid_request", "token is required"));
		}

		if ("access_token".equals(tokenTypeHint)) {
			log.debug("access token revocation requested. this server expires access tokens instead. clientId={}",
					client.clientId());
			return ResponseEntity.ok().build();
		}

		boolean revoked = tokenStateClient.revoke(token, client.clientId());
		log.info("revocation processed. clientId={} revoked={}", client.clientId(), revoked);
		return ResponseEntity.ok().build();
	}
}
```

- [ ] **Step 4: token 전체 테스트 통과 확인**

Run: `cd token && ./gradlew test --no-daemon --rerun-tasks`
Expected: PASS — 78 + 5 = 83 tests

- [ ] **Step 5: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/token
git commit -m "microservice: add token revocation endpoint

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 11: seed · gateway 라우팅 · discovery

**Files:**
- Modify: `microservice/client-registry/src/main/java/dev/starryeye/client_registry/ClientSeedInitializer.java`
- Modify: `microservice/gateway/nginx.conf`
- Modify: `microservice/token/src/main/java/dev/starryeye/token/TokenEndpointController.java` (metadata)
- Test: `microservice/token/src/test/java/dev/starryeye/token/TokenEndpointControllerTest.java` (discovery 단언 보강)

**Interfaces:**
- Consumes: `ClientEntity.builder()` (clientId, clientSecretHash, redirectUris, scopes, grantTypes)
- Produces: seed client 2개, gateway 경로 2개, discovery 필드 4개

- [ ] **Step 1: ClientSeedInitializer 를 client 별 독립 seed 로 바꾼다**

```java
	@Override
	public void run(ApplicationArguments args) {
		PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

		if (!repository.existsById("my-client")) {
			repository.save(ClientEntity.builder()
					.clientId("my-client")
					.clientSecretHash(encoder.encode("secret"))
					.redirectUris("http://127.0.0.1:8080/callback")
					.scopes("openid,profile,email,offline_access")
					.grantTypes("authorization_code,refresh_token")
					.build());
		}

		// resource server 역할. 인가 흐름에 참여하지 않고 introspection 만 호출한다.
		// grant_types 가 비어 있어 토큰 요청은 기존 검사에서 자연히 거절된다.
		if (!repository.existsById("article-api")) {
			repository.save(ClientEntity.builder()
					.clientId("article-api")
					.clientSecretHash(encoder.encode("secret"))
					.redirectUris("")
					.scopes("")
					.grantTypes("")
					.build());
		}
	}
```

`import org.springframework.security.crypto.password.PasswordEncoder;` 를 추가한다.

주의. `ddl-auto: update` 이므로 이미 `my-client` 행이 있는 DB 에서는 이 seed 가 값을 갱신하지 않는다. 기존 행의 `scopes` 에 `offline_access` 가, `grant_types` 에 `refresh_token` 이 없으면 e2e 가 `invalid_scope` 로 실패한다. Task 12 의 e2e 절차에서 확인 · 보정한다.

- [ ] **Step 2: nginx.conf 에 경로 2개 추가**

`gateway/nginx.conf` 의 back-channel 블록에 다음 두 줄을 추가한다.

```
    location /oauth2/introspect { proxy_pass http://token-upstream; proxy_set_header X-Forwarded-Host $http_host; }
    location /oauth2/revoke     { proxy_pass http://token-upstream; proxy_set_header X-Forwarded-Host $http_host; }
```

주의. token-state(8087) 는 여기에 등장하지 않는다. `/internal/**` 은 gateway 가 라우팅하지 않으므로 외부에서 도달할 수 없다.

- [ ] **Step 3: discovery 메타데이터 갱신**

`TokenEndpointController.metadata()` 를 다음처럼 고친다.

```java
		metadata.put("introspection_endpoint", issuer + "/oauth2/introspect");
		metadata.put("revocation_endpoint", issuer + "/oauth2/revoke");
		metadata.put("code_challenge_methods_supported", List.of("S256"));
		metadata.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
		metadata.put("response_types_supported", List.of("code"));
		metadata.put("subject_types_supported", List.of("public"));
		metadata.put("id_token_signing_alg_values_supported", List.of("RS256"));
		metadata.put("introspection_endpoint_auth_methods_supported", List.of("client_secret_basic"));
		metadata.put("revocation_endpoint_auth_methods_supported", List.of("client_secret_basic"));
		metadata.put("scopes_supported", List.of("openid", "profile", "email", "offline_access"));
```

`userinfo_endpoint` 아래에 introspection · revocation 두 줄을 넣고, 기존 `grant_types_supported` · `scopes_supported` 줄을 위 값으로 교체한다. `claims_supported` 는 그대로 둔다.

- [ ] **Step 4: discovery 테스트 보강**

`TokenEndpointControllerTest.openidConfigurationAdvertisesImplementedCapabilities` 에 다음 단언을 추가한다.

```java
				.andExpect(jsonPath("$.introspection_endpoint").value(issuer + "/oauth2/introspect"))
				.andExpect(jsonPath("$.revocation_endpoint").value(issuer + "/oauth2/revoke"))
				.andExpect(jsonPath("$.grant_types_supported[0]").value("authorization_code"))
				.andExpect(jsonPath("$.grant_types_supported[1]").value("refresh_token"))
				.andExpect(jsonPath("$.grant_types_supported.length()").value(2))
				.andExpect(jsonPath("$.scopes_supported[3]").value("offline_access"))
				.andExpect(jsonPath("$.scopes_supported.length()").value(4))
				.andExpect(jsonPath("$.introspection_endpoint_auth_methods_supported[0]").value("client_secret_basic"))
				.andExpect(jsonPath("$.revocation_endpoint_auth_methods_supported[0]").value("client_secret_basic"))
```

기존 `grant_types_supported.length()` 를 1로 단언하는 줄이 있으면 위 값으로 교체한다.

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd token && ./gradlew test --no-daemon --rerun-tasks` → 83 tests PASS
Run: `cd client-registry && ./gradlew test --no-daemon --rerun-tasks` → 기존 테스트 PASS
Run: `docker run --rm -v "$PWD/gateway/nginx.conf:/etc/nginx/conf.d/default.conf:ro" nginx:1.27 nginx -t`
Expected: `syntax is ok` / `test is successful`

- [ ] **Step 6: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice
git commit -m "microservice: seed refresh-capable clients, route and advertise new endpoints

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 12: 관통 e2e 검증 + README + http 파일

**Files:**
- Modify: `microservice/README.md`
- Create: `microservice/http/token-lifecycle.http`

**Interfaces:**
- Consumes: 8개 서비스 전부 + 인프라

- [ ] **Step 1: 전체 빌드**

```bash
cd oauth-2/authorization-server/practice/microservice
for s in signing user-directory client-registry consent token-state token auth; do (cd $s && ./gradlew bootJar --no-daemon -q); done
ls */build/libs/*.jar
```
Expected: 7개 jar

- [ ] **Step 2: 인프라 + 7개 서비스 기동**

```bash
cd oauth-2/authorization-server/practice/microservice
JAVA=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn/bin/java
docker compose -p microservice-as -f docker-compose/docker-compose.yml up -d
for i in $(seq 1 40); do docker exec microservice-as-mysql-1 mysqladmin ping -uroot -p1111 --silent 2>/dev/null && break; sleep 2; done
for s in signing user-directory client-registry consent token-state token auth; do
  nohup $JAVA -jar $s/build/libs/$s-0.0.1-SNAPSHOT.jar > /tmp/ms5-$s.log 2>&1 &
  for i in $(seq 1 40); do grep -q "Started .*Application" /tmp/ms5-$s.log 2>/dev/null && break; sleep 2; done
  echo "$s: $(grep -c 'Started .*Application' /tmp/ms5-$s.log)"
done
```
Expected: 각 서비스 1

주의. 포트가 점유돼 있으면 `lsof -tiTCP:8087 -sTCP:LISTEN | xargs kill -9` 형태로 정리한 뒤 재기동한다.

- [ ] **Step 3: seed 상태 확인 및 보정**

```bash
docker exec microservice-as-mysql-1 mysql -uroot -p1111 microservice_as \
  -e "select client_id, scopes, grant_types from clients;"
```
Expected: `my-client` 의 scopes 에 `offline_access`, grant_types 에 `refresh_token` 이 있고 `article-api` 행이 존재

없으면(`ddl-auto: update` 라 기존 행은 갱신되지 않는다) 보정한 뒤 client-registry 를 재기동해 캐시를 비운다.

```bash
docker exec microservice-as-mysql-1 mysql -uroot -p1111 microservice_as -e \
  "update clients set scopes='openid,profile,email,offline_access', grant_types='authorization_code,refresh_token' where client_id='my-client';"
lsof -tiTCP:8085 -sTCP:LISTEN | xargs kill -9
# client-registry 재기동
```

- [ ] **Step 4: 동의 화면에 offline_access → refresh token 발급 (성공 기준 1, 2)**

```bash
cd /tmp
python3 - <<'EOF'
import hashlib, base64, secrets
v = secrets.token_urlsafe(48)
c = base64.urlsafe_b64encode(hashlib.sha256(v.encode()).digest()).rstrip(b'=').decode()
open('ms5-verifier.txt','w').write(v); open('ms5-challenge.txt','w').write(c)
EOF
CHAL=$(cat ms5-challenge.txt); VER=$(cat ms5-verifier.txt)
GW=http://localhost:9000
csrf() { grep -o 'name="_csrf"[^>]*value="[^"]*"' | sed 's/.*value="//;s/"$//'; }
rm -f ms5-cookies.txt

# 이전 동의를 지워 동의 화면이 뜨게 한다
docker exec microservice-as-mysql-1 mysql -uroot -p1111 microservice_as -e "delete from consents;"

AUTHZ="$GW/oauth2/authorize?response_type=code&client_id=my-client&redirect_uri=http://127.0.0.1:8080/callback&scope=openid%20profile%20offline_access&state=s1&nonce=n-abc123&code_challenge=$CHAL&code_challenge_method=S256"
curl -s -c ms5-cookies.txt -o /dev/null "$AUTHZ"
CSRF=$(curl -s -b ms5-cookies.txt -c ms5-cookies.txt $GW/login | csrf)
curl -s -b ms5-cookies.txt -c ms5-cookies.txt -o /dev/null -X POST $GW/login -d "username=user&password=1111&_csrf=$CSRF"

curl -s -b ms5-cookies.txt -c ms5-cookies.txt "$AUTHZ" > ms5-consent.html
echo "동의 화면에 offline_access 체크박스: $(grep -c 'offline_access' ms5-consent.html)"
PENDING=$(grep -o 'name="pending_id"[^>]*value="[^"]*"' ms5-consent.html | sed 's/.*value="//;s/"$//')
CSRF2=$(cat ms5-consent.html | csrf)

CODE=$(curl -s -i -b ms5-cookies.txt -X POST "$GW/oauth2/consent" \
  --data-urlencode "pending_id=$PENDING" --data-urlencode "_csrf=$CSRF2" \
  --data-urlencode "scope=openid" --data-urlencode "scope=profile" --data-urlencode "scope=offline_access" \
  | grep -i '^location' | grep -o 'code=[^&]*' | cut -d= -f2 | tr -d '\r')

curl -s -u my-client:secret -X POST $GW/oauth2/token \
  -d "grant_type=authorization_code&code=$CODE&redirect_uri=http://127.0.0.1:8080/callback&code_verifier=$VER" > ms5-token.json
python3 -c "
import json
d=json.load(open('ms5-token.json'))
print('refresh_token 존재:', 'refresh_token' in d, '| scope:', d.get('scope'))
open('ms5-refresh.txt','w').write(d['refresh_token'])
open('ms5-at.txt','w').write(d['access_token'])
"
```
Expected: 체크박스 grep ≥ 1, `refresh_token 존재: True`

- [ ] **Step 5: offline_access 없으면 refresh 없음 (성공 기준 2 대조)**

```bash
cd /tmp
GW=http://localhost:9000
CHAL=$(cat ms5-challenge.txt); VER=$(cat ms5-verifier.txt)
CODE2=$(curl -s -i -b ms5-cookies.txt "$GW/oauth2/authorize?response_type=code&client_id=my-client&redirect_uri=http://127.0.0.1:8080/callback&scope=openid%20profile&code_challenge=$CHAL&code_challenge_method=S256" \
  | grep -i '^location' | grep -o 'code=[^&]*' | cut -d= -f2 | tr -d '\r')
curl -s -u my-client:secret -X POST $GW/oauth2/token \
  -d "grant_type=authorization_code&code=$CODE2&redirect_uri=http://127.0.0.1:8080/callback&code_verifier=$VER" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('refresh_token 존재:', 'refresh_token' in d)"
```
Expected: `refresh_token 존재: False`

- [ ] **Step 6: refresh grant + id token 규칙 (성공 기준 3, 4)**

```bash
cd /tmp
GW=http://localhost:9000
RT=$(cat ms5-refresh.txt)
curl -s -u my-client:secret -X POST $GW/oauth2/token \
  -d "grant_type=refresh_token&refresh_token=$RT" > ms5-refreshed.json
python3 - <<'EOF'
import json, base64, hashlib
def b64d(s): return base64.urlsafe_b64decode(s + '='*(-len(s)%4))
first = json.loads(open('ms5-token.json').read())
d = json.load(open('ms5-refreshed.json'))
print('새 access_token:', d['access_token'] != first['access_token'])
print('새 refresh_token:', d['refresh_token'] != first['refresh_token'])
c = json.loads(b64d(d['id_token'].split('.')[1]))
c0 = json.loads(b64d(first['id_token'].split('.')[1]))
exp = base64.urlsafe_b64encode(hashlib.sha256(d['access_token'].encode()).digest()[:16]).rstrip(b'=').decode()
print('nonce 부재:', 'nonce' not in c)
print('auth_time 유지:', c.get('auth_time') == c0.get('auth_time'))
print('at_hash 일치:', c.get('at_hash') == exp)
open('ms5-refresh2.txt','w').write(d['refresh_token'])
EOF
```
Expected: 네 줄 모두 `True`, `nonce 부재: True`

- [ ] **Step 7: 재사용 탐지 (성공 기준 5)**

```bash
cd /tmp
GW=http://localhost:9000
RT_OLD=$(cat ms5-refresh.txt)
echo "=== 소진된 토큰 재사용 ==="
curl -s -u my-client:secret -X POST $GW/oauth2/token \
  -d "grant_type=refresh_token&refresh_token=$RT_OLD" | head -c 120; echo
echo "=== 계열 전체 상태 ==="
docker exec microservice-as-mysql-1 mysql -uroot -p1111 microservice_as \
  -e "select family_id, status, revoked_reason from refresh_tokens order by issued_at;"
echo "=== 방금 받은 정상 토큰도 죽었는지 ==="
curl -s -u my-client:secret -X POST $GW/oauth2/token \
  -d "grant_type=refresh_token&refresh_token=$(cat ms5-refresh2.txt)" | head -c 120; echo
```
Expected: 재사용은 `invalid_grant`, 계열 3행이 전부 `REVOKED` / `REUSE_DETECTED`, 정상 토큰도 `invalid_grant`

- [ ] **Step 8: revoke (성공 기준 6)**

```bash
cd /tmp
GW=http://localhost:9000
CHAL=$(cat ms5-challenge.txt); VER=$(cat ms5-verifier.txt)
CODE3=$(curl -s -i -b ms5-cookies.txt "$GW/oauth2/authorize?response_type=code&client_id=my-client&redirect_uri=http://127.0.0.1:8080/callback&scope=openid%20offline_access&code_challenge=$CHAL&code_challenge_method=S256" \
  | grep -i '^location' | grep -o 'code=[^&]*' | cut -d= -f2 | tr -d '\r')
RT3=$(curl -s -u my-client:secret -X POST $GW/oauth2/token \
  -d "grant_type=authorization_code&code=$CODE3&redirect_uri=http://127.0.0.1:8080/callback&code_verifier=$VER" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['refresh_token'])")
echo "revoke 응답 코드: $(curl -s -o /dev/null -w '%{http_code}' -u my-client:secret -X POST $GW/oauth2/revoke -d "token=$RT3")"
echo "미존재 토큰 revoke: $(curl -s -o /dev/null -w '%{http_code}' -u my-client:secret -X POST $GW/oauth2/revoke -d "token=no-such-token")"
echo "폐기 후 회전:"
curl -s -u my-client:secret -X POST $GW/oauth2/token -d "grant_type=refresh_token&refresh_token=$RT3" | head -c 120; echo
echo "$RT3" > ms5-revoked.txt
```
Expected: 둘 다 `200`, 폐기 후 회전은 `invalid_grant`

- [ ] **Step 9: introspection (성공 기준 7, 8)**

```bash
cd /tmp
GW=http://localhost:9000
CHAL=$(cat ms5-challenge.txt); VER=$(cat ms5-verifier.txt)
CODE4=$(curl -s -i -b ms5-cookies.txt "$GW/oauth2/authorize?response_type=code&client_id=my-client&redirect_uri=http://127.0.0.1:8080/callback&scope=openid%20offline_access&code_challenge=$CHAL&code_challenge_method=S256" \
  | grep -i '^location' | grep -o 'code=[^&]*' | cut -d= -f2 | tr -d '\r')
curl -s -u my-client:secret -X POST $GW/oauth2/token \
  -d "grant_type=authorization_code&code=$CODE4&redirect_uri=http://127.0.0.1:8080/callback&code_verifier=$VER" > ms5-token4.json
RT4=$(python3 -c "import json; print(json.load(open('ms5-token4.json'))['refresh_token'])")
AT4=$(python3 -c "import json; print(json.load(open('ms5-token4.json'))['access_token'])")

echo "=== article-api 가 my-client 의 access token 조회 (호출자 != 토큰 주인) ==="
curl -s -u article-api:secret -X POST $GW/oauth2/introspect -d "token=$AT4" | python3 -m json.tool
echo "=== 살아있는 refresh 조회 ==="
curl -s -u article-api:secret -X POST $GW/oauth2/introspect -d "token=$RT4" | python3 -m json.tool
echo "=== 폐기된 refresh 조회 ==="
curl -s -u article-api:secret -X POST $GW/oauth2/introspect -d "token=$(cat ms5-revoked.txt)"; echo
echo "=== 잘못된 secret ==="
curl -s -o /dev/null -w "%{http_code}\n" -u article-api:wrong -X POST $GW/oauth2/introspect -d "token=$AT4"
```
Expected: access token 은 `active:true` + `client_id: my-client` + `token_type: Bearer`, 살아있는 refresh 는 `active:true` 이고 `token_type` 없음, 폐기된 것은 `{"active":false}` 뿐, 잘못된 secret 은 `401`

- [ ] **Step 10: 회귀 확인 (성공 기준 9)**

```bash
cd /tmp
GW=http://localhost:9000
echo "=== userinfo ==="
curl -s -H "Authorization: Bearer $(python3 -c "import json; print(json.load(open('ms5-token4.json'))['access_token'])")" $GW/userinfo; echo
echo "=== code 재사용 ==="
CHAL=$(cat ms5-challenge.txt); VER=$(cat ms5-verifier.txt)
CODE5=$(curl -s -i -b ms5-cookies.txt "$GW/oauth2/authorize?response_type=code&client_id=my-client&redirect_uri=http://127.0.0.1:8080/callback&scope=openid&code_challenge=$CHAL&code_challenge_method=S256" \
  | grep -i '^location' | grep -o 'code=[^&]*' | cut -d= -f2 | tr -d '\r')
curl -s -o /dev/null -u my-client:secret -X POST $GW/oauth2/token -d "grant_type=authorization_code&code=$CODE5&redirect_uri=http://127.0.0.1:8080/callback&code_verifier=$VER"
curl -s -u my-client:secret -X POST $GW/oauth2/token -d "grant_type=authorization_code&code=$CODE5&redirect_uri=http://127.0.0.1:8080/callback&code_verifier=$VER" | head -c 100; echo
echo "=== discovery ==="
curl -s $GW/.well-known/openid-configuration | python3 -m json.tool | grep -E "introspection|revocation|refresh_token|offline_access"
```
Expected: userinfo 200 + claim, code 재사용 `invalid_grant`, discovery 에 네 항목 노출

- [ ] **Step 11: token-state 외부 비노출 확인**

```bash
echo "gateway 로 내부 API 접근: $(curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:9000/internal/refresh-tokens/introspect -H 'Content-Type: application/json' -d '{"refreshToken":"x"}')"
```
Expected: `404` (gateway 가 라우팅하지 않는다)

- [ ] **Step 12: 정리**

```bash
pkill -f "microservice.*SNAPSHOT.jar" 2>/dev/null
for p in 8081 8082 8083 8084 8085 8086 8087; do lsof -tiTCP:$p -sTCP:LISTEN 2>/dev/null | xargs kill -9 2>/dev/null; done
docker compose -p microservice-as -f oauth-2/authorization-server/practice/microservice/docker-compose/docker-compose.yml stop
```

- [ ] **Step 13: README 갱신**

`microservice/README.md` 를 갱신한다. 기존 톤·구조·mermaid 스타일을 그대로 이어라.

- 제목/개요에 "슬라이스 3: 토큰 수명 관리(refresh 회전 · 재사용 탐지 · introspection · revocation)" 명시
- 구도 ASCII 와 **아키텍처 mermaid 다이어그램에 token-state(8087) 추가**, `/oauth2/introspect`·`/oauth2/revoke` 라우팅 반영
- 서비스 표에 token-state 행 추가(8087, refresh token 계열·폐기 상태 소유, `refresh_tokens` 테이블)
- **시퀀스 다이어그램 3개 추가**: (a) refresh grant 회전, (b) 재사용 탐지 → 계열 폐기, (c) introspection(JWT 로컬 검증 vs token-state 조회 분기)
- "관통 flow" 에 refresh 발급·회전 단계 추가
- "검증된 성공 기준" 을 이번 e2e 결과로 갱신(실제 통과한 것만)
- "기동 방법" 의 서비스 목록·순서에 token-state 추가(signing → user-directory → client-registry → consent → **token-state** → token → auth)
- "알려진 한계" 에 추가:
  - 회전은 즉시 이전 토큰을 무효화하므로 **정상 client 의 재시도도 계열을 죽인다.** client 는 refresh 요청을 직렬화해야 한다
  - client 가 새 refresh token 을 저장하기 전에 죽으면 그 계열을 잃는다(유예 기간을 두지 않는다)
  - introspection 은 **인증된 client 면 누구의 토큰이든** 조회할 수 있다. 좁히려면 client_credentials + `introspect` scope 가 정석이다
  - access token 은 폐기하지 않는다(RFC 7009 §2 의 MAY). 폐기는 refresh 한정이다

- [ ] **Step 14: http 파일 작성**

`microservice/http/token-lifecycle.http`:

```
### refresh grant — 회전. 응답의 새 refresh_token 으로 교체해야 다음 회전이 된다
POST http://localhost:9000/oauth2/token
Authorization: Basic bXktY2xpZW50OnNlY3JldA==
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token&refresh_token={refresh_token}

### scope 축소 — 이번 access token 에만 적용된다. 저장된 refresh 의 scope 는 그대로다
POST http://localhost:9000/oauth2/token
Authorization: Basic bXktY2xpZW50OnNlY3JldA==
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token&refresh_token={refresh_token}&scope=openid

### 소진된 refresh 재사용 -> invalid_grant. 계열 전체가 폐기된다
POST http://localhost:9000/oauth2/token
Authorization: Basic bXktY2xpZW50OnNlY3JldA==
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token&refresh_token={이미_회전된_refresh_token}

### introspection — article-api 가 my-client 의 토큰을 조회한다 (호출자 != 토큰 주인)
POST http://localhost:9000/oauth2/introspect
Authorization: Basic YXJ0aWNsZS1hcGk6c2VjcmV0
Content-Type: application/x-www-form-urlencoded

token={access_token 또는 refresh_token}

### 폐기된 토큰 introspection -> {"active": false} 뿐. 사유를 구분해 주지 않는다

### revocation — 계열 전체를 폐기한다. 존재하지 않는 토큰에도 200 이다
POST http://localhost:9000/oauth2/revoke
Authorization: Basic bXktY2xpZW50OnNlY3JldA==
Content-Type: application/x-www-form-urlencoded

token={refresh_token}

### access token 폐기 요청 -> 200 이지만 아무것도 하지 않는다 (RFC 7009 2 는 MAY)
POST http://localhost:9000/oauth2/revoke
Authorization: Basic bXktY2xpZW50OnNlY3JldA==
Content-Type: application/x-www-form-urlencoded

token={access_token}&token_type_hint=access_token

### discovery — introspection_endpoint · revocation_endpoint · offline_access 확인
GET http://localhost:9000/.well-known/openid-configuration
```

- [ ] **Step 15: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/README.md oauth-2/authorization-server/practice/microservice/http
git commit -m "microservice: e2e verification and docs for token lifecycle slice

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Self-Review 결과

- **Spec coverage**: token-state 신설·데이터 모델(Task 1) ✓ / 토큰 생성·해시(Task 2) ✓ / 발급·조회(Task 3) ✓ / 회전·재사용 탐지·행 잠금(Task 4) ✓ / 폐기·내부 API(Task 5) ✓ / 두 관문 — offline_access 동의 + grant 등록(Task 7 Step 7) ✓ / refresh grant·scope 축소·id token 재발급 nonce·auth_time 규칙(Task 8) ✓ / introspection — 힌트 비신뢰·access token 로컬 검증·비활성 단일 응답·인증된 client 전부 허용(Task 9) ✓ / revocation — 항상 200·계열 폐기·access token MAY(Task 10) ✓ / seed·gateway·discovery(Task 11) ✓ / 실패 모드 fail-closed(Task 7 TokenStateClient 주석 + 예외 전파) ✓ / e2e 9기준(Task 12) ✓
- **auth 무변경**: 스펙이 `offline_access` 를 일반 scope 로 취급한다고 정했고, 계획도 auth 를 건드리지 않는다. File Structure 절에 그 근거와 "변경이 필요하면 그것은 신호"를 명시 ✓
- **제외 항목 준수**: 내부 서비스 인증 · Kafka · back-channel logout · jwks 캐시 · access token deny-list · client_credentials — 어느 태스크에도 없음 ✓
- **Type 일관성**: `RefreshTokenService.issue(clientId, sub, scope, authTime)` 는 Task 3 정의 → Task 5 컨트롤러 호출 일치 ✓. `RotateResult(status, sub, scope, authTime, refreshToken, expiresAt)` 는 token-state(enum status)와 token(String status) 두 곳에 같은 이름의 다른 record 로 존재하며 **패키지가 달라 충돌하지 않는다** — token 쪽은 JSON 으로 받으므로 String ✓. `TokenStateClient.issue(clientId, sub, scope, authTime)` 는 Task 7 정의 → 같은 태스크 Step 7 호출 일치 ✓. `AccessTokenVerifier.VerifiedToken` 5필드는 Task 9 에서 확장하고 같은 태스크에서 기존 호출부를 보강 ✓. `GrantResult` 는 Task 8 정의 → 같은 태스크 컨트롤러 사용 일치 ✓
- **알려진 진행상 주의**: Task 6 은 동작 변경이 없는 리팩터링이므로 기존 테스트가 하나라도 깨지면 구현을 고쳐야 한다(테스트를 고치면 안 된다). Task 9 는 `VerifiedToken` 확장으로 기존 테스트가 컴파일 실패하며, 이는 계획된 것이고 시그니처만 맞춘다.
