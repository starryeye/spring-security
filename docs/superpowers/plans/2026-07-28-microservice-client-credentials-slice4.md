# 마이크로서비스 인가 서버 슬라이스 4 — client_credentials + introspect scope Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** client 자체 능력을 사용자 위임 scope 와 스키마에서 분리하고, client_credentials grant 를 추가해 introspection 엔드포인트의 인가를 scope 로 좁힌다.

**Architecture:** `clients` 에 `client_scopes` 컬럼을 더해 grant 별로 다른 컬럼을 보게 한다(authorization_code → `scopes`, client_credentials → `client_scopes`). introspection 은 client 인증 대상에서 **Bearer 토큰을 검사하는 protected resource** 로 바뀌어 `/userinfo` 와 같은 성격이 된다. 새 서비스는 없고 auth 는 건드리지 않는다.

**Tech Stack:** Java 21, Spring Boot 3.4.5, Spring Data JPA, MySQL, nimbus-jose-jwt, nginx

## Global Constraints

- Java 21 (gradle toolchain). 로컬 `java -jar` 는 `/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn/bin/java` 사용 (PATH java 는 17).
- Spring Boot **3.4.5**, io.spring.dependency-management **1.1.7**, gradle wrapper 8.13. 버전 하드코딩 오타 주의(전 서비스 동일해야 함).
- **SAS starter(`spring-boot-starter-oauth2-authorization-server`) 금지.** OAuth/OIDC 로직은 직접 구현.
- gradle 명령은 반드시 `--no-daemon`. `./gradlew` 가 exit 137(SIGKILL)이면 우회: `/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn/bin/java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon --rerun-tasks`
- 패키지 `dev.starryeye.<service_name>`(underscore).
- 포트: gateway 9000, auth 8081, token 8082, signing 8083, user-directory 8084, client-registry 8085, consent 8086, token-state 8087.
- MySQL `jdbc:mysql://localhost:3306/microservice_as` root/1111.
- 테스트에서 mock 빈은 `@MockitoBean` 사용(`@MockBean` 은 Boot 3.4 deprecated).
- cross-service 수신 record 에는 `@JsonIgnoreProperties(ignoreUnknown = true)`.
- DB 저장은 comma 구분, OAuth 와이어 포맷은 공백 구분. 변환은 경계에서 한 번만.
- 주석: 클래스 설명 javadoc 은 **클래스 바디 안**(여는 중괄호 아래). 경험담 서술 금지 — 함정은 "주의." 항목으로 현재형 지식 서술.
- `git add` 는 **경로를 명시**한다. `-A`/`-a`/`.` 금지 — 저장소에 상시 modified 로 두는 파일이 있어 실제 credential 이 올라갈 수 있다.
- 커밋 직후 `git push origin main`. 거부되면 강제 푸시하지 말고 보고한다.
- 커밋 메시지 말미: `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`

## 현재 테스트 수 (시작 시점)

token **96**, token-state **39**, auth **16**, client-registry **2**, signing 4, user-directory 6.

## 공유 계약 (이 슬라이스에서 변경)

```
clients 테이블
  client_scopes  varchar(500) not null, comma 구분   ← 신규

client-registry  GET /internal/clients/{clientId}
  200 { clientId, redirectUris[], scopes[], clientSecretHash, grantTypes[], clientScopes[] }
                                                                            ↑ 신규(맨 뒤)

token  POST /oauth2/token
  grant_type=client_credentials&scope=<선택>        ← 신규 grant
  200 { access_token, token_type, expires_in, scope }   (refresh_token · id_token 없음)

token  POST /oauth2/introspect
  Authorization: Bearer <client_credentials 토큰>   ← Basic 에서 전환
```

## File Structure

**client-registry**(8085)

| 파일 | 책임 |
|---|---|
| `jpa/ClientEntity.java` (수정) | `clientScopes` 컬럼 |
| `dto/ClientResponse.java` (수정) | `clientScopes` 필드 (**맨 뒤에** 추가) |
| `ClientController.java` (수정) | 엔티티 → 응답 변환에 추가 |
| `ClientSeedInitializer.java` (수정) | seed 두 client 갱신 |

**token**(8082)

| 파일 | 책임 |
|---|---|
| `client/ClientInfo.java` (수정) | `clientScopes` 필드 (**맨 뒤에** 추가) |
| `ClientCredentialsGrantService.java` (신규) | client_credentials 의 판정과 조립 |
| `TokenEndpointController.java` (수정) | grant 분기 추가, discovery 갱신 |
| `IntrospectionController.java` (수정) | Bearer 기반 protected resource 로 전환 |

**token-state**(8087) — 이월 항목만

| 파일 | 책임 |
|---|---|
| `TokenGenerator.java` (수정) | catch 범위 축소 |
| `RefreshTokenService.java` (수정) | `expiresAt` 상한, `revokedReason` 가드 |
| `jpa/RefreshTokenEntity.java` (수정) | 이미 REVOKED 면 사유를 덮어쓰지 않음 |

**auth**(8081) — **변경 없음.** auth 의 `ClientInfo` 는 `@JsonIgnoreProperties(ignoreUnknown = true)` 라 새 필드를 무시한다. client 능력 scope 가 auth 의 시야에 들어오지 않는 것이 동의 화면 노출을 막는 구조적 보장이다.

**주의.** 두 record(`ClientResponse` · `ClientInfo`)에 필드를 **맨 뒤에** 추가한다. 중간에 끼우면 기존 위치 기반 생성자 호출에서 `List<String>` 타입 필드끼리(`redirectUris` · `scopes` · `grantTypes`) 순서가 바뀌어도 컴파일이 통과해 조용히 어긋난다.

---

## Task 1: client-registry — client_scopes 컬럼 · 계약 · seed

**Files:**
- Modify: `microservice/client-registry/src/main/java/dev/starryeye/client_registry/jpa/ClientEntity.java`
- Modify: `microservice/client-registry/src/main/java/dev/starryeye/client_registry/dto/ClientResponse.java`
- Modify: `microservice/client-registry/src/main/java/dev/starryeye/client_registry/ClientController.java`
- Modify: `microservice/client-registry/src/main/java/dev/starryeye/client_registry/ClientSeedInitializer.java`
- Test: `microservice/client-registry/src/test/java/dev/starryeye/client_registry/ClientControllerTest.java` (수정)
- Test: `microservice/client-registry/src/test/java/dev/starryeye/client_registry/ClientSeedInitializerTest.java` (신규 — 이월 항목 5)

**Interfaces:**
- Produces: `ClientResponse(clientId, redirectUris, scopes, clientSecretHash, grantTypes, clientScopes)` — `clientScopes` 는 `List<String>`, 맨 뒤
- Produces: `ClientEntity.getClientScopes() → String`(comma 구분), 빌더에 `clientScopes` 파라미터

- [ ] **Step 1: 엔티티에 컬럼 추가**

`ClientEntity.java` 의 `grantTypes` 필드 아래에 추가한다.

```java
	@Column(name = "client_scopes", nullable = false, length = 500)
	private String clientScopes; // comma 구분. 관리자가 client 에게 부여한 능력(사용자 위임 아님)
```

빌더 생성자의 시그니처와 대입도 함께 바꾼다.

```java
	@Builder
	private ClientEntity(String clientId, String clientSecretHash, String redirectUris, String scopes,
			String grantTypes, String clientScopes) {
		this.clientId = clientId;
		this.clientSecretHash = clientSecretHash;
		this.redirectUris = redirectUris;
		this.scopes = scopes;
		this.grantTypes = grantTypes;
		this.clientScopes = clientScopes;
	}
```

클래스 javadoc 에 다음을 추가한다.

```
	 * 주의. scopes 와 client_scopes 는 성격이 다르다. scopes 는 사용자가 동의 화면에서 위임하는 것이고,
	 *      client_scopes 는 관리자가 client 에게 부여한 능력이라 동의 화면에 뜨지 않는다.
	 *      grant 별로 보는 컬럼이 갈린다 — authorization_code 는 scopes, client_credentials 는 client_scopes.
```

- [ ] **Step 2: ClientResponse 에 필드 추가**

`dto/ClientResponse.java`:

