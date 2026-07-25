# microservice authorization server — 슬라이스 2 (OIDC 확장) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 슬라이스 1 의 OAuth 골격에 OIDC 인증 계층(id token · userinfo · consent 서비스 분리)을 얹는다.

**Architecture:** consent(8086)를 7번째 서비스로 신설해 동의 기록을 소유하게 하고(화면은 auth 가 렌더), token 서비스가 openid scope 요청 시 id token 을 발급하며 `/userinfo` 를 제공한다. authorize 시점 값(nonce·auth_time)은 Redis code 레코드에 실어 token 으로 전달하고, 동의 화면 왕복은 Redis pending authorization 으로 견딘다.

**Tech Stack:** Java 21, Spring Boot 3.4.5, Spring Web/Security/Data JPA/Data Redis, Thymeleaf(auth 동의 화면), Nimbus JOSE(token 의 access token 검증), MySQL, Redis, JUnit 5.

## Global Constraints

- Java 21 (gradle toolchain). 로컬 `java -jar` 는 `/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn/bin/java` 사용 (PATH java 는 17).
- Spring Boot **3.4.5**, io.spring.dependency-management **1.1.7**, gradle wrapper 8.13. 버전 하드코딩 오타 주의(전 서비스 동일해야 함).
- **SAS starter(`spring-boot-starter-oauth2-authorization-server`) 금지.** OIDC 로직은 직접 구현.
- gradle 명령은 반드시 `--no-daemon` (이 환경은 gradle 데몬이 SIGKILL 되는 이슈가 있음).
- 새 서비스 디렉토리에는 **`.gitignore` 를 반드시 복사**한다(`cp signing/.gitignore <new>/.gitignore`). 누락하면 `build/`·`.gradle/` 산출물이 커밋된다.
- 패키지 `dev.starryeye.<service_name>`(underscore), 메인 클래스 PascalCase + `Application`.
- 위치: `oauth-2/authorization-server/practice/microservice/<service>/`.
- 포트: gateway 9000, auth 8081, token 8082, signing 8083, user-directory 8084, client-registry 8085, **consent 8086**.
- MySQL `jdbc:mysql://localhost:3306/microservice_as` root/1111, Redis localhost:6379.
- 테스트에서 mock 빈은 `@MockitoBean` 사용(`@MockBean` 은 Boot 3.4 deprecated — 출력 pristine 유지).
- cross-service 수신 record 에는 `@JsonIgnoreProperties(ignoreUnknown = true)` 를 붙인다.
- 주석: 클래스 설명 javadoc 은 **클래스 바디 안**(여는 중괄호 아래). 경험담 서술 금지 — 함정은 "주의." 항목으로.
- 커밋 메시지 말미: `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`

## 공유 계약 (이 슬라이스에서 신규/확장)

```
consent 서비스 (신규)
  GET  /internal/consents/{sub}/{clientId}
       200 { "sub": str, "clientId": str, "scopes": [str] }      (기록 없으면 scopes: [])
  POST /internal/consents  { "sub": str, "clientId": str, "scopes": [str] }
       200 { "sub": str, "clientId": str, "scopes": [str] }      (기존 기록과 합집합 병합)

user-directory (확장)
  GET  /internal/users/{sub}
       200 { "sub", "username", "authorities": [str],
             "name", "nickname", "preferredUsername", "email", "emailVerified": bool }

Redis (확장)
  auth:code:{code}   { clientId, redirectUri, scope, sub, codeChallenge, nonce, authTime }  TTL 60s
  auth:pending:{id}  { clientId, redirectUri, scope, sub, codeChallenge, state, nonce, authTime }  TTL 300s
```

---

## Task 1: user-directory — 프로필 필드 확장

**Files:**
- Modify: `user-directory/src/main/java/dev/starryeye/user_directory/jpa/UserEntity.java`
- Modify: `user-directory/src/main/java/dev/starryeye/user_directory/dto/UserResponse.java`
- Modify: `user-directory/src/main/java/dev/starryeye/user_directory/UserController.java`
- Modify: `user-directory/src/main/java/dev/starryeye/user_directory/UserSeedInitializer.java`
- Test: `user-directory/src/test/java/dev/starryeye/user_directory/UserControllerTest.java`

**Interfaces:**
- Produces: `GET /internal/users/{sub}` 응답에 `name, nickname, preferredUsername, email, emailVerified` 추가. token 서비스가 id token/userinfo claim 소스로 사용한다.

- [ ] **Step 1: 실패 테스트 작성 (기존 테스트 파일에 추가)**

기존 `UserControllerTest` 의 `seedUser` 헬퍼와 `getUserReturnsProfileForKnownSub` 테스트를 아래로 교체하고, 프로필 필드 검증 테스트를 추가한다.

```java
	private UserEntity seedUser(String authorities) {
		return UserEntity.builder()
				.sub("user-sub-0001").username("user")
				.password(encoder.encode("1111")).authorities(authorities)
				.name("Star Rye").nickname("starry").preferredUsername("starryeye")
				.email("starryeye@example.com").emailVerified(true)
				.build();
	}

	@Test
	void getUserReturnsProfileForKnownSub() throws Exception {
		when(repository.findBySub("user-sub-0001")).thenReturn(Optional.of(seedUser("ROLE_USER,ROLE_ADMIN")));

		mockMvc.perform(get("/internal/users/user-sub-0001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sub").value("user-sub-0001"))
				.andExpect(jsonPath("$.username").value("user"))
				.andExpect(jsonPath("$.authorities", hasSize(2)));
	}

	@Test
	void getUserReturnsProfileClaims() throws Exception {
		when(repository.findBySub("user-sub-0001")).thenReturn(Optional.of(seedUser("ROLE_USER")));

		mockMvc.perform(get("/internal/users/user-sub-0001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Star Rye"))
				.andExpect(jsonPath("$.nickname").value("starry"))
				.andExpect(jsonPath("$.preferredUsername").value("starryeye"))
				.andExpect(jsonPath("$.email").value("starryeye@example.com"))
				.andExpect(jsonPath("$.emailVerified").value(true));
	}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd oauth-2/authorization-server/practice/microservice/user-directory && ./gradlew test --no-daemon --tests UserControllerTest`
Expected: FAIL — `UserEntity.builder()` 에 `name(...)` 등이 없어 컴파일 에러

- [ ] **Step 3: UserEntity 에 프로필 필드 추가**

`UserEntity.java` 의 `authorities` 필드 아래에 컬럼을 추가하고 빌더 생성자를 확장한다.

```java
	@Column(nullable = false)
	private boolean enabled = true;

	// OIDC profile scope 대응 claim
	private String name;

	private String nickname;

	private String preferredUsername;

	// OIDC email scope 대응 claim
	private String email;

	private boolean emailVerified;

	@Builder
	private UserEntity(String sub, String username, String password, String authorities,
			String name, String nickname, String preferredUsername, String email, boolean emailVerified) {
		this.sub = sub;
		this.username = username;
		this.password = password;
		this.authorities = authorities;
		this.name = name;
		this.nickname = nickname;
		this.preferredUsername = preferredUsername;
		this.email = email;
		this.emailVerified = emailVerified;
	}
```

주의. 기존 파일에 `enabled` 필드가 없다면 위 `enabled` 줄은 넣지 말 것 — 실제 파일을 읽고 기존 필드는 그대로 두고 프로필 필드만 추가한다.

- [ ] **Step 4: UserResponse 확장**

```java
package dev.starryeye.user_directory.dto;

import java.util.List;

public record UserResponse(
		String sub,
		String username,
		List<String> authorities,
		String name,
		String nickname,
		String preferredUsername,
		String email,
		boolean emailVerified
) {
}
```

- [ ] **Step 5: UserController 의 응답 생성부 수정**

`getUser` 메서드에서 `UserResponse` 를 만드는 부분을 아래로 교체한다(기존 `toList(...)` 헬퍼는 그대로 사용).

```java
		return new UserResponse(
				user.getSub(), user.getUsername(), toList(user.getAuthorities()),
				user.getName(), user.getNickname(), user.getPreferredUsername(),
				user.getEmail(), user.isEmailVerified());
```

`authenticate` 메서드의 `AuthenticateResponse` 는 변경하지 않는다(로그인은 프로필 claim 이 불필요).

- [ ] **Step 6: seed 에 프로필 값 추가**

`UserSeedInitializer` 의 `repository.save(UserEntity.builder()...)` 호출에 프로필 값을 추가한다.

```java
		repository.save(UserEntity.builder()
				.sub("user-sub-0001")
				.username("user")
				.password(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("1111"))
				.authorities("ROLE_USER")
				.name("Star Rye")
				.nickname("starry")
				.preferredUsername("starryeye")
				.email("starryeye@example.com")
				.emailVerified(true)
				.build());
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `cd oauth-2/authorization-server/practice/microservice/user-directory && ./gradlew test --no-daemon --tests UserControllerTest`
Expected: PASS (6 tests — 기존 4 + 신규 2)

- [ ] **Step 8: Commit**

```bash
git add oauth-2/authorization-server/practice/microservice/user-directory
git commit -m "microservice: extend user-directory with OIDC profile claims

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 2: consent 서비스 — 스캐폴드 + 엔티티

**Files:**
- Create: `consent/build.gradle`, `consent/settings.gradle`, `consent/.gitignore`, `consent/gradle/**`, `consent/gradlew`
- Create: `consent/src/main/resources/application.yml`
- Create: `consent/src/main/java/dev/starryeye/consent/ConsentApplication.java`
- Create: `consent/src/main/java/dev/starryeye/consent/jpa/ConsentEntity.java`
- Create: `consent/src/main/java/dev/starryeye/consent/jpa/ConsentEntityRepository.java`

**Interfaces:**
- Produces: `ConsentEntity`(복합키 sub+clientId, scopes comma 문자열), `ConsentEntityRepository.findBySubAndClientId(String, String) → Optional<ConsentEntity>`

- [ ] **Step 1: wrapper·gitignore 복사 + build.gradle 작성**

```bash
cd oauth-2/authorization-server/practice/microservice
mkdir -p consent
cp -r signing/gradle consent/gradle
cp signing/gradlew consent/gradlew
cp signing/.gitignore consent/.gitignore
```

`consent/build.gradle`:
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
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') { useJUnitPlatform() }
```

- [ ] **Step 2: settings.gradle + application.yml**

`consent/settings.gradle`:
```groovy
rootProject.name = 'consent'
```

`consent/src/main/resources/application.yml`:
```yaml
server:
  port: 8086

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/microservice_as
    username: root
    password: 1111
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

logging:
  level:
    dev.starryeye: DEBUG