```java
package dev.starryeye.client_registry.dto;

import java.util.List;

public record ClientResponse(
		String clientId,
		List<String> redirectUris,
		List<String> scopes,
		String clientSecretHash,
		List<String> grantTypes,
		List<String> clientScopes
) {
}
```

- [ ] **Step 3: 변환에 추가**

`ClientController.ClientLookupService.findByClientId` 의 `new ClientResponse(...)` 마지막 인자로 추가한다.

```java
			return new ClientResponse(
					entity.getClientId(),
					List.of(StringUtils.commaDelimitedListToStringArray(entity.getRedirectUris())),
					List.of(StringUtils.commaDelimitedListToStringArray(entity.getScopes())),
					entity.getClientSecretHash(),
					List.of(StringUtils.commaDelimitedListToStringArray(entity.getGrantTypes())),
					List.of(StringUtils.commaDelimitedListToStringArray(entity.getClientScopes()))
			);
```

- [ ] **Step 4: seed 갱신**

`ClientSeedInitializer.run` 의 두 블록을 아래로 바꾼다.

```java
		if (!repository.existsById("my-client")) {
			repository.save(ClientEntity.builder()
					.clientId("my-client")
					.clientSecretHash(encoder.encode("secret"))
					.redirectUris("http://127.0.0.1:8080/callback")
					.scopes("openid,profile,email,offline_access")
					.grantTypes("authorization_code,refresh_token")
					.clientScopes("")
					.build());
		}

		// resource server 역할. 인가 흐름에 참여하지 않고(redirect_uris · scopes 가 비어 있다)
		// client_credentials 로 자기 토큰만 받아 introspection 을 호출한다.
		if (!repository.existsById("article-api")) {
			repository.save(ClientEntity.builder()
					.clientId("article-api")
					.clientSecretHash(encoder.encode("secret"))
					.redirectUris("")
					.scopes("")
					.grantTypes("client_credentials")
					.clientScopes("introspect")
					.build());
		}
```

- [ ] **Step 5: 기존 컨트롤러 테스트 보강**

`ClientControllerTest` 의 `ClientEntity.builder()` 호출에 `.clientScopes(...)` 를 더하고, 응답에 `clientScopes` 가 실리는지 단언을 **추가**한다. 기존 단언은 바꾸지 않는다. 실제 파일을 읽고 그 관용구에 맞춰라.

`article-api` 를 흉내낸 픽스처(예: `.scopes("")`, `.clientScopes("introspect")`)로 다음을 단언한다.

```java
				.andExpect(jsonPath("$.clientScopes[0]").value("introspect"))
				.andExpect(jsonPath("$.clientScopes.length()").value(1))
				.andExpect(jsonPath("$.scopes.length()").value(0))
```

- [ ] **Step 6: seed 테스트 작성 (이월 항목 5)**

`ClientSeedInitializerTest.java` 신규. `my-client` 가 이미 있어도 `article-api` 가 삽입되는지 — 즉 client 별 독립 삽입인지 고정한다. 과거 코드는 `existsById("my-client")` 로 early return 해서 `article-api` 가 영원히 안 생겼다.

```java
package dev.starryeye.client_registry;

import dev.starryeye.client_registry.jpa.ClientEntity;
import dev.starryeye.client_registry.jpa.ClientEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest
class ClientSeedInitializerTest {

	@Autowired
	private ClientSeedInitializer initializer;

	@Autowired
	private ClientEntityRepository repository;

	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	// my-client 가 이미 있어도 article-api 는 따로 삽입돼야 한다.
	// 예전 코드는 my-client 존재 시 즉시 return 해서 article-api 가 영원히 생기지 않았다.
	@Test
	void seedsEachClientIndependently() {
		repository.save(ClientEntity.builder()
				.clientId("my-client")
				.clientSecretHash("{noop}whatever")
				.redirectUris("")
				.scopes("")
				.grantTypes("")
				.clientScopes("")
				.build());

		initializer.run(mock(ApplicationArguments.class));

		assertThat(repository.existsById("article-api")).isTrue();
	}

	@Test
	void seedsBothClientsOnEmptyDatabase() {
		initializer.run(mock(ApplicationArguments.class));

		assertThat(repository.existsById("my-client")).isTrue();
		assertThat(repository.existsById("article-api")).isTrue();
		assertThat(repository.findById("article-api").orElseThrow().getClientScopes()).isEqualTo("introspect");
		assertThat(repository.findById("article-api").orElseThrow().getGrantTypes()).isEqualTo("client_credentials");
	}
}
```

주의. `client-registry` 의 기존 테스트가 어떤 DB 로 도는지 먼저 확인하라(`src/test/resources` 와 `build.gradle`). h2 가 없으면 이 테스트가 MySQL 을 요구하게 되므로, 기존 테스트와 같은 방식을 그대로 따르고 새 인프라 의존을 만들지 마라. h2 의존이 없으면 `build.gradle` 에 `runtimeOnly 'com.h2database:h2'` 와 테스트 프로필을 추가한다(token-state 의 `src/test/resources/application.yml` 을 참고).

- [ ] **Step 7: 테스트 실행**

Run: `cd client-registry && ./gradlew test --no-daemon --rerun-tasks`
Expected: 기존 2 + 신규 2 = 4 PASS

- [ ] **Step 8: 커밋·푸시**

```bash
git add oauth-2/authorization-server/practice/microservice/client-registry
git commit -m "microservice: split client capability scopes from user-delegated scopes

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
git push origin main
```

---

## Task 2: token — ClientInfo 확장

동작 변경이 없는 계약 확장이다. `ClientInfo` 는 여러 테스트가 위치 기반으로 생성하므로 컴파일이 깨진다 — **시그니처만 맞추고 단언은 하나도 바꾸지 않는다.**

**Files:**
- Modify: `microservice/token/src/main/java/dev/starryeye/token/client/ClientInfo.java`
- Test: `token` 의 `new ClientInfo(...)` 호출부 전부 (컴파일 오류로 찾는다)

**Interfaces:**
- Consumes: client-registry 의 `ClientResponse` 에 `clientScopes` 가 맨 뒤에 있다 (Task 1)
- Produces: `ClientInfo(clientId, redirectUris, scopes, clientSecretHash, grantTypes, clientScopes)`

- [ ] **Step 1: record 확장**

```java
package dev.starryeye.token.client;

import java.util.List;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record ClientInfo(
		String clientId,
		List<String> redirectUris,
		List<String> scopes,
		String clientSecretHash,
		List<String> grantTypes,
		List<String> clientScopes
) {
}
```

- [ ] **Step 2: 컴파일해서 깨지는 곳을 전부 찾는다**

Run: `cd token && ./gradlew compileTestJava --no-daemon`
Expected: FAIL — `new ClientInfo(...)` 호출부마다 "constructor ClientInfo cannot be applied to given types"

- [ ] **Step 3: 호출부에 인자 하나를 뒤에 붙인다**

각 호출에 마지막 인자로 `List.of()` 를 더한다. client 능력이 필요한 테스트가 아직 없으므로 전부 빈 목록이다.

```java
// 예: 기존
new ClientInfo("my-client", List.of("http://127.0.0.1:8080/callback"),
        List.of("openid"), hash, List.of("authorization_code", "refresh_token"))
// 바꾼 뒤
new ClientInfo("my-client", List.of("http://127.0.0.1:8080/callback"),
        List.of("openid"), hash, List.of("authorization_code", "refresh_token"), List.of())
```

**단언은 하나도 바꾸지 마라.** 이 태스크는 시그니처 맞춤이다. 단언을 손대야 할 것 같으면 그건 record 필드를 잘못된 위치에 넣었다는 뜻이니 Step 1 을 다시 확인하라.

- [ ] **Step 4: 전체 테스트**

Run: `cd token && ./gradlew test --no-daemon --rerun-tasks`
Expected: 96 PASS (변화 없음)

- [ ] **Step 5: 커밋·푸시**

```bash
git add oauth-2/authorization-server/practice/microservice/token
git commit -m "microservice: carry client capability scopes into token service contract

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
git push origin main
```

---

## Task 3: token — client_credentials grant

**Files:**
- Create: `microservice/token/src/main/java/dev/starryeye/token/ClientCredentialsGrantService.java`
- Modify: `microservice/token/src/main/java/dev/starryeye/token/TokenEndpointController.java`
- Test: `microservice/token/src/test/java/dev/starryeye/token/ClientCredentialsGrantServiceTest.java` (신규)
- Test: `microservice/token/src/test/java/dev/starryeye/token/TokenEndpointControllerTest.java` (수정)

**Interfaces:**
- Consumes: `AccessTokenIssuer.issue(String sub, String clientId, String scope) → String`, `GrantResult.ok(TokenResponse)` · `GrantResult.failed(String error, String errorDescription)`, `TokenResponse(access_token, token_type, expires_in, scope, id_token, refresh_token)`
- Produces: `ClientCredentialsGrantService.grant(ClientInfo client, String requestedScope) → GrantResult`

- [ ] **Step 1: 실패하는 테스트 작성**

`ClientCredentialsGrantServiceTest.java`:

```java
package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientCredentialsGrantServiceTest {

	private final AccessTokenIssuer accessTokenIssuer = mock(AccessTokenIssuer.class);

	private final ClientCredentialsGrantService service =
			new ClientCredentialsGrantService(accessTokenIssuer, 300L);

	private ClientInfo articleApi() {
		return new ClientInfo("article-api", List.of(), List.of(), "{bcrypt}x",
				List.of("client_credentials"), List.of("introspect"));
	}

	@Test
	void issuesAccessTokenWithRequestedScope() {
		when(accessTokenIssuer.issue("article-api", "article-api", "introspect")).thenReturn("signed-token");

		GrantResult result = service.grant(articleApi(), "introspect");

		assertThat(result.success()).isTrue();
		assertThat(result.response().access_token()).isEqualTo("signed-token");
		assertThat(result.response().scope()).isEqualTo("introspect");
		assertThat(result.response().token_type()).isEqualTo("Bearer");
	}

	// RFC 6749 4.4.3 — refresh token 을 주지 않는다. 사용자가 없으므로 "재로그인 없이 연장" 이라는
	// refresh 의 존재 이유가 성립하지 않고, 자격증명으로 다시 받으면 된다.
	@Test
	void issuesNeitherRefreshTokenNorIdToken() {
		when(accessTokenIssuer.issue(any(), any(), any())).thenReturn("signed-token");

		GrantResult result = service.grant(articleApi(), "introspect");

		assertThat(result.response().refresh_token()).isNull();
		assertThat(result.response().id_token()).isNull();
	}

	// RFC 9068 — 사용자가 없으므로 sub 는 client_id 다.
	@Test
	void subjectIsTheClientItself() {
		when(accessTokenIssuer.issue("article-api", "article-api", "introspect")).thenReturn("signed-token");

		service.grant(articleApi(), "introspect");

		verify(accessTokenIssuer).issue("article-api", "article-api", "introspect");
	}

	// RFC 6749 3.3 — 생략 시 사전 정의된 기본값. authorization_code 경로와 같은 규칙이다.
	@Test
	void omittedScopeDefaultsToAllClientScopes() {
		ClientInfo client = new ClientInfo("article-api", List.of(), List.of(), "{bcrypt}x",
				List.of("client_credentials"), List.of("introspect", "audit"));
		when(accessTokenIssuer.issue(any(), any(), any())).thenReturn("signed-token");

		GrantResult result = service.grant(client, null);

		assertThat(result.response().scope()).isEqualTo("introspect audit");
		verify(accessTokenIssuer).issue("article-api", "article-api", "introspect audit");
	}

	@Test
	void scopeBeyondClientScopesIsRejected() {
		GrantResult result = service.grant(articleApi(), "introspect admin");

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("invalid_scope");
		verify(accessTokenIssuer, never()).issue(any(), any(), any());
	}

	// 사용자 위임 scope 는 이 grant 로 받을 수 없다. openid 는 scopes 컬럼에 있고 clientScopes 에는 없다.
	@Test
	void userDelegatedScopeIsRejected() {
		ClientInfo client = new ClientInfo("my-client", List.of(), List.of("openid", "profile"), "{bcrypt}x",
				List.of("client_credentials"), List.of());

		GrantResult result = service.grant(client, "openid");

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("invalid_scope");
	}

	@Test
	void clientWithNoClientScopesIsRejected() {
		ClientInfo client = new ClientInfo("my-client", List.of(), List.of("openid"), "{bcrypt}x",
				List.of("client_credentials"), List.of());

		GrantResult result = service.grant(client, null);

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("invalid_scope");
		verify(accessTokenIssuer, never()).issue(any(), any(), any());
	}

	@Test
	void clientWithoutTheGrantIsRejected() {
		ClientInfo client = new ClientInfo("my-client", List.of(), List.of("openid"), "{bcrypt}x",
				List.of("authorization_code"), List.of("introspect"));

		GrantResult result = service.grant(client, "introspect");

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("unauthorized_client");
		verify(accessTokenIssuer, never()).issue(any(), any(), any());
	}
}
```

- [ ] **Step 2: RED 확인**

Run: `cd token && ./gradlew test --tests ClientCredentialsGrantServiceTest --no-daemon`
Expected: FAIL — `ClientCredentialsGrantService` 없음

- [ ] **Step 3: 구현**

`ClientCredentialsGrantService.java`:

```java
package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.dto.TokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Service
public class ClientCredentialsGrantService {

	/**
	 * client_credentials grant 를 처리한다. (RFC 6749 4.4)
	 *      사용자가 없는 grant 라 client 가 자기 자신으로서 토큰을 받는다.
	 *
	 * 주의. 이 grant 는 client_scopes 만 본다. scopes(사용자 위임) 는 쳐다보지 않는다 — 사용자가 없는데
	 *      "사용자가 위임한 권한" 을 줄 수는 없다. 그래서 openid 같은 scope 는 이 경로로 나올 수 없고,
	 *      그 결과 이 토큰으로 /userinfo 를 부르면 403 insufficient_scope 가 된다. 별도 방어 코드가 필요 없다.
	 *
	 * 주의. refresh token 을 발급하지 않는다(RFC 6749 4.4.3 이 SHOULD NOT). 사용자가 없으므로
	 *      "재로그인 없이 연장" 이라는 refresh 의 존재 이유가 없고, 필요하면 자격증명으로 다시 받으면 된다.
	 *      id token 도 없다 — 인증한 사용자가 없다.
	 *
	 * 주의. sub 는 client_id 다(RFC 9068). aud 도 client_id 가 되어 sub == aud 인데, 이 서버가
	 *      resource indicator(RFC 8707)를 쓰지 않아 발급 대상 자원을 표현할 방법이 없기 때문이다.
	 */

	private final AccessTokenIssuer accessTokenIssuer;
	private final long accessTokenTtlSeconds;

	public ClientCredentialsGrantService(
			AccessTokenIssuer accessTokenIssuer,
			@Value("${my.access-token-ttl-seconds}") long accessTokenTtlSeconds
	) {
		this.accessTokenIssuer = accessTokenIssuer;
		this.accessTokenTtlSeconds = accessTokenTtlSeconds;
	}

	public GrantResult grant(ClientInfo client, String requestedScope) {

		if (!client.grantTypes().contains("client_credentials")) {
			return GrantResult.failed("unauthorized_client", "client not authorized for client_credentials grant");
		}

		List<String> allowed = client.clientScopes();
		String effectiveScope = StringUtils.hasText(requestedScope)
				? String.join(" ", requestedScope.trim().split("\\s+"))
				: String.join(" ", allowed);

		if (!StringUtils.hasText(effectiveScope)) {
			return GrantResult.failed("invalid_scope", "client has no client scopes");
		}
		if (!allowed.containsAll(Arrays.asList(effectiveScope.split(" ")))) {
			return GrantResult.failed("invalid_scope", "requested scope exceeds the client scopes");
		}

		String accessToken = accessTokenIssuer.issue(client.clientId(), client.clientId(), effectiveScope);

		return GrantResult.ok(new TokenResponse(accessToken, "Bearer", accessTokenTtlSeconds,
				effectiveScope, null, null));
	}
}
```

- [ ] **Step 4: GREEN 확인**

Run: `cd token && ./gradlew test --tests ClientCredentialsGrantServiceTest --no-daemon --rerun-tasks`
Expected: 8 PASS