```

- [ ] **Step 3: ConsentApplication 작성**

```java
package dev.starryeye.consent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ConsentApplication {

	/**
	 * 사용자가 client 에게 부여한 동의(consent) 기록을 소유하는 서비스이다.
	 *      "누가(sub) 어떤 client 에게 어떤 scope 를 승인했는가" 만 관리하며 화면은 갖지 않는다.
	 *      동의 화면은 로그인 세션과 진행 중 인가 맥락을 가진 auth 가 렌더하고, 이 서비스는 기록의 소유자로만 남는다.
	 *      -> user-directory(사용자 소유), client-registry(client 소유) 와 같은 성격의 내부 데이터 서비스다.
	 */

	public static void main(String[] args) {
		SpringApplication.run(ConsentApplication.class, args);
	}
}
```

- [ ] **Step 4: ConsentEntity 작성**

```java
package dev.starryeye.consent.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "consents", uniqueConstraints = @UniqueConstraint(columnNames = {"sub", "client_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentEntity {

	/**
	 * 동의 기록이다. (sub, clientId) 한 쌍당 한 행이며 승인된 scope 를 comma 로 보관한다.
	 *      scope 는 추가 동의 때 합집합으로 병합되므로 행이 늘지 않고 갱신된다.
	 */

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "sub", nullable = false)
	private String sub;

	@Column(name = "client_id", nullable = false)
	private String clientId;

	@Column(nullable = false, length = 1000)
	private String scopes; // comma 구분

	@Builder
	private ConsentEntity(String sub, String clientId, String scopes) {
		this.sub = sub;
		this.clientId = clientId;
		this.scopes = scopes;
	}

	public void replaceScopes(String scopes) {
		this.scopes = scopes;
	}
}
```

- [ ] **Step 5: ConsentEntityRepository 작성**

```java
package dev.starryeye.consent.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsentEntityRepository extends JpaRepository<ConsentEntity, Long> {

	Optional<ConsentEntity> findBySubAndClientId(String sub, String clientId);
}
```

- [ ] **Step 6: 컴파일 검증**

Run: `cd oauth-2/authorization-server/practice/microservice/consent && ./gradlew compileJava --no-daemon -q`
Expected: 성공(출력 없음)

- [ ] **Step 7: Commit**

```bash
git add oauth-2/authorization-server/practice/microservice/consent
git commit -m "microservice: scaffold consent service with consent entity

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 3: consent 서비스 — 조회/저장 API

**Files:**
- Create: `consent/src/main/java/dev/starryeye/consent/ConsentController.java`
- Create: `consent/src/main/java/dev/starryeye/consent/dto/ConsentResponse.java`
- Create: `consent/src/main/java/dev/starryeye/consent/dto/SaveConsentRequest.java`
- Test: `consent/src/test/java/dev/starryeye/consent/ConsentControllerTest.java`

**Interfaces:**
- Consumes: `ConsentEntityRepository.findBySubAndClientId`
- Produces: `GET /internal/consents/{sub}/{clientId}` → `{sub, clientId, scopes:[str]}`(없으면 빈 배열), `POST /internal/consents` → 합집합 병합 후 동일 형식

- [ ] **Step 1: 실패 테스트 작성**

```java
package dev.starryeye.consent;

import dev.starryeye.consent.jpa.ConsentEntity;
import dev.starryeye.consent.jpa.ConsentEntityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsentController.class)
class ConsentControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	ConsentEntityRepository repository;

	@Test
	void returnsGrantedScopes() throws Exception {
		when(repository.findBySubAndClientId("user-sub-0001", "my-client")).thenReturn(
				Optional.of(ConsentEntity.builder().sub("user-sub-0001").clientId("my-client")
						.scopes("openid,profile").build()));

		mockMvc.perform(get("/internal/consents/user-sub-0001/my-client"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sub").value("user-sub-0001"))
				.andExpect(jsonPath("$.clientId").value("my-client"))
				.andExpect(jsonPath("$.scopes", containsInAnyOrder("openid", "profile")));
	}

	@Test
	void returnsEmptyScopesWhenNoConsentRecorded() throws Exception {
		when(repository.findBySubAndClientId("user-sub-0001", "unknown-client")).thenReturn(Optional.empty());

		mockMvc.perform(get("/internal/consents/user-sub-0001/unknown-client"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.scopes", hasSize(0)));
	}

	@Test
	void saveMergesWithExistingScopes() throws Exception {
		ConsentEntity existing = ConsentEntity.builder().sub("user-sub-0001").clientId("my-client")
				.scopes("openid,profile").build();
		when(repository.findBySubAndClientId("user-sub-0001", "my-client")).thenReturn(Optional.of(existing));
		when(repository.save(any(ConsentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		mockMvc.perform(post("/internal/consents")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sub\":\"user-sub-0001\",\"clientId\":\"my-client\",\"scopes\":[\"email\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.scopes", containsInAnyOrder("openid", "profile", "email")));
	}

	@Test
	void saveCreatesRecordWhenAbsent() throws Exception {
		when(repository.findBySubAndClientId("user-sub-0002", "my-client")).thenReturn(Optional.empty());
		when(repository.save(any(ConsentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		mockMvc.perform(post("/internal/consents")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sub\":\"user-sub-0002\",\"clientId\":\"my-client\",\"scopes\":[\"openid\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sub").value("user-sub-0002"))
				.andExpect(jsonPath("$.scopes", containsInAnyOrder("openid")));
	}
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd oauth-2/authorization-server/practice/microservice/consent && ./gradlew test --no-daemon --tests ConsentControllerTest`
Expected: FAIL — `ConsentController` 클래스 없음(컴파일 에러)

- [ ] **Step 3: DTO 작성**

`consent/src/main/java/dev/starryeye/consent/dto/ConsentResponse.java`:
```java
package dev.starryeye.consent.dto;

import java.util.List;

public record ConsentResponse(String sub, String clientId, List<String> scopes) {
}
```

`consent/src/main/java/dev/starryeye/consent/dto/SaveConsentRequest.java`:
```java
package dev.starryeye.consent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SaveConsentRequest(String sub, String clientId, List<String> scopes) {
}
```

- [ ] **Step 4: ConsentController 구현**

```java
package dev.starryeye.consent;

import dev.starryeye.consent.dto.ConsentResponse;
import dev.starryeye.consent.dto.SaveConsentRequest;
import dev.starryeye.consent.jpa.ConsentEntity;
import dev.starryeye.consent.jpa.ConsentEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class ConsentController {

	/**
	 * 동의 기록 조회/저장 API 이다. (내부 전용.. gateway 에 노출하지 않는다)
	 *      조회는 기록이 없어도 200 + 빈 scope 로 응답한다. "동의한 적 없음" 은 오류가 아니라 정상 상태다.
	 *      저장은 기존 기록과 합집합으로 병합한다. 추가 동의가 이전 동의를 지우면 안 되기 때문이다.
	 */

	private final ConsentEntityRepository repository;

	@GetMapping("/internal/consents/{sub}/{clientId}")
	public ConsentResponse getConsent(@PathVariable String sub, @PathVariable String clientId) {
		List<String> scopes = repository.findBySubAndClientId(sub, clientId)
				.map(entity -> toList(entity.getScopes()))
				.orElseGet(ArrayList::new);
		return new ConsentResponse(sub, clientId, scopes);
	}

	@PostMapping("/internal/consents")
	public ConsentResponse saveConsent(@RequestBody SaveConsentRequest request) {

		Set<String> merged = new LinkedHashSet<>();
		ConsentEntity entity = repository.findBySubAndClientId(request.sub(), request.clientId()).orElse(null);
		if (entity != null) {
			merged.addAll(toList(entity.getScopes()));
		}
		if (request.scopes() != null) {
			merged.addAll(request.scopes());
		}

		String mergedScopes = String.join(",", merged);
		if (entity == null) {
			entity = ConsentEntity.builder()
					.sub(request.sub()).clientId(request.clientId()).scopes(mergedScopes).build();
		} else {
			entity.replaceScopes(mergedScopes);
		}
		repository.save(entity);

		return new ConsentResponse(request.sub(), request.clientId(), new ArrayList<>(merged));
	}

	private List<String> toList(String commaDelimited) {
		if (!StringUtils.hasText(commaDelimited)) {
			return new ArrayList<>();
		}
		return new ArrayList<>(List.of(StringUtils.commaDelimitedListToStringArray(commaDelimited)));
	}
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd oauth-2/authorization-server/practice/microservice/consent && ./gradlew test --no-daemon --tests ConsentControllerTest`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
git add oauth-2/authorization-server/practice/microservice/consent
git commit -m "microservice: consent lookup and merge API

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 4: auth — pending authorization 저장소

**Files:**
- Create: `auth/src/main/java/dev/starryeye/auth/PendingAuthorization.java`
- Create: `auth/src/main/java/dev/starryeye/auth/PendingAuthorizationStore.java`
- Modify: `auth/src/main/resources/application.yml`
- Test: `auth/src/test/java/dev/starryeye/auth/PendingAuthorizationStoreTest.java`

**Interfaces:**
- Produces:
  - `PendingAuthorization(String clientId, String redirectUri, String scope, String sub, String codeChallenge, String state, String nonce, long authTime)` (record)
  - `PendingAuthorizationStore.save(PendingAuthorization) → String pendingId`
  - `PendingAuthorizationStore.consume(String pendingId) → Optional<PendingAuthorization>` (조회 후 삭제, 1회용)

- [ ] **Step 1: 실패 테스트 작성**

```java
package dev.starryeye.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingAuthorizationStoreTest {

	@Test
	void saveStoresPendingWithTtlAndReturnsId() throws Exception {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);

		PendingAuthorizationStore store = new PendingAuthorizationStore(redis, new ObjectMapper(), 300);
		String pendingId = store.save(new PendingAuthorization(
				"my-client", "http://127.0.0.1:8080/callback", "openid profile",
				"user-sub-0001", "chal", "xyz789", "n-0S6_WzA2Mj", 1700000000L));

		assertThat(pendingId).isNotBlank();

		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
		verify(ops).set(keyCaptor.capture(), valueCaptor.capture(), eq(Duration.ofSeconds(300)));

		assertThat(keyCaptor.getValue()).isEqualTo("auth:pending:" + pendingId);
		Map<String, Object> json = new ObjectMapper().readValue(valueCaptor.getValue(),
				new com.fasterxml.jackson.core.type.TypeReference<>() {});
		assertThat(json)
				.containsEntry("clientId", "my-client")
				.containsEntry("redirectUri", "http://127.0.0.1:8080/callback")
				.containsEntry("scope", "openid profile")
				.containsEntry("sub", "user-sub-0001")
				.containsEntry("codeChallenge", "chal")
				.containsEntry("state", "xyz789")
				.containsEntry("nonce", "n-0S6_WzA2Mj");
	}

	@Test
	void consumeReadsAndDeletesAtomically() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);
		when(ops.getAndDelete("auth:pending:p1")).thenReturn(
				"{\"clientId\":\"my-client\",\"redirectUri\":\"http://127.0.0.1:8080/callback\","
						+ "\"scope\":\"openid profile\",\"sub\":\"user-sub-0001\",\"codeChallenge\":\"chal\","
						+ "\"state\":\"xyz789\",\"nonce\":\"n-0S6_WzA2Mj\",\"authTime\":1700000000}");

		PendingAuthorizationStore store = new PendingAuthorizationStore(redis, new ObjectMapper(), 300);
		Optional<PendingAuthorization> result = store.consume("p1");

		assertThat(result).isPresent();
		assertThat(result.get().sub()).isEqualTo("user-sub-0001");
		assertThat(result.get().nonce()).isEqualTo("n-0S6_WzA2Mj");
		verify(ops).getAndDelete("auth:pending:p1");
	}

	@Test
	void consumeMissingReturnsEmpty() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);
		when(ops.getAndDelete("auth:pending:none")).thenReturn(null);

		PendingAuthorizationStore store = new PendingAuthorizationStore(redis, new ObjectMapper(), 300);
		assertThat(store.consume("none")).isEmpty();
	}
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd oauth-2/authorization-server/practice/microservice/auth && ./gradlew test --no-daemon --tests PendingAuthorizationStoreTest`
Expected: FAIL — `PendingAuthorization`/`PendingAuthorizationStore` 없음

- [ ] **Step 3: PendingAuthorization record 작성**

```java
package dev.starryeye.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PendingAuthorization(
		String clientId,
		String redirectUri,
		String scope,
		String sub,
		String codeChallenge,
		String state,
		String nonce,
		long authTime
) {
}
```

- [ ] **Step 4: PendingAuthorizationStore 구현**

```java
package dev.starryeye.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class PendingAuthorizationStore {

	/**
	 * 동의 화면을 거치는 동안 진행 중인 인가 요청을 서버에 보관한다.
	 *      화면에는 불투명한 pendingId 만 내보내고 client_id/redirect_uri/scope 는 서버에만 둔다.
	 *      -> 폼 hidden 으로 흘리면 사용자가 scope 를 올리거나 redirect_uri 를 바꿔치기할 수 있다.
	 *
	 * 주의. 표준이 정한 방식이 아니라 구현 선택이다. OIDC 는 동의 화면 상태 유지 방법을 규정하지 않는다.
	 *      (같은 패턴이 널리 쓰인다.. spring authorization server 는 진행 중 authorization 을 저장소에 두고 내부 state 로 조회하고,
	 *       keycloak 은 authentication session 에 두고 불투명한 tab id 를 노출한다)
	 */

	private static final String KEY_PREFIX = "auth:pending:";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final long ttlSeconds;

	public PendingAuthorizationStore(
			StringRedisTemplate redisTemplate,
			ObjectMapper objectMapper,
			@Value("${my.pending-authorization-ttl-seconds}") long ttlSeconds
	) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.ttlSeconds = ttlSeconds;
	}

	public String save(PendingAuthorization pending) {
		String pendingId = UUID.randomUUID().toString().replace("-", "");
		try {
			redisTemplate.opsForValue().set(KEY_PREFIX + pendingId,
					objectMapper.writeValueAsString(pending), Duration.ofSeconds(ttlSeconds));
		} catch (Exception e) {
			throw new IllegalStateException("failed to store pending authorization", e);
		}
		return pendingId;
	}

	public Optional<PendingAuthorization> consume(String pendingId) {
		String json = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + pendingId); // 원자적 조회+삭제(1회용)
		if (json == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(objectMapper.readValue(json, PendingAuthorization.class));
		} catch (Exception e) {
			return Optional.empty();
		}
	}
}
```

- [ ] **Step 5: application.yml 에 TTL 설정 추가**

`auth/src/main/resources/application.yml` 의 `my:` 블록에 아래 두 줄을 추가한다(기존 키는 그대로 둔다).

```yaml
my:
  pending-authorization-ttl-seconds: 300
  consent-base-url: http://localhost:8086
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `cd oauth-2/authorization-server/practice/microservice/auth && ./gradlew test --no-daemon --tests PendingAuthorizationStoreTest`
Expected: PASS (3 tests)

- [ ] **Step 7: Commit**

```bash
git add oauth-2/authorization-server/practice/microservice/auth
git commit -m "microservice: auth pending authorization store

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 5: auth — consent 서비스 클라이언트 + code 에 nonce/authTime 전달

**Files:**
- Create: `auth/src/main/java/dev/starryeye/auth/client/ConsentClient.java`
- Create: `auth/src/main/java/dev/starryeye/auth/client/ConsentInfo.java`
- Modify: `auth/src/main/java/dev/starryeye/auth/AuthorizationCodeIssuer.java`
- Test: `auth/src/test/java/dev/starryeye/auth/AuthorizationCodeIssuerTest.java`

**Interfaces:**
- Consumes: consent 서비스 `GET /internal/consents/{sub}/{clientId}`, `POST /internal/consents`
- Produces:
  - `ConsentInfo(String sub, String clientId, List<String> scopes)` (record)
  - `ConsentClient.getGrantedScopes(String sub, String clientId) → List<String>`
  - `ConsentClient.saveConsent(String sub, String clientId, List<String> scopes) → void`
  - `AuthorizationCodeIssuer.issue(String clientId, String redirectUri, String scope, String sub, String codeChallenge, String nonce, long authTime) → String code` (**시그니처 확장 — 인자 7개**)

- [ ] **Step 1: 계약 테스트 갱신 (실패 테스트)**

`AuthorizationCodeIssuerTest` 의 기존 테스트를 아래로 교체한다. 저장 JSON 에 nonce·authTime 이 포함되는지까지 검증한다.

```java
	@Test
	void issueStoresCodeWithTtlAndReturnsCode() throws Exception {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);

		AuthorizationCodeIssuer issuer = new AuthorizationCodeIssuer(redis, new ObjectMapper(), 60);
		String code = issuer.issue("my-client", "http://127.0.0.1:8080/callback", "openid profile",
				"user-sub-0001", "chal", "n-0S6_WzA2Mj", 1700000000L);

		assertThat(code).isNotBlank();

		org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
		org.mockito.ArgumentCaptor<String> valueCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(ops).set(keyCaptor.capture(), valueCaptor.capture(),
				org.mockito.ArgumentMatchers.eq(java.time.Duration.ofSeconds(60)));

		assertThat(keyCaptor.getValue()).isEqualTo("auth:code:" + code);
		java.util.Map<String, Object> json = new ObjectMapper()
				.readValue(valueCaptor.getValue(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
		assertThat(json)
				.containsEntry("clientId", "my-client")
				.containsEntry("redirectUri", "http://127.0.0.1:8080/callback")
				.containsEntry("scope", "openid profile")
				.containsEntry("sub", "user-sub-0001")
				.containsEntry("codeChallenge", "chal")
				.containsEntry("nonce", "n-0S6_WzA2Mj")
				.containsEntry("authTime", 1700000000);
	}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd oauth-2/authorization-server/practice/microservice/auth && ./gradlew test --no-daemon --tests AuthorizationCodeIssuerTest`
Expected: FAIL — `issue(...)` 인자 개수 불일치(컴파일 에러)

- [ ] **Step 3: AuthorizationCodeIssuer 확장**

`issue` 메서드를 아래로 교체한다(클래스의 나머지 필드·생성자는 그대로).

```java
	/**
	 * authorization code 를 만들어 Redis 에 저장한다. (token 이 소비할 공유 계약)
	 *      key "auth:code:{code}", value 는 {clientId, redirectUri, scope, sub, codeChallenge, nonce, authTime} JSON.
	 *
	 * nonce/authTime 을 함께 싣는 이유..
	 *      두 값은 authorize 시점(이 서비스)에만 알 수 있는데 정작 필요한 곳은 id token 을 만드는 token 서비스다.
	 *      client 가 token 요청에 실어 보내게 하면 조작 가능하므로, 서버끼리만 오가는 code 레코드에 담아 전달한다.
	 *      (표준은 id token 에 nonce/auth_time 이 규칙대로 담길 것을 요구할 뿐 나르는 방법은 규정하지 않는다)
	 */
	public String issue(String clientId, String redirectUri, String scope, String sub, String codeChallenge,
			String nonce, long authTime) {
		String code = UUID.randomUUID().toString().replace("-", "");
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("clientId", clientId);
		data.put("redirectUri", redirectUri);
		data.put("scope", scope);
		data.put("sub", sub);
		data.put("codeChallenge", codeChallenge);
		data.put("nonce", nonce);
		data.put("authTime", authTime);
		try {
			redisTemplate.opsForValue().set(KEY_PREFIX + code, objectMapper.writeValueAsString(data), Duration.ofSeconds(ttlSeconds));
		} catch (Exception e) {
			throw new IllegalStateException("failed to store authorization code", e);
		}
		return code;
	}
```

- [ ] **Step 4: ConsentInfo record 작성**

```java
package dev.starryeye.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConsentInfo(String sub, String clientId, List<String> scopes) {
}
```

- [ ] **Step 5: ConsentClient 구현**