- [ ] **Step 5: 컨트롤러에 분기 추가**

`TokenEndpointController` 의 grant type 검사를 셋을 받도록 바꾼다.

```java
		if (!"authorization_code".equals(grantType) && !"refresh_token".equals(grantType)
				&& !"client_credentials".equals(grantType)) {
			return error(HttpStatus.BAD_REQUEST, "unsupported_grant_type",
					"only authorization_code, refresh_token and client_credentials are supported");
		}
```

`refresh_token` 분기 바로 아래에 추가한다.

```java
		if ("client_credentials".equals(grantType)) {
			GrantResult result = clientCredentialsGrantService.grant(client, scopeParam);
			if (!result.success()) {
				return error(HttpStatus.BAD_REQUEST, result.error(), result.errorDescription());
			}
			return ResponseEntity.ok(result.response());
		}
```

필드에 `private final ClientCredentialsGrantService clientCredentialsGrantService;` 를 추가한다.

- [ ] **Step 6: 컨트롤러 테스트 보강**

`TokenEndpointControllerTest` 에 `@MockitoBean ClientCredentialsGrantService clientCredentialsGrantService;` 를 추가하고 아래 두 테스트를 넣는다.

```java
	@Test
	void clientCredentialsGrantDelegatesToItsService() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(clientCredentialsGrantService.grant(any(), eq("introspect")))
				.thenReturn(GrantResult.ok(new TokenResponse("cc-token", "Bearer", 300L,
						"introspect", null, null)));

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "client_credentials")
						.param("scope", "introspect"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.access_token").value("cc-token"))
				.andExpect(jsonPath("$.refresh_token").doesNotExist())
				.andExpect(jsonPath("$.id_token").doesNotExist());
	}

	@Test
	void clientCredentialsFailureBecomesOAuth2Error() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(clientCredentialsGrantService.grant(any(), any()))
				.thenReturn(GrantResult.failed("unauthorized_client", "client not authorized for client_credentials grant"));

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "client_credentials"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("unauthorized_client"));
	}
```

- [ ] **Step 7: 전체 테스트**

Run: `cd token && ./gradlew test --no-daemon --rerun-tasks`
Expected: 96 + 8 + 2 = 106 PASS

- [ ] **Step 8: 커밋·푸시**

```bash
git add oauth-2/authorization-server/practice/microservice/token
git commit -m "microservice: add client credentials grant

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
git push origin main
```

---

## Task 4: token — introspection 을 Bearer 기반 protected resource 로

**Files:**
- Modify: `microservice/token/src/main/java/dev/starryeye/token/IntrospectionController.java`
- Test: `microservice/token/src/test/java/dev/starryeye/token/IntrospectionControllerTest.java` (전면 수정)

**Interfaces:**
- Consumes: `AccessTokenVerifier.verify(String) → VerifiedToken(sub, scopes, clientId, exp, iat)` (throws `AccessTokenVerifier.InvalidTokenException`), `TokenStateClient.introspect(String) → RefreshTokenInfo(active, sub, clientId, scope, exp, iat)`
- Produces: `POST /oauth2/introspect` — Bearer 인증

**이 태스크가 바꾸는 것:** 지금은 `ClientAuthenticator` 로 Basic 인증만 하고 인가가 없다. 이제 호출자의 **access token 을 검증하고 `introspect` scope 를 확인**한다. `ClientAuthenticator` 의존을 제거한다(다른 소비자 — `/oauth2/token`, `/oauth2/revoke` — 는 그대로 쓴다).

- [ ] **Step 1: 테스트를 새 계약으로 다시 쓴다**

`IntrospectionControllerTest` 를 아래로 교체한다. 기존 파일의 "검사 대상 토큰" 관련 단언(활성 access token 응답 형태, refresh 폴백, 비활성 단일 키, jwks 장애 500, token 파라미터 누락 400)은 **의미를 그대로 유지**하고 호출자 인증 부분만 Bearer 로 바꾼 것이다.

```java
package dev.starryeye.token;

import dev.starryeye.token.client.ClientRegistryClient;
import dev.starryeye.token.client.RefreshTokenInfo;
import dev.starryeye.token.client.SigningClient;
import dev.starryeye.token.client.TokenStateClient;
import dev.starryeye.token.client.UserDirectoryClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IntrospectionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AccessTokenVerifier accessTokenVerifier;

	@MockitoBean
	private TokenStateClient tokenStateClient;

	@MockitoBean
	private ClientRegistryClient clientRegistryClient;

	@MockitoBean
	private SigningClient signingClient;

	@MockitoBean
	private UserDirectoryClient userDirectoryClient;

	@MockitoBean
	private AuthorizationCodeStore codeStore;

	private static final String CALLER = "Bearer caller-token";

	private void callerHasIntrospectScope() {
		when(accessTokenVerifier.verify("caller-token")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("article-api", List.of("introspect"),
						"article-api", 1800000000L, 1700000000L));
	}

	// 호출자와 토큰 주인이 다른 것이 introspection 의 정상 상황이다.
	@Test
	void callerWithIntrospectScopeCanInspectAnotherClientsAccessToken() throws Exception {
		callerHasIntrospectScope();
		when(accessTokenVerifier.verify("subject-token")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("user-sub-0001", List.of("openid", "profile"),
						"my-client", 1800000000L, 1700000000L));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", CALLER)
						.param("token", "subject-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.sub").value("user-sub-0001"))
				.andExpect(jsonPath("$.client_id").value("my-client"))
				.andExpect(jsonPath("$.scope").value("openid profile"))
				.andExpect(jsonPath("$.token_type").value("Bearer"));

		verify(tokenStateClient, never()).introspect(any());
	}

	// scope 가 없으면 인증은 됐지만 권한이 없는 것이라 403 이다 (RFC 6750 3.1)
	@Test
	void callerWithoutIntrospectScopeIsForbidden() throws Exception {
		when(accessTokenVerifier.verify("caller-token")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("my-client", List.of("openid"),
						"my-client", 1800000000L, 1700000000L));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", CALLER)
						.param("token", "subject-token"))
				.andExpect(status().isForbidden())
				.andExpect(header().string("WWW-Authenticate", "Bearer error=\"insufficient_scope\""));

		verify(tokenStateClient, never()).introspect(any());
	}

	// Basic 은 더 이상 받지 않는다. 계속 받으면 scope 로 좁힌 의미가 사라진다.
	@Test
	void basicCredentialsAreNoLongerAccepted() throws Exception {
		String basic = "Basic " + Base64.getEncoder()
				.encodeToString("article-api:secret".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", basic)
						.param("token", "subject-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"));

		verify(accessTokenVerifier, never()).verify(any());
		verify(tokenStateClient, never()).introspect(any());
	}

	@Test
	void missingAuthorizationHeaderIsUnauthorized() throws Exception {
		mockMvc.perform(post("/oauth2/introspect").param("token", "subject-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"));

		verify(tokenStateClient, never()).introspect(any());
	}

	@Test
	void invalidCallerTokenIsUnauthorized() throws Exception {
		when(accessTokenVerifier.verify("caller-token"))
				.thenThrow(new AccessTokenVerifier.InvalidTokenException("token expired"));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", CALLER)
						.param("token", "subject-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""));

		verify(tokenStateClient, never()).introspect(any());
	}

	// 호출자 토큰 검증 중 jwks 를 못 구한 것은 토큰의 죄가 아니라 서버 장애다.
	@Test
	void signingFailureWhileVerifyingCallerReturns500() throws Exception {
		when(accessTokenVerifier.verify("caller-token"))
				.thenThrow(new IllegalStateException("signing is down"));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", CALLER)
						.param("token", "subject-token"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("server_error"));

		verify(tokenStateClient, never()).introspect(any());
	}

	@Test
	void missingTokenParameterIsInvalidRequest() throws Exception {
		callerHasIntrospectScope();

		mockMvc.perform(post("/oauth2/introspect").header("Authorization", CALLER))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_request"));
	}

	// JWT 가 아니면 refresh token 일 수 있으므로 소유자에게 묻는다. token_type_hint 는 쓰지 않는다.
	@Test
	void nonJwtSubjectTokenFallsBackToTokenState() throws Exception {
		callerHasIntrospectScope();
		when(accessTokenVerifier.verify("opaque-refresh"))
				.thenThrow(new AccessTokenVerifier.InvalidTokenException("malformed token"));
		when(tokenStateClient.introspect("opaque-refresh")).thenReturn(
				new RefreshTokenInfo(true, "user-sub-0001", "my-client", "openid offline_access",
						1800000000L, 1700000000L));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", CALLER)
						.param("token", "opaque-refresh"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.scope").value("openid offline_access"))
				.andExpect(jsonPath("$.token_type").doesNotExist());
	}

	// 비활성 응답에서는 어떤 정보도 새지 않아야 한다 (RFC 7662 2.2)
	@Test
	void inactiveResponseContainsOnlyActiveFalse() throws Exception {
		callerHasIntrospectScope();
		when(accessTokenVerifier.verify("dead"))
				.thenThrow(new AccessTokenVerifier.InvalidTokenException("token expired"));
		when(tokenStateClient.introspect("dead"))
				.thenReturn(new RefreshTokenInfo(false, null, null, null, 0L, 0L));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", CALLER)
						.param("token", "dead"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false))
				.andExpect(jsonPath("$.sub").doesNotExist())
				.andExpect(jsonPath("$.client_id").doesNotExist())
				.andExpect(jsonPath("$.scope").doesNotExist())
				.andExpect(jsonPath("$.exp").doesNotExist());
	}

	// token-state 가 빈 본문을 주면 "비활성" 이 아니라 "확인하지 못했다" 이므로 500 이다.
	@Test
	void nullIntrospectionResultReturns500NotInactive() throws Exception {
		callerHasIntrospectScope();
		when(accessTokenVerifier.verify("opaque"))
				.thenThrow(new AccessTokenVerifier.InvalidTokenException("malformed token"));
		when(tokenStateClient.introspect("opaque")).thenReturn(null);

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", CALLER)
						.param("token", "opaque"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("server_error"));
	}
}
```