```java
package dev.starryeye.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class ConsentClient {

	/**
	 * consent 서비스의 동의 기록 API 를 호출한다.
	 *
	 * 주의. 조회 실패(consent 다운 등)는 예외로 전파해 fail-closed 로 처리한다.
	 *      "승인 여부를 모른다" 를 "승인했다" 로 취급하면 동의 없이 토큰이 발급된다.
	 */

	private final RestClient restClient;

	public ConsentClient(RestClient.Builder builder, @Value("${my.consent-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public List<String> getGrantedScopes(String sub, String clientId) {
		ConsentInfo consent = restClient.get()
				.uri("/internal/consents/{sub}/{clientId}", sub, clientId)
				.retrieve()
				.body(ConsentInfo.class);
		return (consent == null || consent.scopes() == null) ? List.of() : consent.scopes();
	}

	public void saveConsent(String sub, String clientId, List<String> scopes) {
		restClient.post()
				.uri("/internal/consents")
				.body(Map.of("sub", sub, "clientId", clientId, "scopes", scopes))
				.retrieve()
				.toBodilessEntity();
	}
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `cd oauth-2/authorization-server/practice/microservice/auth && ./gradlew test --no-daemon --tests AuthorizationCodeIssuerTest`
Expected: FAIL — `AuthorizeController` 가 아직 6인자 `issue(...)` 를 호출해 컴파일 에러. 이 에러는 Task 6 에서 해소된다.

주의. 이 태스크는 컴파일이 깨진 상태로 끝나므로 **Task 6 과 함께 커밋**한다. Step 7 은 수행하지 않고 Task 6 으로 진행한다.

- [ ] **Step 7: (커밋하지 않음 — Task 6 에서 함께 커밋)**

---

## Task 6: auth — 동의 화면과 제출 처리

**Files:**
- Modify: `auth/src/main/java/dev/starryeye/auth/AuthorizeController.java`
- Create: `auth/src/main/java/dev/starryeye/auth/ConsentPageController.java`
- Create: `auth/src/main/resources/templates/consent.html`
- Modify: `auth/build.gradle` (thymeleaf 추가)
- Modify: `auth/src/main/java/dev/starryeye/auth/security/SecurityConfig.java`
- Test: `auth/src/test/java/dev/starryeye/auth/ConsentPageControllerTest.java`

**Interfaces:**
- Consumes: `ConsentClient.getGrantedScopes/saveConsent`, `PendingAuthorizationStore.save/consume`, `AuthorizationCodeIssuer.issue(7 args)`
- Produces: `GET /oauth2/authorize` 가 미승인 scope 존재 시 동의 화면 렌더, `POST /oauth2/consent` 가 동의 저장 후 code 발급

- [ ] **Step 1: build.gradle 에 thymeleaf 추가**

`auth/build.gradle` 의 dependencies 블록에 한 줄 추가:

```groovy
	implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
```

- [ ] **Step 2: 실패 테스트 작성**

```java
package dev.starryeye.auth;