- [ ] **Step 2: RED 확인**

Run: `cd token && ./gradlew test --tests IntrospectionControllerTest --no-daemon`
Expected: FAIL — Basic 을 받는 현재 구현이라 Bearer 케이스가 401 `invalid_client` 로 떨어진다

- [ ] **Step 3: 컨트롤러 재작성**

`IntrospectionController.java` — 필드에서 `clientAuthenticator` 를 빼고, 인증 블록을 아래로 바꾼다. `activeAccessToken` · `activeRefreshToken` 두 private 메서드와 폴백 로직은 **그대로 둔다**.

```java
	private static final String REQUIRED_SCOPE = "introspect";

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
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").build();
		}

		AccessTokenVerifier.VerifiedToken caller;
		try {
			caller = accessTokenVerifier.verify(authorization.substring(7));
		} catch (AccessTokenVerifier.InvalidTokenException e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"").build();
		}
		if (!caller.scopes().contains(REQUIRED_SCOPE)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"").build();
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
			if (info == null) {
				throw new IllegalStateException("token-state returned an empty introspection response");
			}
			if (!info.active()) {
				return ResponseEntity.ok(Map.of("active", false));
			}
			return ResponseEntity.ok(activeRefreshToken(info));
		}
	}
```

클래스 javadoc 의 마지막 "주의." 항목(인증된 등록 client 면 누구의 토큰이든 조회할 수 있다 …)을 아래로 교체한다.

```
	 * 주의. 이 엔드포인트는 client 인증 대상이 아니라 protected resource 다. 호출자는 client_credentials 로
	 *      받은 access token 을 Bearer 로 제시하고, 그 토큰에 introspect scope 가 있어야 한다.
	 *      그래서 오류도 RFC 6749(client 인증)가 아니라 RFC 6750(Bearer)을 따른다 —
	 *      토큰 없음/Basic 은 401, 무효 토큰은 401 invalid_token, scope 부족은 403 insufficient_scope 다.
	 *
	 * 주의. Basic 을 계속 받으면 scope 로 좁힌 의미가 사라진다. 그래서 Basic 은 토큰 없음과 같이 401 이다.
	 *
	 * 주의. revoke 는 여전히 Basic 을 쓴다. 비대칭이 의도적이다 — revoke 는 "자기 토큰을 폐기" 라 소유자
	 *      확인(client 인증)이 맞고, introspect 는 "남의 토큰을 검사" 라 별도로 부여된 능력(scope)이 맞다.
	 *
	 * 주의. 호출자 토큰 검증도 검사 대상 토큰 검증도 같은 AccessTokenVerifier 를 쓴다. jwks 확보 실패는
	 *      InvalidTokenException 이 아닌 예외로 전파돼 500 server_error 가 된다 — 401 로 응답하면 호출자가
	 *      멀쩡한 자기 토큰을 폐기하고 재발급을 돌린다.
```

쓰이지 않게 된 import(`ClientAuthenticator` 관련)를 정리한다. `OAuth2ErrorResponse` 는 `invalid_request` 에서 계속 쓰므로 남긴다.

- [ ] **Step 4: GREEN 확인**

Run: `cd token && ./gradlew test --tests IntrospectionControllerTest --no-daemon --rerun-tasks`
Expected: 10 PASS

- [ ] **Step 5: 전체 테스트**

Run: `cd token && ./gradlew test --no-daemon --rerun-tasks`
Expected: 106 - (기존 IntrospectionControllerTest 7) + 10 = 109 PASS

기존 7개가 10개로 바뀌므로 총계가 달라진다. 실제 수가 다르면 무엇이 늘고 줄었는지 보고에 적어라.

- [ ] **Step 6: 커밋·푸시**

```bash
git add oauth-2/authorization-server/practice/microservice/token
git commit -m "microservice: authorize introspection by scope instead of client authentication

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
git push origin main
```

---

## Task 5: token — discovery 갱신

**Files:**
- Modify: `microservice/token/src/main/java/dev/starryeye/token/TokenEndpointController.java` (`metadata()`)
- Test: `microservice/token/src/test/java/dev/starryeye/token/TokenEndpointControllerTest.java` (discovery 단언)

**Interfaces:**
- Produces: `/.well-known/openid-configuration` 과 `/.well-known/oauth-authorization-server` (같은 메서드가 서빙)

- [ ] **Step 1: metadata 갱신**

`metadata()` 에서 세 곳을 바꾼다.

```java
		metadata.put("grant_types_supported",
				List.of("authorization_code", "refresh_token", "client_credentials"));
		metadata.put("scopes_supported",
				List.of("openid", "profile", "email", "offline_access", "introspect"));
```

그리고 `introspection_endpoint_auth_methods_supported` 를 넣는 줄을 **삭제**한다. `revocation_endpoint_auth_methods_supported` 는 그대로 둔다.

메서드 javadoc(또는 클래스 javadoc)에 "주의." 항목을 추가한다.

```
	 * 주의. introspection_endpoint_auth_methods_supported 를 내보내지 않는다. RFC 8414 의 그 필드는
	 *      client 인증 방식(client_secret_basic 등)을 담는데, 이 엔드포인트는 Bearer 토큰과 introspect scope 를
	 *      요구하므로 담을 값이 없다. "none" 은 인증이 필요 없다는 거짓이 된다.
	 *      discovery 에는 "Bearer 토큰 + 특정 scope" 를 표현할 표준 필드가 없다.
	 *
	 * 주의. scopes_supported 에 introspect 가 들어가지만, discovery 에는 그것이 사용자 위임 가능한지
	 *      client 자체 능력인지 구분할 필드가 없다. client 가 authorization_code 로 요청할 수 있다고
	 *      오해할 여지가 남는다.
```

- [ ] **Step 2: discovery 단언 갱신 (이월 항목 4 포함)**

`TokenEndpointControllerTest.openidConfigurationAdvertisesImplementedCapabilities` 를 고친다.

- `introspection_endpoint_auth_methods_supported` 를 **부재로 뒤집는다.** 줄을 지우지 마라 — 지우면 그 필드를 아무도 안 보게 되어 나중에 되살아나도 잡히지 않는다

```java
				.andExpect(jsonPath("$.introspection_endpoint_auth_methods_supported").doesNotExist())
```

- `grant_types_supported` 와 `scopes_supported` 의 원소·길이 단언을 새 값으로 바꾼다