import dev.starryeye.auth.client.ConsentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsentPageController.class)
class ConsentPageControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	PendingAuthorizationStore pendingStore;

	@MockitoBean
	ConsentClient consentClient;

	@MockitoBean
	AuthorizationCodeIssuer codeIssuer;

	private PendingAuthorization pending() {
		return new PendingAuthorization("my-client", "http://127.0.0.1:8080/callback",
				"openid profile email", "user-sub-0001", "chal", "xyz789", "nonce-1", 1700000000L);
	}

	@Test
	void approvedScopesAreSavedAndCodeIssued() throws Exception {
		when(pendingStore.consume("p1")).thenReturn(Optional.of(pending()));
		when(codeIssuer.issue(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong()))
				.thenReturn("issued-code");

		mockMvc.perform(post("/oauth2/consent").with(user("user-sub-0001")).with(csrf())
						.param("pending_id", "p1")
						.param("scope", "openid").param("scope", "profile"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("http://127.0.0.1:8080/callback?code=issued-code*"));

		verify(consentClient).saveConsent(eq("user-sub-0001"), eq("my-client"), any());
	}

	@Test
	void scopesBeyondPendingAreIgnored() throws Exception {
		when(pendingStore.consume("p1")).thenReturn(Optional.of(pending()));
		when(codeIssuer.issue(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong()))
				.thenReturn("issued-code");

		// pending 에 없는 admin 을 끼워 제출해도 승인되면 안 된다 (폼 조작 방어)
		mockMvc.perform(post("/oauth2/consent").with(user("user-sub-0001")).with(csrf())
						.param("pending_id", "p1")
						.param("scope", "openid").param("scope", "admin"))
				.andExpect(status().is3xxRedirection());

		verify(consentClient).saveConsent("user-sub-0001", "my-client", List.of("openid"));
	}

	@Test
	void denyingEverythingRedirectsWithAccessDenied() throws Exception {
		when(pendingStore.consume("p1")).thenReturn(Optional.of(pending()));

		mockMvc.perform(post("/oauth2/consent").with(user("user-sub-0001")).with(csrf())
						.param("pending_id", "p1"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("http://127.0.0.1:8080/callback?error=access_denied*"));

		verify(codeIssuer, never()).issue(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
	}

	@Test
	void unknownPendingReturnsErrorPageWithoutRedirect() throws Exception {
		when(pendingStore.consume("gone")).thenReturn(Optional.empty());

		mockMvc.perform(post("/oauth2/consent").with(user("user-sub-0001")).with(csrf())
						.param("pending_id", "gone")
						.param("scope", "openid"))
				.andExpect(status().isBadRequest());
	}
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd oauth-2/authorization-server/practice/microservice/auth && ./gradlew test --no-daemon --tests ConsentPageControllerTest`
Expected: FAIL — `ConsentPageController` 없음

- [ ] **Step 4: consent.html 작성**

`auth/src/main/resources/templates/consent.html`:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>동의 요청</title>
</head>
<body>
<h1>동의 요청</h1>
<p><span th:text="${clientId}">client</span> 이(가) 아래 권한을 요청합니다.</p>

<form method="post" th:action="@{/oauth2/consent}">
    <input type="hidden" name="pending_id" th:value="${pendingId}"/>

    <div th:if="${not #lists.isEmpty(grantedScopes)}">
        <p>이미 승인한 권한</p>
        <ul>
            <li th:each="granted : ${grantedScopes}" th:text="${granted}">openid</li>
        </ul>
    </div>

    <p>새로 요청된 권한</p>
    <ul>
        <li th:each="requested : ${requestedScopes}">
            <label>
                <input type="checkbox" name="scope" th:value="${requested}" checked/>
                <span th:text="${requested}">profile</span>
            </label>
        </li>
    </ul>

    <button type="submit">동의</button>
</form>
</body>
</html>
```

- [ ] **Step 5: ConsentPageController 구현**

```java
package dev.starryeye.auth;

import dev.starryeye.auth.client.ConsentClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class ConsentPageController {

	/**
	 * 동의 화면 제출을 처리한다. (화면 렌더는 AuthorizeController 가 담당)
	 *      승인 scope 는 "제출값 ∩ pending 의 scope" 로 계산한다. 폼을 조작해 상위 scope 를 승인할 수 없게 하기 위함이다.
	 *      승인이 하나도 없으면 표준 에러 access_denied 로 redirect 한다.
	 *
	 * 주의. pending 이 없거나 만료됐으면 redirect 하지 않고 에러 페이지로 끝낸다.
	 *      pending 이 없으면 redirect_uri 를 신뢰할 수 없어 open redirect 통로가 된다.
	 */

	private final PendingAuthorizationStore pendingStore;
	private final ConsentClient consentClient;
	private final AuthorizationCodeIssuer codeIssuer;

	@PostMapping("/oauth2/consent")
	public Object consent(
			@RequestParam("pending_id") String pendingId,
			@RequestParam(value = "scope", required = false) List<String> submittedScopes
	) {
		Optional<PendingAuthorization> maybePending = pendingStore.consume(pendingId);
		if (maybePending.isEmpty()) {
			return ResponseEntity.badRequest().body("consent error: pending authorization not found or expired");
		}
		PendingAuthorization pending = maybePending.get();

		List<String> requested = List.of(pending.scope().split(" "));
		List<String> approved = new ArrayList<>();
		if (submittedScopes != null) {
			for (String submitted : submittedScopes) {
				if (requested.contains(submitted)) { // pending 범위 밖은 버린다
					approved.add(submitted);
				}
			}
		}

		if (approved.isEmpty()) {
			return errorRedirect(pending.redirectUri(), "access_denied", pending.state());
		}

		consentClient.saveConsent(pending.sub(), pending.clientId(), approved);

		String code = codeIssuer.issue(pending.clientId(), pending.redirectUri(),
				String.join(" ", approved), pending.sub(), pending.codeChallenge(),
				pending.nonce(), pending.authTime());

		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(pending.redirectUri())
				.queryParam("code", code);
		if (StringUtils.hasText(pending.state())) {
			builder.queryParam("state", pending.state());
		}
		return new RedirectView(builder.encode().build().toUriString());
	}

	private RedirectView errorRedirect(String redirectUri, String error, String state) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(redirectUri).queryParam("error", error);
		if (StringUtils.hasText(state)) {
			builder.queryParam("state", state);
		}
		return new RedirectView(builder.encode().build().toUriString());
	}
}
```

- [ ] **Step 6: AuthorizeController 에 동의 분기 추가**

`AuthorizeController` 를 아래처럼 수정한다.

(a) 필드에 협력자 추가 — 기존 `clientRegistryClient`, `codeIssuer` 옆에:
```java
	private final ConsentClient consentClient;
	private final PendingAuthorizationStore pendingStore;
```

(b) import 추가:
```java
import dev.starryeye.auth.client.ConsentClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import java.util.ArrayList;
import java.util.List;
```

(c) 메서드 시그니처에 `nonce` 파라미터와 `Model` 을 추가:
```java
			@RequestParam(value = "nonce", required = false) String nonce,
			Model model,
```

(d) 기존 scope 검증 루프 **다음**, `codeIssuer.issue(...)` 호출 **앞** 부분을 아래로 교체한다.

```java
		// 동의 확인.. consent 서비스가 이 사용자/client 에 대해 이미 승인한 scope 를 조회한다
		List<String> granted = consentClient.getGrantedScopes(principal.getName(), clientId);
		List<String> requested = List.of(effectiveScope.split(" "));
		List<String> missing = new ArrayList<>();
		for (String requestedScope : requested) {
			if (!granted.contains(requestedScope)) {
				missing.add(requestedScope);
			}
		}

		long authTime = java.time.Instant.now().getEpochSecond();

		if (!missing.isEmpty()) {
			// 미승인 scope 가 있으면 동의 화면으로 보낸다. 진행 중 인가는 서버(Redis)에 두고 화면에는 불투명 id 만 노출한다.
			String pendingId = pendingStore.save(new PendingAuthorization(
					clientId, redirectUri, effectiveScope, principal.getName(), codeChallenge, state, nonce, authTime));
			model.addAttribute("pendingId", pendingId);
			model.addAttribute("clientId", clientId);
			model.addAttribute("requestedScopes", missing);
			model.addAttribute("grantedScopes", granted);
			return "consent";
		}

		String code = codeIssuer.issue(clientId, redirectUri, effectiveScope, principal.getName(), codeChallenge,
				nonce, authTime);
```

기존의 `String code = codeIssuer.issue(clientId, redirectUri, effectiveScope, principal.getName(), codeChallenge);` 한 줄은 위 블록으로 대체되어 사라진다. 그 아래 `UriComponentsBuilder ... return new RedirectView(...)` 부분은 그대로 둔다.

주의. `authTime` 은 실제 로그인 시각이 이상적이지만 이 슬라이스에서는 authorize 처리 시각을 사용한다. 세션에 로그인 시각을 심는 것은 이후 개선 항목이다.

- [ ] **Step 7: SecurityConfig 에 consent 경로 CSRF 예외 없이 인증 요구 확인**

`SecurityConfig` 의 `authorizeHttpRequests` 는 이미 `.anyRequest().authenticated()` 이므로 `/oauth2/consent` 도 인증이 필요하다. **변경 불필요**하지만, thymeleaf 폼은 CSRF 토큰을 자동 포함하므로 `consent.html` 의 `th:action` 사용이 필수다(Step 4 에서 반영됨). 이 Step 에서는 파일을 열어 `.anyRequest().authenticated()` 가 있는지 확인만 한다.

Run: `grep -n "anyRequest" oauth-2/authorization-server/practice/microservice/auth/src/main/java/dev/starryeye/auth/security/SecurityConfig.java`
Expected: `.anyRequest().authenticated()` 출력

- [ ] **Step 8: 테스트 통과 확인**

Run: `cd oauth-2/authorization-server/practice/microservice/auth && ./gradlew test --no-daemon`
Expected: PASS — `PendingAuthorizationStoreTest`(3) + `AuthorizationCodeIssuerTest`(1) + `ConsentPageControllerTest`(4) + 기존 테스트

- [ ] **Step 9: Commit (Task 5 변경분 포함)**

```bash
git add oauth-2/authorization-server/practice/microservice/auth
git commit -m "microservice: auth consent screen with pending authorization

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 7: token — id token 발급

**Files:**
- Modify: `token/src/main/java/dev/starryeye/token/AuthorizationCodeData.java`
- Create: `token/src/main/java/dev/starryeye/token/IdTokenIssuer.java`
- Create: `token/src/main/java/dev/starryeye/token/client/UserDirectoryClient.java`
- Create: `token/src/main/java/dev/starryeye/token/client/UserProfile.java`
- Modify: `token/src/main/java/dev/starryeye/token/dto/TokenResponse.java`
- Modify: `token/src/main/java/dev/starryeye/token/TokenEndpointController.java`
- Modify: `token/src/main/resources/application.yml`
- Test: `token/src/test/java/dev/starryeye/token/IdTokenIssuerTest.java`

**Interfaces:**
- Consumes: `SigningClient.sign(Map<String,Object> claims) → String jwt`, user-directory `GET /internal/users/{sub}`
- Produces:
  - `UserProfile(String sub, String username, List<String> authorities, String name, String nickname, String preferredUsername, String email, boolean emailVerified)` (record)
  - `UserDirectoryClient.getUser(String sub) → UserProfile` (404 시 null)
  - `IdTokenIssuer.issue(String sub, String clientId, String scope, String nonce, long authTime, String accessToken) → String idToken`
  - `IdTokenIssuer.computeAtHash(String accessToken) → String`
  - `TokenResponse(String access_token, String token_type, long expires_in, String scope, String id_token)` (**필드 추가**)

- [ ] **Step 1: 실패 테스트 작성 (at_hash 는 알려진 값으로 대조)**

```java
package dev.starryeye.token;

import com.nimbusds.jwt.SignedJWT;
import dev.starryeye.token.client.SigningClient;
import dev.starryeye.token.client.UserDirectoryClient;
import dev.starryeye.token.client.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdTokenIssuerTest {

	SigningClient signingClient;
	UserDirectoryClient userDirectoryClient;
	IdTokenIssuer issuer;

	@BeforeEach
	void setUp() {
		signingClient = mock(SigningClient.class);
		userDirectoryClient = mock(UserDirectoryClient.class);
		when(signingClient.sign(anyMap())).thenReturn("signed.jwt.value");
		issuer = new IdTokenIssuer(signingClient, userDirectoryClient, "http://localhost:9000", 300);
	}

	private UserProfile profile() {
		return new UserProfile("user-sub-0001", "user", List.of("ROLE_USER"),
				"Star Rye", "starry", "starryeye", "starryeye@example.com", true);
	}

	// OIDC Core 3.1.3.6 예시 벡터: access_token "jHkWEdUXMU1BwAsC4vtUsZwnNvTIxEl0z9K3vx5KF0Y" 의 at_hash 는 "77QmUPtjPfzWtF2AnpK9RQ"
	@Test
	void computesAtHashPerSpec() {
		assertThat(issuer.computeAtHash("jHkWEdUXMU1BwAsC4vtUsZwnNvTIxEl0z9K3vx5KF0Y"))
				.isEqualTo("77QmUPtjPfzWtF2AnpK9RQ");
	}

	@Test
	void includesRequiredClaimsAndNonce() {
		when(userDirectoryClient.getUser("user-sub-0001")).thenReturn(profile());

		issuer.issue("user-sub-0001", "my-client", "openid", "nonce-1", 1700000000L, "access-token-value");

		ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
		verify(signingClient).sign(captor.capture());
		Map<String, Object> claims = captor.getValue();

		assertThat(claims).containsEntry("iss", "http://localhost:9000");
		assertThat(claims).containsEntry("sub", "user-sub-0001");
		assertThat(claims).containsEntry("aud", "my-client");
		assertThat(claims).containsKeys("exp", "iat");
		assertThat(claims).containsEntry("nonce", "nonce-1");
		assertThat(claims).containsEntry("auth_time", 1700000000L);
		assertThat(claims).containsKey("at_hash");
	}

	@Test
	void omitsNonceWhenNotRequested() {
		when(userDirectoryClient.getUser("user-sub-0001")).thenReturn(profile());

		issuer.issue("user-sub-0001", "my-client", "openid", null, 1700000000L, "access-token-value");

		ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
		verify(signingClient).sign(captor.capture());
		assertThat(captor.getValue()).doesNotContainKey("nonce");
	}

	@Test
	void includesProfileClaimsOnlyWhenScopePresent() {
		when(userDirectoryClient.getUser("user-sub-0001")).thenReturn(profile());

		issuer.issue("user-sub-0001", "my-client", "openid profile", null, 1700000000L, "at");

		ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
		verify(signingClient).sign(captor.capture());
		Map<String, Object> claims = captor.getValue();
		assertThat(claims).containsEntry("name", "Star Rye");
		assertThat(claims).containsEntry("nickname", "starry");
		assertThat(claims).containsEntry("preferred_username", "starryeye");
		assertThat(claims).doesNotContainKey("email");
	}

	@Test
	void includesEmailClaimsWhenScopePresent() {
		when(userDirectoryClient.getUser("user-sub-0001")).thenReturn(profile());

		issuer.issue("user-sub-0001", "my-client", "openid email", null, 1700000000L, "at");

		ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
		verify(signingClient).sign(captor.capture());
		Map<String, Object> claims = captor.getValue();
		assertThat(claims).containsEntry("email", "starryeye@example.com");
		assertThat(claims).containsEntry("email_verified", true);
		assertThat(claims).doesNotContainKey("name");
	}

	@Test
	void issuesWithoutProfileClaimsWhenUserDirectoryFails() {
		when(userDirectoryClient.getUser("user-sub-0001")).thenThrow(new RuntimeException("user-directory down"));

		String idToken = issuer.issue("user-sub-0001", "my-client", "openid profile", null, 1700000000L, "at");

		assertThat(idToken).isEqualTo("signed.jwt.value");
		ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
		verify(signingClient).sign(captor.capture());
		Map<String, Object> claims = captor.getValue();
		assertThat(claims).containsEntry("sub", "user-sub-0001"); // 필수 claim 은 유지
		assertThat(claims).doesNotContainKey("name");             // 프로필만 degrade
	}
}
```

주의. `SignedJWT` import 는 이 테스트에서 사용하지 않으면 제거한다(경고 방지).

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd oauth-2/authorization-server/practice/microservice/token && ./gradlew test --no-daemon --tests IdTokenIssuerTest`
Expected: FAIL — `IdTokenIssuer`/`UserDirectoryClient`/`UserProfile` 없음

- [ ] **Step 3: UserProfile record + UserDirectoryClient 작성**

`token/src/main/java/dev/starryeye/token/client/UserProfile.java`:
```java
package dev.starryeye.token.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserProfile(
		String sub,
		String username,
		List<String> authorities,
		String name,
		String nickname,
		String preferredUsername,
		String email,
		boolean emailVerified
) {
}
```

`token/src/main/java/dev/starryeye/token/client/UserDirectoryClient.java`:
```java
package dev.starryeye.token.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserDirectoryClient {

	/**
	 * user-directory 에서 사용자 프로필을 조회한다. (id token claim, userinfo 응답의 원본)
	 *      user-directory 는 내부 전용 API 라 외부에 노출되지 않으며, 이 서비스가 토큰 검증을 마친 뒤에만 호출한다.
	 */

	private final RestClient restClient;

	public UserDirectoryClient(RestClient.Builder builder, @Value("${my.user-directory-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public UserProfile getUser(String sub) {
		return restClient.get()
				.uri("/internal/users/{sub}", sub)
				.retrieve()
				.onStatus(status -> status.value() == 404, (req, res) -> { throw new UserNotFoundException(); })
				.body(UserProfile.class);
	}

	public static class UserNotFoundException extends RuntimeException {
	}
}
```

- [ ] **Step 4: IdTokenIssuer 구현**

```java
package dev.starryeye.token;

import dev.starryeye.token.client.SigningClient;
import dev.starryeye.token.client.UserDirectoryClient;
import dev.starryeye.token.client.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class IdTokenIssuer {

	/**
	 * id token 을 만들어 signing 에 서명을 위임한다. (openid scope 요청 시에만 호출된다)
	 *      필수 claim(iss/sub/aud/exp/iat)에 더해 nonce(요청에 있었으면), auth_time, at_hash 를 담고..
	 *      scope 에 따라 profile/email claim 을 덧붙인다.
	 *
	 * 주의. user-directory 가 응답하지 않아도 id token 발급 자체는 계속한다.
	 *      필수 claim 만으로도 표준상 유효한 id token 이므로, 인증(누가 로그인했는가)을 프로필 조회 실패로 막지 않는다.
	 */

	private final SigningClient signingClient;
	private final UserDirectoryClient userDirectoryClient;
	private final String issuer;
	private final long idTokenTtlSeconds;

	public IdTokenIssuer(
			SigningClient signingClient,
			UserDirectoryClient userDirectoryClient,
			@Value("${my.issuer}") String issuer,
			@Value("${my.id-token-ttl-seconds}") long idTokenTtlSeconds
	) {
		this.signingClient = signingClient;
		this.userDirectoryClient = userDirectoryClient;
		this.issuer = issuer;
		this.idTokenTtlSeconds = idTokenTtlSeconds;
	}

	public String issue(String sub, String clientId, String scope, String nonce, long authTime, String accessToken) {

		Instant now = Instant.now();
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("iss", issuer);
		claims.put("sub", sub);
		claims.put("aud", clientId);
		claims.put("iat", now.getEpochSecond());
		claims.put("exp", now.plusSeconds(idTokenTtlSeconds).getEpochSecond());
		claims.put("auth_time", authTime);
		claims.put("at_hash", computeAtHash(accessToken));
		if (StringUtils.hasText(nonce)) {
			claims.put("nonce", nonce); // 요청에 있었으면 그대로 되돌려준다 (표준 요구)
		}

		List<String> scopes = Arrays.asList(scope.split(" "));
		if (scopes.contains("profile") || scopes.contains("email")) {
			addProfileClaims(claims, sub, scopes);
		}

		return signingClient.sign(claims);
	}

	/**
	 * at_hash = BASE64URL( SHA-256(access_token) 의 좌측 절반 ). (alg 가 RS256 이므로 SHA-256)
	 */
	public String computeAtHash(String accessToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(accessToken.getBytes(StandardCharsets.US_ASCII));
			byte[] leftHalf = Arrays.copyOf(digest, digest.length / 2);
			return Base64.getUrlEncoder().withoutPadding().encodeToString(leftHalf);
		} catch (Exception e) {
			throw new IllegalStateException("failed to compute at_hash", e);
		}
	}

	private void addProfileClaims(Map<String, Object> claims, String sub, List<String> scopes) {
		UserProfile profile;
		try {
			profile = userDirectoryClient.getUser(sub);
		} catch (Exception e) {
			log.warn("user-directory 조회 실패.. 프로필 claim 없이 id token 을 발급한다. sub={}", sub);
			return;
		}
		if (profile == null) {
			return;
		}
		if (scopes.contains("profile")) {
			putIfPresent(claims, "name", profile.name());
			putIfPresent(claims, "nickname", profile.nickname());
			putIfPresent(claims, "preferred_username", profile.preferredUsername());
		}
		if (scopes.contains("email")) {
			putIfPresent(claims, "email", profile.email());
			claims.put("email_verified", profile.emailVerified());
		}
	}

	private void putIfPresent(Map<String, Object> claims, String key, String value) {
		if (StringUtils.hasText(value)) {
			claims.put(key, value);
		}
	}
}
```

- [ ] **Step 5: AuthorizationCodeData 에 nonce/authTime 추가**

```java
package dev.starryeye.token;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthorizationCodeData(
		String clientId,
		String redirectUri,
		String scope,
		String sub,
		String codeChallenge,
		String nonce,
		long authTime
) {
}
```

- [ ] **Step 6: TokenResponse 에 id_token 추가**

```java
package dev.starryeye.token.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(String access_token, String token_type, long expires_in, String scope, String id_token) {
}
```

- [ ] **Step 7: application.yml 에 설정 추가**

`token/src/main/resources/application.yml` 의 `my:` 블록에 아래를 추가한다.

```yaml
my:
  user-directory-base-url: http://localhost:8084
  id-token-ttl-seconds: 300
```

- [ ] **Step 8: TokenEndpointController 에서 id token 발급 연결**

(a) 필드 추가:
```java
	private final IdTokenIssuer idTokenIssuer;
```

(b) `String jwt = signingClient.sign(claims);` 아래의 응답 생성부를 아래로 교체:
```java
		String jwt = signingClient.sign(claims);

		// openid scope 요청 시 id token 을 함께 발급한다 (OIDC)
		String idToken = null;
		if (Arrays.asList(data.scope().split(" ")).contains("openid")) {
			idToken = idTokenIssuer.issue(data.sub(), client.clientId(), data.scope(),
					data.nonce(), data.authTime(), jwt);
		}

		return ResponseEntity.ok(new TokenResponse(jwt, "Bearer", accessTokenTtlSeconds, data.scope(), idToken));
```

- [ ] **Step 9: 기존 token 테스트의 TokenResponse/AuthorizationCodeData 사용부 수정**

`TokenEndpointControllerTest` 에서 `new AuthorizationCodeData(...)` 를 호출하는 곳에 nonce·authTime 인자를 추가한다(예: `new AuthorizationCodeData("my-client", "http://127.0.0.1:8080/callback", "openid", "user-sub-0001", "chal", null, 1700000000L)`). `@MockitoBean IdTokenIssuer idTokenIssuer;` 를 테스트 클래스에 추가해 컨텍스트 로딩을 만족시킨다.

`AuthorizationCodeStoreTest` 의 stub JSON 은 `@JsonIgnoreProperties` 덕에 그대로 두어도 통과하지만, `nonce`·`authTime` 을 추가해 계약을 명시하는 편이 낫다.

- [ ] **Step 10: 테스트 통과 확인**

Run: `cd oauth-2/authorization-server/practice/microservice/token && ./gradlew test --no-daemon`
Expected: PASS — `IdTokenIssuerTest`(6) + 기존 token 테스트 전부

- [ ] **Step 11: Commit**

```bash
git add oauth-2/authorization-server/practice/microservice/token
git commit -m "microservice: issue id token for openid scope

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 8: token — userinfo 엔드포인트

**Files:**
- Create: `token/src/main/java/dev/starryeye/token/AccessTokenVerifier.java`
- Create: `token/src/main/java/dev/starryeye/token/UserInfoController.java`
- Test: `token/src/test/java/dev/starryeye/token/UserInfoControllerTest.java`

**Interfaces:**
- Consumes: `SigningClient.jwks() → Map<?,?>`, `UserDirectoryClient.getUser(String) → UserProfile`
- Produces:
  - `AccessTokenVerifier.verify(String token) → VerifiedToken`
  - `AccessTokenVerifier.VerifiedToken(String sub, List<String> scopes)` (record)
  - `AccessTokenVerifier.InvalidTokenException` (RuntimeException)
  - `GET /userinfo` → `{sub, ...scope 대응 claim}`

- [ ] **Step 1: 실패 테스트 작성**

```java
package dev.starryeye.token;

import dev.starryeye.token.client.UserDirectoryClient;
import dev.starryeye.token.client.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserInfoController.class)
class UserInfoControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	AccessTokenVerifier accessTokenVerifier;

	@MockitoBean
	UserDirectoryClient userDirectoryClient;

	private UserProfile profile() {
		return new UserProfile("user-sub-0001", "user", List.of("ROLE_USER"),
				"Star Rye", "starry", "starryeye", "starryeye@example.com", true);
	}

	@Test
	void returnsOnlySubWhenScopeIsOpenidOnly() throws Exception {
		when(accessTokenVerifier.verify("tok")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("user-sub-0001", List.of("openid")));
		when(userDirectoryClient.getUser("user-sub-0001")).thenReturn(profile());

		mockMvc.perform(get("/userinfo").header("Authorization", "Bearer tok"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sub").value("user-sub-0001"))
				.andExpect(jsonPath("$.name").doesNotExist())
				.andExpect(jsonPath("$.email").doesNotExist());
	}

	@Test
	void returnsProfileClaimsWhenProfileScopePresent() throws Exception {
		when(accessTokenVerifier.verify("tok")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("user-sub-0001", List.of("openid", "profile")));
		when(userDirectoryClient.getUser("user-sub-0001")).thenReturn(profile());

		mockMvc.perform(get("/userinfo").header("Authorization", "Bearer tok"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Star Rye"))
				.andExpect(jsonPath("$.nickname").value("starry"))
				.andExpect(jsonPath("$.preferred_username").value("starryeye"))
				.andExpect(jsonPath("$.email").doesNotExist());
	}

	@Test
	void returnsEmailClaimsWhenEmailScopePresent() throws Exception {
		when(accessTokenVerifier.verify("tok")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("user-sub-0001", List.of("openid", "email")));
		when(userDirectoryClient.getUser("user-sub-0001")).thenReturn(profile());

		mockMvc.perform(get("/userinfo").header("Authorization", "Bearer tok"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("starryeye@example.com"))
				.andExpect(jsonPath("$.email_verified").value(true))
				.andExpect(jsonPath("$.name").doesNotExist());
	}

	@Test
	void missingTokenReturns401WithBearerChallenge() throws Exception {
		mockMvc.perform(get("/userinfo"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"));
	}

	@Test
	void invalidTokenReturns401InvalidToken() throws Exception {
		when(accessTokenVerifier.verify("bad")).thenThrow(new AccessTokenVerifier.InvalidTokenException("bad signature"));

		mockMvc.perform(get("/userinfo").header("Authorization", "Bearer bad"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""));
	}

	@Test
	void tokenWithoutOpenidScopeReturns403InsufficientScope() throws Exception {
		when(accessTokenVerifier.verify("tok")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("user-sub-0001", List.of("profile")));

		mockMvc.perform(get("/userinfo").header("Authorization", "Bearer tok"))
				.andExpect(status().isForbidden())
				.andExpect(header().string("WWW-Authenticate", "Bearer error=\"insufficient_scope\""));
	}
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd oauth-2/authorization-server/practice/microservice/token && ./gradlew test --no-daemon --tests UserInfoControllerTest`
Expected: FAIL — `AccessTokenVerifier`/`UserInfoController` 없음

- [ ] **Step 3: AccessTokenVerifier 구현**

```java
package dev.starryeye.token;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.starryeye.token.client.SigningClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AccessTokenVerifier {

	/**
	 * userinfo 요청이 실어온 access token 을 자체 검증한다.
	 *      서명은 signing 이 공개하는 jwks 로, 그 외 iss/exp 를 확인한다.
	 *      -> 이 검증 역량이 이미 token 서비스에 있기 때문에 userinfo 를 별도 서비스로 빼지 않았다.
	 *
	 * 주의. jwks 를 매 요청 조회하면 signing 에 부하가 걸린다. 캐시는 이후 개선 항목이다.
	 */

	private final SigningClient signingClient;

	@Value("${my.issuer}")
	private String issuer;

	public record VerifiedToken(String sub, List<String> scopes) {
	}

	public static class InvalidTokenException extends RuntimeException {
		public InvalidTokenException(String message) {
			super(message);
		}
	}

	public VerifiedToken verify(String token) {

		SignedJWT signedJWT;
		JWTClaimsSet claims;
		try {
			signedJWT = SignedJWT.parse(token);
			claims = signedJWT.getJWTClaimsSet();
		} catch (Exception e) {
			throw new InvalidTokenException("malformed token");
		}

		try {
			JWKSet jwkSet = JWKSet.parse((Map<String, Object>) signingClient.jwks());
			RSAKey key = (RSAKey) jwkSet.getKeyByKeyId(signedJWT.getHeader().getKeyID());
			if (key == null || !signedJWT.verify(new RSASSAVerifier(key.toRSAPublicKey()))) {
				throw new InvalidTokenException("signature verification failed");
			}
		} catch (InvalidTokenException e) {
			throw e;
		} catch (Exception e) {
			throw new InvalidTokenException("cannot verify signature");
		}

		Date expiration = claims.getExpirationTime();
		if (expiration == null || expiration.before(new Date())) {
			throw new InvalidTokenException("token expired");
		}
		if (!issuer.equals(claims.getIssuer())) {
			throw new InvalidTokenException("issuer mismatch");
		}

		List<String> scopes = new ArrayList<>();
		try {
			List<String> claimScopes = claims.getStringListClaim("scope");
			if (claimScopes != null) {
				scopes.addAll(claimScopes);
			}
		} catch (Exception e) {
			throw new InvalidTokenException("malformed scope claim");
		}

		return new VerifiedToken(claims.getSubject(), scopes);
	}
}
```

- [ ] **Step 4: UserInfoController 구현**

```java
package dev.starryeye.token;

import dev.starryeye.token.client.UserDirectoryClient;
import dev.starryeye.token.client.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UserInfoController {

	/**
	 * OIDC userinfo 엔드포인트이다. access token 으로 인증하고 scope 에 대응하는 claim 만 돌려준다.
	 *      sub 는 항상 포함한다(표준 필수). profile/email scope 가 없으면 해당 claim 은 응답에서 제외한다.
	 *      에러는 RFC 6750 형식으로 WWW-Authenticate 헤더에 담는다.
	 */

	private final AccessTokenVerifier accessTokenVerifier;
	private final UserDirectoryClient userDirectoryClient;

	@GetMapping(value = "/userinfo", produces = "application/json")
	public ResponseEntity<?> userinfo(@RequestHeader(value = "Authorization", required = false) String authorization) {

		if (authorization == null || !authorization.startsWith("Bearer ")) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").build();
		}

		AccessTokenVerifier.VerifiedToken verified;
		try {
			verified = accessTokenVerifier.verify(authorization.substring(7));
		} catch (AccessTokenVerifier.InvalidTokenException e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"").build();
		}

		if (!verified.scopes().contains("openid")) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"").build();
		}

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("sub", verified.sub()); // 표준 필수

		UserProfile profile = userDirectoryClient.getUser(verified.sub());
		if (profile != null) {
			List<String> scopes = verified.scopes();
			if (scopes.contains("profile")) {
				putIfPresent(response, "name", profile.name());
				putIfPresent(response, "nickname", profile.nickname());
				putIfPresent(response, "preferred_username", profile.preferredUsername());
			}
			if (scopes.contains("email")) {
				putIfPresent(response, "email", profile.email());
				response.put("email_verified", profile.emailVerified());
			}
		}

		return ResponseEntity.ok(response);
	}

	private void putIfPresent(Map<String, Object> response, String key, String value) {
		if (StringUtils.hasText(value)) {
			response.put(key, value);
		}
	}
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd oauth-2/authorization-server/practice/microservice/token && ./gradlew test --no-daemon`
Expected: PASS — `UserInfoControllerTest`(6) + `IdTokenIssuerTest`(6) + 기존 테스트

- [ ] **Step 6: Commit**

```bash
git add oauth-2/authorization-server/practice/microservice/token
git commit -m "microservice: userinfo endpoint with scope filtering

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 9: gateway 라우팅 + discovery 확장

**Files:**
- Modify: `gateway/nginx.conf`
- Modify: `token/src/main/java/dev/starryeye/token/TokenEndpointController.java` (metadata 메서드)
- Modify: `client-registry/src/main/java/dev/starryeye/client_registry/ClientSeedInitializer.java` (seed scope 에 email 추가)

**Interfaces:**
- Produces: gateway 가 `/userinfo` → token, `/oauth2/consent` → auth 로 라우팅. discovery 문서에 OIDC 항목 추가.

- [ ] **Step 1: nginx.conf 확장**

`gateway/nginx.conf` 의 server 블록에 두 location 을 추가한다(기존 location 은 그대로).

```nginx
    # front-channel -> auth
    location /oauth2/consent   { proxy_pass http://auth-upstream;  proxy_set_header X-Forwarded-Host $http_host; }

    # back-channel -> token
    location /userinfo         { proxy_pass http://token-upstream; proxy_set_header X-Forwarded-Host $http_host; }
```

- [ ] **Step 2: discovery 메타데이터 확장**

`TokenEndpointController.metadata()` 를 아래로 교체한다.

```java
	@GetMapping({"/.well-known/oauth-authorization-server", "/.well-known/openid-configuration"})
	public Map<String, Object> metadata() {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("issuer", issuer);
		metadata.put("token_endpoint", issuer + "/oauth2/token");
		metadata.put("authorization_endpoint", issuer + "/oauth2/authorize");
		metadata.put("jwks_uri", issuer + "/oauth2/jwks");
		metadata.put("userinfo_endpoint", issuer + "/userinfo");
		metadata.put("code_challenge_methods_supported", List.of("S256"));
		metadata.put("grant_types_supported", List.of("authorization_code"));
		metadata.put("response_types_supported", List.of("code"));
		metadata.put("subject_types_supported", List.of("public"));
		metadata.put("id_token_signing_alg_values_supported", List.of("RS256"));
		metadata.put("scopes_supported", List.of("openid", "profile", "email"));
		metadata.put("claims_supported", List.of("sub", "iss", "aud", "exp", "iat", "auth_time", "nonce",
				"name", "nickname", "preferred_username", "email", "email_verified"));
		return metadata;
	}
```

- [ ] **Step 3: client seed 의 scope 에 email 추가**

`ClientSeedInitializer` 의 `.scopes("openid,profile")` 를 `.scopes("openid,profile,email")` 로 바꾼다.

- [ ] **Step 4: nginx 설정 문법 확인**

Run:
```bash
cd oauth-2/authorization-server/practice/microservice/docker-compose && docker compose -f docker-compose.yml config > /dev/null && echo "compose OK"
```
Expected: `compose OK`

- [ ] **Step 5: token 테스트 재확인**

Run: `cd oauth-2/authorization-server/practice/microservice/token && ./gradlew test --no-daemon`
Expected: PASS (기존 테스트 유지)

- [ ] **Step 6: Commit**

```bash
git add oauth-2/authorization-server/practice/microservice/gateway oauth-2/authorization-server/practice/microservice/token oauth-2/authorization-server/practice/microservice/client-registry
git commit -m "microservice: route userinfo and consent, expand discovery metadata

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 10: 관통 e2e 검증 + README

**Files:**
- Modify: `microservice/README.md`
- Create: `microservice/http/userinfo.http`

**Interfaces:**
- Consumes: 7개 서비스 전부 + 인프라

- [ ] **Step 1: 전체 빌드**

```bash
cd oauth-2/authorization-server/practice/microservice
for s in signing user-directory client-registry consent token auth; do (cd $s && ./gradlew bootJar --no-daemon -q); done
ls */build/libs/*.jar
```
Expected: 6개 jar

- [ ] **Step 2: 인프라 + 6개 서비스 기동**

```bash
cd oauth-2/authorization-server/practice/microservice
JAVA=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn/bin/java
docker compose -p microservice-as -f docker-compose/docker-compose.yml up -d
for i in $(seq 1 40); do docker exec microservice-as-mysql-1 mysqladmin ping -uroot -p1111 --silent 2>/dev/null && break; sleep 2; done
for s in signing user-directory client-registry consent token auth; do
  nohup $JAVA -jar $s/build/libs/$s-0.0.1-SNAPSHOT.jar > /tmp/ms2-$s.log 2>&1 &
  for i in $(seq 1 30); do grep -q "Started .*Application" /tmp/ms2-$s.log 2>/dev/null && break; sleep 2; done
  echo "$s: $(grep -c 'Started .*Application' /tmp/ms2-$s.log)"
done
```
Expected: 각 서비스 1

주의. 기존 프로세스가 포트를 점유하고 있으면 `lsof -tiTCP:8081 -sTCP:LISTEN | xargs kill -9` 형태로 정리한 뒤 재기동한다.

- [ ] **Step 3: 동의 화면 → id token 발급 (성공 기준 1, 2)**

```bash
cd /tmp
python3 - <<'EOF'
import hashlib, base64, secrets
v = secrets.token_urlsafe(48)
c = base64.urlsafe_b64encode(hashlib.sha256(v.encode()).digest()).rstrip(b'=').decode()
open('ms2-verifier.txt','w').write(v); open('ms2-challenge.txt','w').write(c)
EOF
CHAL=$(cat ms2-challenge.txt); VER=$(cat ms2-verifier.txt)
GW=http://localhost:9000
csrf() { grep -o 'name="_csrf"[^>]*value="[^"]*"' | sed 's/.*value="//;s/"$//'; }
rm -f ms2-cookies.txt
AUTHZ="$GW/oauth2/authorize?response_type=code&client_id=my-client&redirect_uri=http://127.0.0.1:8080/callback&scope=openid%20profile%20email&state=xyz&nonce=n-abc123&code_challenge=$CHAL&code_challenge_method=S256"

# 로그인
curl -s -c ms2-cookies.txt -o /dev/null "$AUTHZ"
CSRF=$(curl -s -b ms2-cookies.txt -c ms2-cookies.txt $GW/login | csrf)
curl -s -b ms2-cookies.txt -c ms2-cookies.txt -o /dev/null -X POST $GW/login -d "username=user&password=1111&_csrf=$CSRF"

# 동의 화면 확인 (성공 기준 1)
curl -s -b ms2-cookies.txt -c ms2-cookies.txt "$AUTHZ" > ms2-consent.html
grep -c "pending_id" ms2-consent.html   # 1 이면 동의 화면이 떴다
PENDING=$(grep -o 'name="pending_id"[^>]*value="[^"]*"' ms2-consent.html | sed 's/.*value="//;s/"$//')
CSRF2=$(cat ms2-consent.html | csrf)

# 동의 제출 -> code
CODE=$(curl -s -i -b ms2-cookies.txt -X POST "$GW/oauth2/consent" \
  --data-urlencode "pending_id=$PENDING" --data-urlencode "_csrf=$CSRF2" \
  --data-urlencode "scope=openid" --data-urlencode "scope=profile" --data-urlencode "scope=email" \
  | grep -i '^location' | grep -o 'code=[^&]*' | cut -d= -f2 | tr -d '\r')
echo "code: ${CODE:0:12}..."

# token 교환
curl -s -u my-client:secret -X POST $GW/oauth2/token \
  -d "grant_type=authorization_code&code=$CODE&redirect_uri=http://127.0.0.1:8080/callback&code_verifier=$VER" > ms2-token.json
python3 - <<'EOF'
import json, base64, hashlib
def b64d(s): return base64.urlsafe_b64decode(s + '='*(-len(s)%4))
d = json.load(open('ms2-token.json'))
assert 'id_token' in d, d
at = d['access_token']; it = d['id_token']
c = json.loads(b64d(it.split('.')[1]))
expected_at_hash = base64.urlsafe_b64encode(hashlib.sha256(at.encode()).digest()[:16]).rstrip(b'=').decode()
print('id_token claims:', {k: c.get(k) for k in ['iss','sub','aud','nonce','auth_time','name','email','email_verified']})
print('at_hash 일치:', c.get('at_hash') == expected_at_hash)
EOF
```
Expected: 동의 화면 grep 결과 1, `id_token` 존재, `nonce=n-abc123`, `at_hash 일치: True`

- [ ] **Step 4: id token 서명 검증 (성공 기준 2)**

```bash
cd /tmp
python3 - <<'EOF'
import json, base64, hashlib, urllib.request
def b64d(s): return base64.urlsafe_b64decode(s + '='*(-len(s)%4))
it = json.load(open('ms2-token.json'))['id_token']
h,p,sig = it.split('.')
kid = json.loads(b64d(h))['kid']
jwks = json.load(urllib.request.urlopen('http://localhost:9000/oauth2/jwks'))
key = next(k for k in jwks['keys'] if k['kid']==kid)
n=int.from_bytes(b64d(key['n'])); e=int.from_bytes(b64d(key['e']))
m=pow(int.from_bytes(b64d(sig)),e,n).to_bytes((n.bit_length()+7)//8)
print('id_token 서명 검증:', 'PASS' if m.endswith(hashlib.sha256(f'{h}.{p}'.encode()).digest()) else 'FAIL')
EOF
```
Expected: `id_token 서명 검증: PASS`

- [ ] **Step 5: userinfo + 재인가 동의 생략 (성공 기준 3, 4)**

```bash
cd /tmp
GW=http://localhost:9000
AT=$(python3 -c "import json; print(json.load(open('ms2-token.json'))['access_token'])")

echo "=== userinfo (openid profile email) ==="
curl -s -H "Authorization: Bearer $AT" $GW/userinfo | python3 -m json.tool

echo "=== 재인가: 동의 화면 없이 바로 code (성공 기준 3) ==="
CHAL=$(cat ms2-challenge.txt)
AUTHZ2="$GW/oauth2/authorize?response_type=code&client_id=my-client&redirect_uri=http://127.0.0.1:8080/callback&scope=openid%20profile%20email&nonce=n-2&code_challenge=$CHAL&code_challenge_method=S256"
curl -s -o /dev/null -w "재인가 응답: %{http_code} redirect=%{redirect_url}\n" -b ms2-cookies.txt "$AUTHZ2" | cut -c1-100
```
Expected: userinfo 에 sub·name·nickname·preferred_username·email·email_verified 포함. 재인가는 302 이고 redirect 에 `code=` 포함(동의 화면 없음)

- [ ] **Step 6: 부정 케이스 (성공 기준 4 필터링, 5, 6)**

```bash
cd /tmp
GW=http://localhost:9000
CHAL=$(cat ms2-challenge.txt); VER=$(cat ms2-verifier.txt)

echo "=== openid 만 있는 토큰의 userinfo -> 프로필 claim 미포함 ==="
# openid 만 요청 (이미 동의된 scope 의 부분집합이라 동의 화면 없음)
CODE_O=$(curl -s -i -b ms2-cookies.txt "$GW/oauth2/authorize?response_type=code&client_id=my-client&redirect_uri=http://127.0.0.1:8080/callback&scope=openid&code_challenge=$CHAL&code_challenge_method=S256" | grep -i '^location' | grep -o 'code=[^&]*' | cut -d= -f2 | tr -d '\r')
AT_O=$(curl -s -u my-client:secret -X POST $GW/oauth2/token -d "grant_type=authorization_code&code=$CODE_O&redirect_uri=http://127.0.0.1:8080/callback&code_verifier=$VER" | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])")
curl -s -H "Authorization: Bearer $AT_O" $GW/userinfo | python3 -m json.tool

echo "=== userinfo 무효 토큰 -> 401 invalid_token (성공 기준 6) ==="
curl -s -o /dev/null -D - -H "Authorization: Bearer not.a.token" $GW/userinfo | grep -iE "^HTTP|www-authenticate"

echo "=== 회귀: code 재사용 -> invalid_grant (성공 기준 7) ==="
curl -s -u my-client:secret -X POST $GW/oauth2/token -d "grant_type=authorization_code&code=$CODE_O&redirect_uri=http://127.0.0.1:8080/callback&code_verifier=$VER" | python3 -c "import json,sys; print(json.load(sys.stdin).get('error'))"
```
Expected: openid 전용 토큰의 userinfo 는 `sub` 만, 무효 토큰은 401 + `Bearer error="invalid_token"`, code 재사용은 `invalid_grant`

- [ ] **Step 7: 동의 거부 확인 (성공 기준 5)**

```bash
cd /tmp
GW=http://localhost:9000
CHAL=$(cat ms2-challenge.txt)
csrf() { grep -o 'name="_csrf"[^>]*value="[^"]*"' | sed 's/.*value="//;s/"$//'; }

# 새 scope(동의 안 된 상태를 만들기 위해 consent DB 를 비운다)
docker exec microservice-as-mysql-1 mysql -uroot -p1111 microservice_as -e "delete from consents;" 2>/dev/null

curl -s -b ms2-cookies.txt -c ms2-cookies.txt "$GW/oauth2/authorize?response_type=code&client_id=my-client&redirect_uri=http://127.0.0.1:8080/callback&scope=openid%20profile&code_challenge=$CHAL&code_challenge_method=S256" > ms2-consent2.html
PENDING=$(grep -o 'name="pending_id"[^>]*value="[^"]*"' ms2-consent2.html | sed 's/.*value="//;s/"$//')
CSRF=$(cat ms2-consent2.html | csrf)

# scope 하나도 체크 안 하고 제출
curl -s -o /dev/null -w "동의 거부: %{redirect_url}\n" -b ms2-cookies.txt -X POST "$GW/oauth2/consent" \
  --data-urlencode "pending_id=$PENDING" --data-urlencode "_csrf=$CSRF"
```
Expected: redirect 에 `error=access_denied` 포함

- [ ] **Step 8: 서버·컨테이너 정리**

```bash
pkill -f "microservice.*SNAPSHOT.jar" 2>/dev/null
for p in 8081 8082 8083 8084 8085 8086; do lsof -tiTCP:$p -sTCP:LISTEN 2>/dev/null | xargs kill -9 2>/dev/null; done
docker compose -p microservice-as -f oauth-2/authorization-server/practice/microservice/docker-compose/docker-compose.yml stop
```

- [ ] **Step 9: README 갱신**

`microservice/README.md` 를 아래 항목으로 갱신한다.
- 제목/개요에 "슬라이스 2: OIDC(id token · userinfo · consent 분리) 포함" 명시
- 구도 ASCII 와 **아키텍처 mermaid 다이어그램에 consent(8086) 추가**, `/userinfo`·`/oauth2/consent` 라우팅 반영
- 서비스 표에 consent 행 추가(8086, 동의 기록 소유, consents 테이블)
- "관통 flow" 에 동의 단계와 id token 발급 단계 추가
- **API 별 시퀀스 다이어그램**에 (a) 동의 흐름(authorize → consent 조회 → pending 저장 → 화면 → 제출 → code), (b) id token 발급이 포함된 token 교환, (c) userinfo 시퀀스를 mermaid 로 추가
- "검증된 성공 기준" 을 이번 e2e 결과로 갱신(동의 화면, id token claim/at_hash/서명, 재인가 동의 생략, userinfo scope 필터링, access_denied, 무효 토큰 401, 회귀)
- "기동 방법" 의 서비스 목록에 consent 추가(기동 순서: signing → user-directory → client-registry → consent → token → auth)
- "알려진 한계" 에 추가: jwks 를 userinfo 요청마다 조회(캐시 없음), auth_time 이 로그인 시각이 아니라 authorize 처리 시각, ForwardedHeaderFilter 미적용(기존)

- [ ] **Step 10: http 파일 작성**

`microservice/http/userinfo.http`:
```
### userinfo — access token 으로 사용자 claim 조회 (scope 에 따라 필터링된다)
GET http://localhost:9000/userinfo
Authorization: Bearer {access_token}

### 무효 토큰 -> 401 invalid_token
GET http://localhost:9000/userinfo
Authorization: Bearer not.a.token

### openid scope 없는 토큰 -> 403 insufficient_scope

### discovery (OIDC) — userinfo_endpoint, scopes_supported, claims_supported 확인
GET http://localhost:9000/.well-known/openid-configuration
```

- [ ] **Step 11: Commit**

```bash
git add oauth-2/authorization-server/practice/microservice/README.md oauth-2/authorization-server/practice/microservice/http
git commit -m "microservice: e2e verification and docs for OIDC slice

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Self-Review 결과

- **Spec coverage**: consent 서비스 신설(Task 2,3) ✓ / auth 동의 화면·pending(Task 4,5,6) ✓ / id token + nonce·auth_time·at_hash·scope claim(Task 7) ✓ / userinfo + scope 필터링 + RFC 6750 에러(Task 8) ✓ / user-directory 프로필 확장(Task 1) ✓ / gateway 라우팅·discovery(Task 9) ✓ / 공유 계약(code 에 nonce·authTime, pending, consent API)(Task 4,5,3) ✓ / 실패 모드 — consent fail-closed(Task 5 ConsentClient 주석·예외 전파), user-directory 다운 시 프로필 없이 발급(Task 7 테스트) ✓ / 검증 7기준(Task 10) ✓ / at_hash 실측 대조(Task 7 Step 1) ✓
- **제외 항목 준수**: refresh·introspection·back-channel logout(sid)·내부 인증·Kafka — 계획에 없음 ✓
- **Type 일관성**: `AuthorizationCodeIssuer.issue(7 args)` 는 Task 5 정의 → Task 6 호출 일치 ✓. `PendingAuthorization` 필드 = pending JSON = Task 6 사용 일치 ✓. `AuthorizationCodeData(nonce, authTime)` = auth 가 쓰는 code JSON 키 이름 일치 ✓. `UserProfile` 필드 = user-directory `UserResponse` 필드명 일치(preferredUsername/emailVerified) ✓. `AccessTokenVerifier.VerifiedToken(sub, scopes)` = Task 8 컨트롤러 사용 일치 ✓. `TokenResponse` 5필드 = Task 7 사용 일치 ✓
- **알려진 진행상 주의**: Task 5 는 컴파일이 깨진 상태로 끝나고 Task 6 에서 해소되어 함께 커밋된다(계획에 명시).