```java
				.andExpect(jsonPath("$.grant_types_supported[2]").value("client_credentials"))
				.andExpect(jsonPath("$.grant_types_supported.length()").value(3))
				.andExpect(jsonPath("$.scopes_supported[4]").value("introspect"))
				.andExpect(jsonPath("$.scopes_supported.length()").value(5))
```

- **이월 항목 4**: 남아 있는 단일 원소 배열에 `length()` 단언을 더한다.

```java
				.andExpect(jsonPath("$.response_types_supported.length()").value(1))
				.andExpect(jsonPath("$.code_challenge_methods_supported.length()").value(1))
				.andExpect(jsonPath("$.subject_types_supported.length()").value(1))
				.andExpect(jsonPath("$.id_token_signing_alg_values_supported.length()").value(1))
				.andExpect(jsonPath("$.revocation_endpoint_auth_methods_supported.length()").value(1))
```

- [ ] **Step 3: 전체 테스트**

Run: `cd token && ./gradlew test --no-daemon --rerun-tasks`
Expected: 109 PASS (테스트 수 불변, 단언만 증가)

- [ ] **Step 4: 커밋·푸시**

```bash
git add oauth-2/authorization-server/practice/microservice/token
git commit -m "microservice: advertise client credentials and drop misleading introspection auth methods

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
git push origin main
```

---

## Task 6: token-state — 이월 항목 3건

슬라이스 3 리뷰가 남긴 것들이다. 서로 독립이고 전부 token-state 안에 있다.

**Files:**
- Modify: `microservice/token-state/src/main/java/dev/starryeye/token_state/TokenGenerator.java`
- Modify: `microservice/token-state/src/main/java/dev/starryeye/token_state/RefreshTokenService.java`
- Modify: `microservice/token-state/src/main/java/dev/starryeye/token_state/jpa/RefreshTokenEntity.java`
- Test: `microservice/token-state/src/test/java/dev/starryeye/token_state/TokenGeneratorTest.java`
- Test: `microservice/token-state/src/test/java/dev/starryeye/token_state/RefreshTokenServiceRotateTest.java`
- Test: `microservice/token-state/src/test/java/dev/starryeye/token_state/RefreshTokenServiceRevokeTest.java`

**Interfaces:**
- Consumes: `RefreshTokenEntity.revoke(Instant at, String reason)`, `RefreshTokenStatus.{ACTIVE,CONSUMED,REVOKED}`
- Produces: 동작 변경 3건 — 아래 각 Step 참조

- [ ] **Step 1: 이월 1 — TokenGenerator 의 catch 범위 축소**

현재 `catch (Exception e)` 가 null 입력의 NPE 까지 삼켜 `IllegalStateException("SHA-256 unavailable")` 로 오도한다. 운영에서 이 알람이 뜨면 JCE 문제로 오진한다.

`hash` 메서드의 catch 를 `NoSuchAlgorithmException` 으로 좁힌다.

```java
	public String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
```

`import java.security.NoSuchAlgorithmException;` 를 추가한다.

`TokenGeneratorTest` 에 테스트를 하나 더한다.

```java
	// null 은 SHA-256 이 없다는 뜻이 아니다. 넓은 catch 가 NPE 를 삼켜 원인을 오도하지 않는지 고정한다.
	@Test
	void nullInputSurfacesAsNullPointerNotAlgorithmFailure() {
		assertThatThrownBy(() -> generator.hash(null))
				.isInstanceOf(NullPointerException.class);
	}
```

`import static org.assertj.core.api.Assertions.assertThatThrownBy;` 를 추가한다.

- [ ] **Step 2: 이월 2 — 회전 새 행의 expiresAt 에 계열 상한을 적용**

지금은 회전이 만드는 새 행의 `expiresAt` 이 `familyExpiresAt` 을 넘을 수 있고, 그 값이 `RotateResult.expiresAt` 으로 나간다. 판정은 `isExpired` 가 두 항을 다 보므로 정확하지만, 호출자에게는 실제보다 긴 수명을 알려주는 셈이다.

`RefreshTokenService.rotate` 의 새 행 생성에서 `expiresAt` 을 아래로 바꾼다.

```java
		Instant rotatedExpiresAt = now.plusSeconds(ttlSeconds);
		if (rotatedExpiresAt.isAfter(entity.getFamilyExpiresAt())) {
			rotatedExpiresAt = entity.getFamilyExpiresAt(); // 계열 상한을 넘는 수명을 알리지 않는다
		}
```

그리고 빌더의 `.expiresAt(now.plusSeconds(ttlSeconds))` 를 `.expiresAt(rotatedExpiresAt)` 으로 바꾼다.

`RefreshTokenServiceRotateTest` 에 테스트를 더한다. 테스트 설정은 ttl 60초 / family 300초다.

```java
	// 계열 상한이 개별 TTL 보다 가까우면 새 행의 수명은 상한에서 끊긴다.
	// 그러지 않으면 응답의 expiresAt 이 실제보다 긴 수명을 알린다.
	@Test
	void rotatedTokenExpiryNeverExceedsFamilyCeiling() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);
		RefreshTokenEntity entity = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken())).orElseThrow();
		Instant nearCeiling = Instant.now().plusSeconds(5);
		repository.save(withFamilyExpiresAt(entity, nearCeiling));

		RotateResult result = service.rotate(issued.refreshToken(), "my-client", null);

		assertThat(result.status()).isEqualTo(RotateStatus.ROTATED);
		RefreshTokenEntity rotated = repository.findByTokenHash(tokenGenerator.hash(result.refreshToken())).orElseThrow();
		assertThat(rotated.getExpiresAt()).isEqualTo(rotated.getFamilyExpiresAt());
		assertThat(result.expiresAt()).isEqualTo(nearCeiling.getEpochSecond());
	}
```

`withFamilyExpiresAt` 헬퍼는 기존 `rotateAfterFamilyAbsoluteExpiryReturnsExpired` 가 쓰는 헬퍼와 같은 방식으로 만든다 — 실제 파일을 읽고 그 관용구를 재사용하라. 새 헬퍼를 발명하지 마라.

- [ ] **Step 3: 이월 3 — revoke 가 기존 폐기 사유를 덮어쓰지 않게**

`REUSE_DETECTED`(탈취 탐지) 뒤에 `CLIENT_REVOKED`(로그아웃)가 오면 지금은 사유가 덮어써져 탈취 흔적이 사라진다. 인가 판정에는 영향이 없지만 감사 신호를 잃는다.

`RefreshTokenEntity.revoke` 에 가드를 넣는다.

```java
	/**
	 * 이미 REVOKED 면 아무것도 바꾸지 않는다. 최초 폐기 사유가 감사 기록이므로,
	 *      나중에 온 폐기가 REUSE_DETECTED 를 CLIENT_REVOKED 로 덮어쓰면 탈취 탐지 흔적이 사라진다.
	 */
	public void revoke(Instant at, String reason) {
		if (this.status == RefreshTokenStatus.REVOKED) {
			return;
		}
		this.status = RefreshTokenStatus.REVOKED;
		this.revokedAt = at;
		this.revokedReason = reason;
	}
```

`RefreshTokenServiceRevokeTest` 에 테스트를 더한다.

```java
	// 재사용 탐지로 폐기된 계열에 client 폐기가 뒤따라도 최초 사유가 남아야 한다.
	@Test
	void revokeDoesNotOverwriteReuseDetectedReason() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);
		RotateResult rotated = service.rotate(issued.refreshToken(), "my-client", null);
		service.rotate(issued.refreshToken(), "my-client", null); // 재사용 -> 계열 REUSE_DETECTED

		service.revoke(rotated.refreshToken(), "my-client");

		String familyId = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken()))
				.orElseThrow().getFamilyId();
		assertThat(repository.findByFamilyId(familyId))
				.allMatch(e -> "REUSE_DETECTED".equals(e.getRevokedReason()));
	}
```

주의. `revoke` 는 이미 폐기된 계열에 대해서도 `true` 를 돌려준다(RFC 7009 상 항상 200 이므로 호출자에게 차이가 없다). 이 테스트는 **사유가 보존되는지**만 본다.

- [ ] **Step 4: 전체 테스트**

Run: `cd token-state && ./gradlew test --no-daemon --rerun-tasks`
Expected: 39 + 3 = 42 PASS (MySQL 이 없으면 1 skip 포함)

- [ ] **Step 5: 의도적 확인**

세 수정이 실제로 통제를 만드는지 하나씩 되돌려 확인한다.

1. `catch (NoSuchAlgorithmException)` 를 `catch (Exception)` 으로 → `nullInputSurfacesAsNullPointerNotAlgorithmFailure` 실패
2. `rotatedExpiresAt` 상한 적용을 제거 → `rotatedTokenExpiryNeverExceedsFamilyCeiling` 실패
3. `revoke` 의 가드를 제거 → `revokeDoesNotOverwriteReuseDetectedReason` 실패

각각 확인 후 **반드시 원복**하고 `git diff` 로 잔여 변경이 없음을 증명하라. 실패하지 않는 것이 있으면 그 테스트는 해당 수정을 잡지 못하는 것이므로 보고하라.

- [ ] **Step 6: 커밋·푸시**

```bash
git add oauth-2/authorization-server/practice/microservice/token-state
git commit -m "microservice: narrow hash failure, cap rotated expiry, preserve first revocation reason

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
git push origin main
```

---

## Task 7: 관통 e2e 검증 + README + http 파일

**Files:**
- Modify: `microservice/README.md`
- Modify: `microservice/http/token-lifecycle.http`
- Create: `microservice/http/client-credentials.http`

**Interfaces:**
- Consumes: 8개 서비스 전부 + 인프라

- [ ] **Step 1: 전체 빌드**

```bash
cd oauth-2/authorization-server/practice/microservice
for s in signing user-directory client-registry consent token-state token auth; do (cd $s && ./gradlew bootJar --no-daemon -q); done
ls */build/libs/*.jar
```
Expected: 7개 jar

- [ ] **Step 2: 인프라 + 서비스 기동**

```bash
cd oauth-2/authorization-server/practice/microservice
JAVA=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn/bin/java
docker compose -p microservice-as -f docker-compose/docker-compose.yml up -d
for i in $(seq 1 40); do docker exec microservice-as-mysql-1 mysqladmin ping -uroot -p1111 --silent 2>/dev/null && break; sleep 2; done
for s in signing user-directory client-registry consent token-state token auth; do
  nohup $JAVA -jar $s/build/libs/$s-0.0.1-SNAPSHOT.jar > /tmp/ms7-$s.log 2>&1 &
  for i in $(seq 1 40); do grep -q "Started .*Application" /tmp/ms7-$s.log 2>/dev/null && break; sleep 2; done
  echo "$s: $(grep -c 'Started .*Application' /tmp/ms7-$s.log)"
done
```
Expected: 각 서비스 1

주의. 포트가 점유돼 있으면 `lsof -tiTCP:<port> -sTCP:LISTEN | xargs kill -9` 로 정리 후 기동한다.

- [ ] **Step 3: 스키마·seed 확인 및 보정**

`ddl-auto: update` 는 **기존 행이 있는 테이블에 not null 컬럼을 더할 때 실패하거나 빈 문자열로 채울 수 있다.** client-registry 로그를 먼저 확인하라.

```bash
grep -iE "error|exception|alter" /tmp/ms7-client-registry.log | head -20
docker exec microservice-as-mysql-1 mysql -uroot -p1111 microservice_as \
  -e "describe clients; select client_id, scopes, grant_types, client_scopes from clients;"
```

기대: `client_scopes` 컬럼이 있고, `my-client` 는 `''`, `article-api` 는 `introspect` / `grant_types` 는 `client_credentials`.

컬럼 추가가 실패했으면 수동으로 넣고 재기동한다.

```bash
docker exec microservice-as-mysql-1 mysql -uroot -p1111 microservice_as \
  -e "alter table clients add column client_scopes varchar(500) not null default '';"
```

기존 행의 값이 seed 와 다르면(슬라이스 3에서 겪은 함정) 보정하고 client-registry 를 재기동해 Caffeine 캐시(30s)를 비운다.

```bash
docker exec microservice-as-mysql-1 mysql -uroot -p1111 microservice_as -e \
  "update clients set client_scopes='introspect', grant_types='client_credentials' where client_id='article-api'; \
   update clients set client_scopes='' where client_id='my-client';"
```

- [ ] **Step 4: client_credentials 토큰 획득 (성공 기준 1)**

```bash
cd /tmp
GW=http://localhost:9000
curl -s -u article-api:secret -X POST $GW/oauth2/token \
  -d "grant_type=client_credentials&scope=introspect" > ms7-cc.json
python3 - <<'EOF'
import json
d = json.load(open('ms7-cc.json'))
print('scope:', d.get('scope'))
print('refresh_token 부재:', 'refresh_token' not in d)
print('id_token 부재:', 'id_token' not in d)
open('ms7-cc-token.txt','w').write(d['access_token'])
import base64
def b64d(s): return base64.urlsafe_b64decode(s + '='*(-len(s)%4))
c = json.loads(b64d(d['access_token'].split('.')[1]))
print('sub:', c.get('sub'), '| aud:', c.get('aud'), '| scope claim:', c.get('scope'))
EOF
```
Expected: `scope: introspect`, 두 부재 모두 `True`, `sub` 와 `aud` 가 모두 `article-api`

- [ ] **Step 5: introspection — Bearer 로 남의 토큰 조회 (성공 기준 2, 3)**

먼저 `my-client` 의 access token 을 하나 만든다. 절차는 `docs/superpowers/plans/2026-07-25-microservice-token-lifecycle-slice3.md` Task 12 의 로그인·동의·code 교환 스니펫을 재사용한다(이미 동작이 검증된 것이다).

```bash
cd /tmp
GW=http://localhost:9000
CC=$(cat ms7-cc-token.txt)
AT=$(python3 -c "import json; print(json.load(open('ms7-token.json'))['access_token'])")

echo "=== article-api 가 my-client 의 토큰 조회 ==="
curl -s -H "Authorization: Bearer $CC" -X POST $GW/oauth2/introspect -d "token=$AT" | python3 -m json.tool

echo "=== Basic 은 더 이상 안 받는다 ==="
curl -s -o /dev/null -D - -u article-api:secret -X POST $GW/oauth2/introspect -d "token=$AT" \
  | grep -iE "^HTTP|www-authenticate"

echo "=== introspect scope 없는 토큰으로 호출 -> 403 ==="
curl -s -o /dev/null -D - -H "Authorization: Bearer $AT" -X POST $GW/oauth2/introspect -d "token=$AT" \
  | grep -iE "^HTTP|www-authenticate"
```
Expected: 첫 번째는 `active: true` + `client_id: my-client`, 두 번째는 `401` + `WWW-Authenticate: Bearer`, 세 번째는 `403` + `insufficient_scope`

- [ ] **Step 6: 관문 확인 (성공 기준 4, 5)**

```bash
cd /tmp
GW=http://localhost:9000
echo "=== my-client 가 client_credentials 시도 -> unauthorized_client ==="
curl -s -u my-client:secret -X POST $GW/oauth2/token -d "grant_type=client_credentials" | head -c 150; echo

echo "=== my-client 가 authorization_code 로 introspect 요청 -> invalid_scope ==="
CHAL=$(cat ms7-challenge.txt)
curl -s -o /dev/null -w "%{redirect_url}\n" -b ms7-cookies.txt \
  "$GW/oauth2/authorize?response_type=code&client_id=my-client&redirect_uri=http://127.0.0.1:8080/callback&scope=openid%20introspect&code_challenge=$CHAL&code_challenge_method=S256"
```
Expected: 첫 번째 `unauthorized_client`, 두 번째 redirect 에 `error=invalid_scope`

- [ ] **Step 7: 동의 화면 확인 (성공 기준 7)**

```bash
cd /tmp
GW=http://localhost:9000
CHAL=$(cat ms7-challenge.txt)
docker exec microservice-as-mysql-1 mysql -uroot -p1111 microservice_as -e "delete from consents;"
curl -s -b ms7-cookies.txt -c ms7-cookies.txt \
  "$GW/oauth2/authorize?response_type=code&client_id=my-client&redirect_uri=http://127.0.0.1:8080/callback&scope=openid%20profile&code_challenge=$CHAL&code_challenge_method=S256" > ms7-consent.html
echo "introspect 노출 횟수: $(grep -c introspect ms7-consent.html)"
```
Expected: `0`

- [ ] **Step 8: discovery 확인**

```bash
curl -s http://localhost:9000/.well-known/openid-configuration | python3 -m json.tool \
  | grep -E "grant_types_supported|client_credentials|introspect|auth_methods" -A2
```
Expected: `grant_types_supported` 에 `client_credentials`, `scopes_supported` 에 `introspect`, `introspection_endpoint_auth_methods_supported` **부재**, `revocation_endpoint_auth_methods_supported` 존재

- [ ] **Step 9: 회귀 확인 (성공 기준 8)**

```bash
cd /tmp
GW=http://localhost:9000
echo "=== userinfo (client_credentials 토큰) -> 403 insufficient_scope ==="
curl -s -o /dev/null -D - -H "Authorization: Bearer $(cat ms7-cc-token.txt)" $GW/userinfo \
  | grep -iE "^HTTP|www-authenticate"
echo "=== revoke 는 여전히 Basic ==="
curl -s -o /dev/null -w "revoke: %{http_code}\n" -u my-client:secret -X POST $GW/oauth2/revoke -d "token=no-such-token"
```

그리고 슬라이스 3 e2e 의 refresh 회전 · 재사용 탐지 · code 재사용 항목을 다시 돌려 통과를 확인한다.

- [ ] **Step 10: 정리**

```bash
pkill -f "microservice.*SNAPSHOT.jar" 2>/dev/null
for p in 8081 8082 8083 8084 8085 8086 8087; do lsof -tiTCP:$p -sTCP:LISTEN 2>/dev/null | xargs kill -9 2>/dev/null; done
docker compose -p microservice-as -f oauth-2/authorization-server/practice/microservice/docker-compose/docker-compose.yml stop
```

- [ ] **Step 11: README 갱신**

기존 톤·구조·mermaid 스타일을 이어서 확장한다. 한국어, 경험담 서술 금지, 함정은 "주의." 로 현재형 지식 서술.

- 제목/개요에 "슬라이스 4: client 능력 scope 와 client_credentials" 추가
- 서비스 표의 token 행에 `client_credentials` grant 추가
- **`clients` 의 두 scope 컬럼 차이를 설명하는 절을 추가** — `scopes` 는 사용자 위임(동의 화면에 뜸), `client_scopes` 는 관리자 부여(뜨지 않음), grant 별로 보는 컬럼이 다름
- 기동 방법의 seed 설명에 `article-api` 의 `grant_types=client_credentials` · `client_scopes=introspect` 반영
- **시퀀스 다이어그램 추가**: client_credentials 로 토큰 획득 → 그 토큰으로 introspection
- "검증된 성공 기준" 을 이번 e2e 결과로 갱신(실제 통과한 것만)
- "알려진 한계" 갱신:
  - `sub == aud` — resource indicator(RFC 8707)를 쓰지 않아 발급 대상 자원을 표현할 수 없다
  - discovery 에 "Bearer 토큰 + 특정 scope 요구" 를 표현할 표준 필드가 없어 `introspection_endpoint_auth_methods_supported` 를 아예 내보내지 않는다
  - `scopes_supported` 에 `introspect` 가 있지만 사용자 위임 가능 여부를 discovery 로 구분할 수 없다
  - 슬라이스 3의 한계 중 이번에 해소된 것("인증된 client 면 누구의 토큰이든 조회 가능")은 **제거**한다
- 기동 방법의 `ddl-auto: update` 주의 항목에 **not null 컬럼 추가** 함정을 더한다

- [ ] **Step 12: http 파일**

`microservice/http/client-credentials.http` 신규:

```
### client_credentials — article-api 가 자기 자격증명으로 토큰을 받는다 (사용자 없음)
POST http://localhost:9000/oauth2/token
Authorization: Basic YXJ0aWNsZS1hcGk6c2VjcmV0
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&scope=introspect

### scope 생략 -> client_scopes 전부 (RFC 6749 3.3 의 사전 정의 기본값)
POST http://localhost:9000/oauth2/token
Authorization: Basic YXJ0aWNsZS1hcGk6c2VjcmV0
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials

### client_scopes 를 벗어난 요청 -> invalid_scope
POST http://localhost:9000/oauth2/token
Authorization: Basic YXJ0aWNsZS1hcGk6c2VjcmV0
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&scope=introspect admin

### grant 미등록 client -> unauthorized_client
POST http://localhost:9000/oauth2/token
Authorization: Basic bXktY2xpZW50OnNlY3JldA==
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials

### introspection — Bearer 로 호출한다. 위에서 받은 토큰을 쓴다
POST http://localhost:9000/oauth2/introspect
Authorization: Bearer {client_credentials_access_token}
Content-Type: application/x-www-form-urlencoded

token={검사할_토큰}

### Basic 은 더 이상 받지 않는다 -> 401 WWW-Authenticate: Bearer
POST http://localhost:9000/oauth2/introspect
Authorization: Basic YXJ0aWNsZS1hcGk6c2VjcmV0
Content-Type: application/x-www-form-urlencoded

token={검사할_토큰}

### introspect scope 없는 토큰으로 호출 -> 403 insufficient_scope
POST http://localhost:9000/oauth2/introspect
Authorization: Bearer {my-client_의_access_token}
Content-Type: application/x-www-form-urlencoded

token={검사할_토큰}
```

`http/token-lifecycle.http` 의 introspection 요청 두 개를 Bearer 로 바꾸고, Basic 예시는 "더 이상 받지 않는다" 로 의미를 뒤집는다.

- [ ] **Step 13: 커밋·푸시**

```bash
git add oauth-2/authorization-server/practice/microservice/README.md oauth-2/authorization-server/practice/microservice/http
git commit -m "microservice: e2e verification and docs for client credentials slice

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
git push origin main
```

---

## Self-Review 결과

- **Spec coverage**: §1 auth 무변경·token-state 무관여(Task 1·2 의 File Structure 주석) ✓ / §2 컬럼·계약·seed(Task 1, 2) ✓ / §3 client_credentials 규칙 전부 — grant 검사·부분집합·생략 기본값·refresh/id token 부재·sub=client_id(Task 3) ✓ / §4 Bearer 전환·RFC 6750 오류·revoke 비대칭·discovery 필드 제거·userinfo 403(Task 4, 5, 7 Step 9) ✓ / §5 실패 모드(Task 4 javadoc) ✓ / §6 이월 5건(Task 6 의 3건 + Task 5 의 length 단언 + Task 1 의 seed 테스트) ✓ / §7 단위·e2e 기준(Task 3·4·6·7) ✓ / §8 제외 항목 — 어느 태스크에도 없음 ✓
- **Placeholder scan**: "TBD"·"적절한 처리" 류 없음. 코드가 필요한 모든 Step 에 코드 블록이 있다. Task 6 Step 2 의 `withFamilyExpiresAt` 헬퍼만 "기존 관용구를 재사용하라" 로 두었는데, 기존 테스트에 이미 있는 헬퍼를 발명하지 말라는 지시이므로 placeholder 가 아니다.
- **Type 일관성**: `ClientResponse` 6필드(Task 1) = `ClientInfo` 6필드(Task 2), 둘 다 `clientScopes` 가 맨 뒤 ✓. `ClientCredentialsGrantService.grant(ClientInfo, String) → GrantResult`(Task 3 정의) = 같은 태스크 컨트롤러 호출 ✓. `GrantResult` · `TokenResponse` · `AccessTokenIssuer.issue` 는 슬라이스 3 산출물을 그대로 소비 ✓. `AccessTokenVerifier.VerifiedToken(sub, scopes, clientId, exp, iat)` 5필드(슬라이스 3) = Task 4 사용 ✓
- **알려진 진행상 주의**: Task 2 는 컴파일 오류를 의도적으로 만들어 호출부를 찾는 방식이다(Step 2 가 RED). Task 4 는 기존 테스트 7개를 10개로 교체하므로 총계가 늘고 준다 — 단언의 의미가 유지되는지가 검토 대상이다.
