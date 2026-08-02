# 마이크로서비스 인가 서버 슬라이스 5 — back-channel logout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** OP 가 로그아웃을 세션을 가진 모든 RP 에게 알리고, 같은 키로 서명되는 세 토큰 타입을 `typ` 헤더로 구분한다.

**Architecture:** `sid`(OP 세션 식별자)를 로그인 → authorize → id token 으로 관통시키고, 새 서비스 **session**(8088)이 `(sid, sub, client_id)` 레지스트리와 logout token 발송을 소유한다. auth 에 `end_session_endpoint` 를 만들고, 진짜 Spring Security RP 인 **demo-rp**(8095)로 상호운용성을 검증한다.

**Tech Stack:** Java 21, Spring Boot 3.4.5, Spring Data JPA, MySQL, Redis, nimbus-jose-jwt, Spring Security(RP 는 `spring-boot-starter-oauth2-client`), nginx

## Global Constraints

- Java 21 (gradle toolchain). 로컬 `java -jar` 는 `/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn/bin/java` 사용 (PATH java 는 17).
- Spring Boot **3.4.5**, io.spring.dependency-management **1.1.7**, gradle wrapper 8.13. 버전 하드코딩 오타 주의(전 서비스 동일해야 함).
- **SAS starter(`spring-boot-starter-oauth2-authorization-server`) 금지.** OAuth/OIDC 서버 로직은 직접 구현. **demo-rp 는 client 이므로 `spring-boot-starter-oauth2-client` 를 쓴다 — 이건 SAS starter 가 아니라 허용된다.**
- 각 서비스는 **독립 gradle 프로젝트**다(자체 `settings.gradle` + wrapper). 루트 멀티모듈 빌드가 없다.
- gradle 명령은 반드시 `--no-daemon`. `./gradlew` 가 exit 137(SIGKILL)이면 우회: `/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn/bin/java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon --rerun-tasks`
- 패키지 `dev.starryeye.<service_name>`(underscore). 새 서비스는 `dev.starryeye.session`, `dev.starryeye.demo_rp`.
- 포트: gateway 9000, auth 8081, token 8082, signing 8083, user-directory 8084, client-registry 8085, consent 8086, token-state 8087, **session 8088**, **demo-rp 8095**.
- MySQL `jdbc:mysql://localhost:3306/microservice_as` root/1111.
- 테스트에서 mock 빈은 `@MockitoBean` 사용(`@MockBean` 은 Boot 3.4 deprecated).
- cross-service 수신 record 에는 `@JsonIgnoreProperties(ignoreUnknown = true)`.
- DB 저장은 comma 구분, OAuth 와이어 포맷은 공백 구분. 변환은 경계에서 한 번만.
- 주석: 클래스 설명 javadoc 은 **클래스 바디 안**(여는 중괄호 아래). 경험담 서술 금지 — 함정은 "주의." 항목으로 현재형 지식 서술.
- h2 는 `testRuntimeOnly` 로 선언한다. `runtimeOnly` 는 운영 bootJar 에 h2 를 싣는다.
- `git add` 는 **경로를 명시**한다. `-A`/`-a`/`.` 금지 — 저장소에 상시 modified 로 두는 credential 파일이 있어 실제 비밀이 올라갈 수 있다.
- 커밋 직후 `git push origin main`. 거부되면 강제 푸시하지 말고 보고한다.
- 커밋 메시지 말미: `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`

## 현재 테스트 수 (시작 시점)

token **112**, token-state **42**, auth **16**, client-registry **5**, signing **4**, user-directory **6**, consent **4**.

## 공유 계약 (이 슬라이스에서 변경)

```
clients 테이블
  backchannel_logout_uri      varchar(500) null            ← 신규
  post_logout_redirect_uris   varchar(500) not null        ← 신규 (comma 구분)

client-registry  GET /internal/clients/{clientId}
  200 { clientId, redirectUris[], scopes[], clientSecretHash, grantTypes[], clientScopes[],
        backchannelLogoutUri, postLogoutRedirectUris[] }        ↑ 신규 2개 (맨 뒤)

Redis  auth:code:{code}   (auth 저장 · token 소비)
  { clientId, redirectUri, scope, sub, codeChallenge, nonce, authTime, sid }   ← sid 신규 (맨 뒤)

signing  POST /internal/sign
  { claims: {...}, typ: "at+jwt" | "logout+jwt" | "JWT" }        ← typ 신규

session  POST /internal/sessions          { sid, sub, clientId } → 200
session  POST /internal/sessions/logout   { sid }                → 200 (발송은 비동기)

auth  GET /oauth2/logout?id_token_hint&post_logout_redirect_uri&state    ← 신규
```

## File Structure

**client-registry**(8085)

| 파일 | 책임 |
|---|---|
| `jpa/ClientEntity.java` (수정) | `backchannelLogoutUri` · `postLogoutRedirectUris` 컬럼 |
| `dto/ClientResponse.java` (수정) | 두 필드 (**맨 뒤에** 추가) |
| `ClientController.java` (수정) | 엔티티 → 응답 변환 |
| `ClientSeedInitializer.java` (수정) | `demo-rp` client 추가 |

**signing**(8083)

| 파일 | 책임 |
|---|---|
| `dto/SignRequest.java` (수정) | `typ` 필드 |
| `SignController.java` (수정) | 헤더에 `typ` 지정 |

**token**(8082)

| 파일 | 책임 |
|---|---|
| `client/SigningClient.java` (수정) | `sign(claims, typ)` |
| `AccessTokenIssuer.java` (수정) | `at+jwt` |
| `IdTokenIssuer.java` (수정) | `JWT` · `sid` claim |
| `AccessTokenVerifier.java` (수정) | `typ != at+jwt` 거부 |
| `AuthorizationCodeData.java` (수정) | `sid` (**맨 뒤**) |
| `client/ClientInfo.java` (수정) | 두 필드 (**맨 뒤**) |
| `client/SessionClient.java` (신규) | session 서비스 호출 |
| `TokenEndpointController.java` (수정) | session 등록, discovery 3항목 |

**auth**(8081)

| 파일 | 책임 |
|---|---|
| `security/SessionIdIssuer.java` (신규) | `sid` 생성 · 세션 저장 · 조회 |
| `security/SecurityConfig.java` (수정) | 로그인 성공 핸들러, `/oauth2/logout` 접근 |
| `AuthorizationCodeIssuer.java` (수정) | `sid` 를 code 레코드에 |
| `PendingAuthorization.java` (수정) | `sid` (**맨 뒤**) |
| `AuthorizeController.java` (수정) | `sid` 전달 |
| `ConsentPageController.java` (수정) | `sid` 전달 |
| `LogoutController.java` (신규) | `GET /oauth2/logout` |
| `IdTokenHintVerifier.java` (신규) | 서명 검증(만료 무시) · `aud` 추출 |
| `client/SessionClient.java` (신규) | session 로그아웃 통지 |
| `client/SigningJwksClient.java` (신규) | jwks 조회 |

**session**(8088) — 신규 서비스

| 파일 | 책임 |
|---|---|
| `SessionApplication.java` | 진입점 |
| `jpa/OidcSessionEntity.java` · `OidcSessionEntityRepository.java` | 레지스트리 테이블 |
| `SessionService.java` | 등록(멱등) · 로그아웃 대상 조회 후 삭제 |
| `LogoutTokenFactory.java` | **logout token claim 구성** (계약이 사는 곳) |
| `LogoutTokenSender.java` | `@Async` 발송 |
| `SessionController.java` | 두 엔드포인트 |
| `client/ClientRegistryClient.java` · `client/SigningClient.java` | 외부 호출 |
| `dto/*.java` | 요청/응답 record |

**demo-rp**(8095) — 신규 서비스

| 파일 | 책임 |
|---|---|
| `DemoRpApplication.java` · `SecurityConfig.java` · `HomeController.java` | RP 전체 |

**gateway** — `nginx.conf` 에 `/oauth2/logout` 라우팅

**주의.** 두 record(`ClientResponse` · `ClientInfo`)와 `AuthorizationCodeData` · `PendingAuthorization` 에 필드를 **맨 뒤에** 추가한다. 중간에 끼우면 기존 위치 기반 생성자 호출에서 같은 타입 필드끼리 순서가 바뀌어도 컴파일이 통과해 조용히 어긋난다.

---

## Task 1: client-registry — 로그아웃 컬럼 2개 · demo-rp seed

**Files:**
- Modify: `microservice/client-registry/src/main/java/dev/starryeye/client_registry/jpa/ClientEntity.java`
- Modify: `microservice/client-registry/src/main/java/dev/starryeye/client_registry/dto/ClientResponse.java`
- Modify: `microservice/client-registry/src/main/java/dev/starryeye/client_registry/ClientController.java`
- Modify: `microservice/client-registry/src/main/java/dev/starryeye/client_registry/ClientSeedInitializer.java`
- Test: `microservice/client-registry/src/test/java/dev/starryeye/client_registry/ClientControllerTest.java` (수정)
- Test: `microservice/client-registry/src/test/java/dev/starryeye/client_registry/ClientSeedInitializerTest.java` (수정)

**Interfaces:**
- Produces: `ClientResponse(clientId, redirectUris, scopes, clientSecretHash, grantTypes, clientScopes, backchannelLogoutUri, postLogoutRedirectUris)` — 마지막 둘이 신규, `backchannelLogoutUri` 는 `String`(null 가능), `postLogoutRedirectUris` 는 `List<String>`
- Produces: `ClientEntity.getBackchannelLogoutUri() → String`, `getPostLogoutRedirectUris() → String`(comma 구분), 빌더에 두 파라미터

- [ ] **Step 1: 엔티티에 컬럼 2개 추가**

`ClientEntity.java` 의 `clientScopes` 필드 아래에 추가한다.

```java
	@Column(name = "backchannel_logout_uri", length = 500)
	private String backchannelLogoutUri; // 비어 있으면 back-channel logout 통지 대상이 아니다

	@Column(name = "post_logout_redirect_uris", nullable = false, length = 500)
	private String postLogoutRedirectUris; // comma 구분
```

빌더 생성자의 시그니처와 대입도 함께 바꾼다.

```java
	@Builder
	private ClientEntity(String clientId, String clientSecretHash, String redirectUris, String scopes,
			String grantTypes, String clientScopes, String backchannelLogoutUri, String postLogoutRedirectUris) {
		this.clientId = clientId;
		this.clientSecretHash = clientSecretHash;
		this.redirectUris = redirectUris;
		this.scopes = scopes;
		this.grantTypes = grantTypes;
		this.clientScopes = clientScopes;
		this.backchannelLogoutUri = backchannelLogoutUri;
		this.postLogoutRedirectUris = postLogoutRedirectUris;
	}
```

클래스 javadoc 에 다음을 추가한다.

```
	 * 주의. post_logout_redirect_uris 는 redirect_uris 와 별도 컬럼이다. 전자는 로그아웃 후 사용자를 돌려보낼 곳이고
	 *      후자는 authorization code 를 받을 곳이라 목적이 다르다. 한 컬럼에 섞으면 로그인 콜백 주소로 로그아웃
	 *      리다이렉트가 되거나 그 반대가 된다.
```

- [ ] **Step 2: ClientResponse 에 필드 2개 추가**

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
		List<String> clientScopes,
		String backchannelLogoutUri,
		List<String> postLogoutRedirectUris
) {
}
```

- [ ] **Step 3: 변환에 추가**

`ClientController.ClientLookupService.findByClientId` 의 `new ClientResponse(...)` 마지막 두 인자로 추가한다.

```java
			return new ClientResponse(
					entity.getClientId(),
					List.of(StringUtils.commaDelimitedListToStringArray(entity.getRedirectUris())),
					List.of(StringUtils.commaDelimitedListToStringArray(entity.getScopes())),
					entity.getClientSecretHash(),
					List.of(StringUtils.commaDelimitedListToStringArray(entity.getGrantTypes())),
					List.of(StringUtils.commaDelimitedListToStringArray(entity.getClientScopes())),
					entity.getBackchannelLogoutUri(),
					List.of(StringUtils.commaDelimitedListToStringArray(entity.getPostLogoutRedirectUris()))
			);
```

- [ ] **Step 4: seed 갱신 — 기존 2개에 빈 값, demo-rp 신규**

`ClientSeedInitializer.run` 의 기존 두 블록에 새 빌더 인자를 채우고, 세 번째 블록을 추가한다.

```java
		if (!repository.existsById("my-client")) {
			repository.save(ClientEntity.builder()
					.clientId("my-client")
					.clientSecretHash(encoder.encode("secret"))
					.redirectUris("http://127.0.0.1:8080/callback")
					.scopes("openid,profile,email,offline_access")
					.grantTypes("authorization_code,refresh_token")
					.clientScopes("")
					.backchannelLogoutUri(null) // curl 용 가상 client — 받을 서버가 없다
					.postLogoutRedirectUris("")
					.build());
		}

		if (!repository.existsById("article-api")) {
			repository.save(ClientEntity.builder()
					.clientId("article-api")
					.clientSecretHash(encoder.encode("secret"))
					.redirectUris("")
					.scopes("")
					.grantTypes("client_credentials")
					.clientScopes("introspect")
					.backchannelLogoutUri(null) // 사용자 세션이 없는 client 라 로그아웃 통지 대상이 아니다
					.postLogoutRedirectUris("")
					.build());
		}

		// 진짜 Spring Security RP. back-channel logout 상호운용성을 실증하는 client 다.
		// URI 3개는 Spring Security 의 기본 경로 규약을 따른다 (registrationId = microservice).
		if (!repository.existsById("demo-rp")) {
			repository.save(ClientEntity.builder()
					.clientId("demo-rp")
					.clientSecretHash(encoder.encode("secret"))
					.redirectUris("http://localhost:8095/login/oauth2/code/microservice")
					.scopes("openid,profile,email")
					.grantTypes("authorization_code,refresh_token")
					.clientScopes("")
					.backchannelLogoutUri("http://localhost:8095/logout/connect/back-channel/microservice")
					.postLogoutRedirectUris("http://localhost:8095/")
					.build());
		}
```

- [ ] **Step 5: 기존 테스트에 새 필드 단언 추가**

`ClientControllerTest` 의 `returnsClientWithUserDelegatedScopesOnly`(my-client)와 `returnsClientWithClientScopesOnly`(article-api)에 각각 다음 두 줄을 추가한다.

```java
				.andExpect(jsonPath("$.backchannelLogoutUri").doesNotExist())
				.andExpect(jsonPath("$.postLogoutRedirectUris.length()").value(0))
```

- [ ] **Step 6: demo-rp 계약 테스트 추가**

`ClientControllerTest` 에 추가한다. 이 테스트가 **두 URI 컬럼이 와이어에서 분리돼 실린다는 유일한 계약 단언**이다.

```java
	// demo-rp: 로그아웃 URI 두 개가 각각 제 필드로 실리는지 확인한다.
	// redirect_uri 와 post_logout_redirect_uri 는 목적이 달라 절대 같은 필드로 나가면 안 된다.
	@Test
	void returnsClientWithLogoutUris() throws Exception {
		mockMvc.perform(get("/internal/clients/demo-rp"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clientId").value("demo-rp"))
				.andExpect(jsonPath("$.redirectUris[0]")
						.value("http://localhost:8095/login/oauth2/code/microservice"))
				.andExpect(jsonPath("$.redirectUris.length()").value(1))
				.andExpect(jsonPath("$.backchannelLogoutUri")
						.value("http://localhost:8095/logout/connect/back-channel/microservice"))
				.andExpect(jsonPath("$.postLogoutRedirectUris[0]").value("http://localhost:8095/"))
				.andExpect(jsonPath("$.postLogoutRedirectUris.length()").value(1))
				.andExpect(jsonPath("$.clientScopes.length()").value(0));
	}
```

- [ ] **Step 7: seed 테스트 갱신**

`ClientSeedInitializerTest.seedsBothClientsOnEmptyDatabase` 를 세 client 기준으로 고치고 이름을 `seedsAllClientsOnEmptyDatabase` 로 바꾼다. `demo-rp` 행이 생기고 `backchannelLogoutUri` 가 비어 있지 않은지 단언한다.

```java
	@Test
	void seedsAllClientsOnEmptyDatabase() {
		initializer.run(null);

		assertThat(repository.count()).isEqualTo(3);
		assertThat(repository.findById("demo-rp")).get()
				.extracting(ClientEntity::getBackchannelLogoutUri)
				.isEqualTo("http://localhost:8095/logout/connect/back-channel/microservice");
	}
```

- [ ] **Step 8: 테스트 실행**

Run (client-registry 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks`
Expected: PASS, 6 tests (기존 5 + 신규 1)

- [ ] **Step 9: 뮤테이션 확인**

`ClientController` 의 변환에서 `entity.getPostLogoutRedirectUris()` 를 `entity.getRedirectUris()` 로 바꾼 뒤 테스트를 돌린다.
Expected: `returnsClientWithLogoutUris` FAIL (postLogoutRedirectUris 가 로그인 콜백 주소가 된다)
확인 후 되돌리고 `git diff` 로 원복을 증명한다.

- [ ] **Step 10: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/client-registry/src
git commit -m "$(cat <<'EOF'
microservice: add backchannel logout columns and demo-rp client

back-channel logout 통지 주소와 로그아웃 후 리다이렉트 주소를 clients 에 담는다.
post_logout_redirect_uris 는 redirect_uris 와 목적이 달라 별도 컬럼이다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 2: signing — 서명 API 가 `typ` 을 받는다

**Files:**
- Modify: `microservice/signing/src/main/java/dev/starryeye/signing/dto/SignRequest.java`
- Modify: `microservice/signing/src/main/java/dev/starryeye/signing/SignController.java`
- Test: `microservice/signing/src/test/java/dev/starryeye/signing/SignControllerTest.java` (없으면 신규)

**Interfaces:**
- Consumes: 없음
- Produces: `POST /internal/sign` 요청 본문 `{ claims: Map, typ: String }`. `typ` 이 null·빈 문자열이면 `"JWT"` 로 서명한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`SignControllerTest.java` 에 추가한다(파일이 없으면 아래 전체로 새로 만든다).

```java
package dev.starryeye.signing;

import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SignControllerTest {

	@Autowired MockMvc mockMvc;

	private String signWith(String typRequestFragment) throws Exception {
		String body = "{\"claims\":{\"sub\":\"user-sub-0001\"}" + typRequestFragment + "}";
		String response = mockMvc.perform(post("/internal/sign")
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return com.jayway.jsonpath.JsonPath.read(response, "$.jwt");
	}

	// 요청이 지정한 typ 이 헤더에 그대로 실려야 한다. 토큰 타입 혼동을 막는 유일한 표식이다.
	@Test
	void signsWithRequestedTyp() throws Exception {
		SignedJWT jwt = SignedJWT.parse(signWith(",\"typ\":\"at+jwt\""));
		assertThat(jwt.getHeader().getType().toString()).isEqualTo("at+jwt");
	}

	@Test
	void signsLogoutTokenTyp() throws Exception {
		SignedJWT jwt = SignedJWT.parse(signWith(",\"typ\":\"logout+jwt\""));
		assertThat(jwt.getHeader().getType().toString()).isEqualTo("logout+jwt");
	}

	// typ 을 안 보내는 구버전 호출자는 JWT 로 서명된다. at+jwt 를 요구하는 검증기는 그 토큰을 거부하므로
	// 구버전 token 서비스가 새 검증을 통과하는 access token 을 만들어낼 수 없다.
	@Test
	void defaultsToPlainJwtWhenTypIsAbsent() throws Exception {
		SignedJWT jwt = SignedJWT.parse(signWith(""));
		assertThat(jwt.getHeader().getType().toString()).isEqualTo("JWT");
	}

	@Test
	void signedJwtCarriesKeyIdInHeader() throws Exception {
		SignedJWT jwt = SignedJWT.parse(signWith(",\"typ\":\"at+jwt\""));
		assertThat(jwt.getHeader().getKeyID()).isNotBlank();
	}
}
```

- [ ] **Step 2: 실패 확인**

Run (signing 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks --tests '*SignControllerTest*'`
Expected: FAIL — 컴파일은 되지만 `getType()` 이 null 이라 NPE 또는 단언 실패

- [ ] **Step 3: SignRequest 에 typ 추가**

```java
package dev.starryeye.signing.dto;

import java.util.Map;

public record SignRequest(Map<String, Object> claims, String typ) {
}
```

- [ ] **Step 4: SignController 가 typ 을 헤더에 싣는다**

`SignController.sign` 의 헤더 구성을 바꾸고 javadoc 을 갱신한다.

```java
	/**
	 * "서명 기계" 로서 claims 를 받아 RS256 으로 서명한 JWT 를 돌려준다.
	 *      iss/exp 같은 표준 claim 은 호출자가 채워서 넘긴다. 이 서비스는 정책 판단을 하지 않고 서명 + kid/typ 지정만 한다.
	 *
	 * 주의. typ 은 호출자가 정한다. 같은 키로 access token(at+jwt) · id token(JWT) · logout token(logout+jwt) 이
	 *      서명되므로, 이 헤더가 세 토큰을 구분하는 유일한 표식이다. 어떤 토큰인지는 claim 을 만드는 쪽만 안다.
	 *
	 * 주의. typ 이 없으면 JWT 로 서명한다. at+jwt 를 요구하는 검증기가 그 토큰을 거부하므로, typ 을 보내지 않는
	 *      구버전 호출자가 access token 으로 통하는 JWT 를 만들어낼 수 없다.
	 */

	private static final JOSEObjectType DEFAULT_TYPE = JOSEObjectType.JWT;

	private final JwkKeyProvider keyProvider;

	@PostMapping("/internal/sign")
	public SignResponse sign(@RequestBody SignRequest request) throws Exception {

		JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder();
		request.claims().forEach(claimsBuilder::claim);

		JOSEObjectType type = StringUtils.hasText(request.typ())
				? new JOSEObjectType(request.typ())
				: DEFAULT_TYPE;

		JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
				.keyID(keyProvider.getSigningKey().getKeyID())
				.type(type)
				.build();

		SignedJWT signedJWT = new SignedJWT(header, claimsBuilder.build());
		signedJWT.sign(new RSASSASigner(keyProvider.getSigningKey()));

		return new SignResponse(signedJWT.serialize());
	}
```

import 두 개를 추가한다.

```java
import com.nimbusds.jose.JOSEObjectType;
import org.springframework.util.StringUtils;
```

- [ ] **Step 5: 테스트 통과 확인**

Run (signing 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks`
Expected: PASS, 8 tests (기존 4 + 신규 4)

- [ ] **Step 6: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/signing/src
git commit -m "$(cat <<'EOF'
microservice: let sign requests choose the JWT typ header

같은 키로 서명되는 토큰이 셋이 되므로 typ 으로 구분한다. 헤더 소유권은 signing 에
있지만 어떤 토큰인지는 claim 을 만드는 쪽만 알기 때문에 호출자가 지정한다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 3: token — 세 토큰의 `typ` 지정과 검증 강제

**Files:**
- Modify: `microservice/token/src/main/java/dev/starryeye/token/client/SigningClient.java`
- Modify: `microservice/token/src/main/java/dev/starryeye/token/AccessTokenIssuer.java`
- Modify: `microservice/token/src/main/java/dev/starryeye/token/IdTokenIssuer.java`
- Modify: `microservice/token/src/main/java/dev/starryeye/token/AccessTokenVerifier.java`
- Test: `microservice/token/src/test/java/dev/starryeye/token/AccessTokenVerifierTest.java` (수정)
- Test: `microservice/token/src/test/java/dev/starryeye/token/AccessTokenIssuerTest.java` (없으면 신규)

**Interfaces:**
- Consumes: `POST /internal/sign` 이 `{claims, typ}` 를 받는다 (Task 2)
- Produces: `SigningClient.sign(Map<String,Object> claims, String typ) → String`. 기존 1인자 오버로드는 **남기지 않는다** — 남기면 호출자가 조용히 `JWT` 를 받는다.
- Produces: `AccessTokenVerifier.verify(String)` 가 `typ != "at+jwt"` 인 토큰에 `InvalidTokenException("unexpected token type")` 을 던진다

- [ ] **Step 1: 실패하는 검증 테스트 작성**

`AccessTokenVerifierTest` 에 추가한다. 이 파일에는 이미 실제 RSA 키로 토큰을 만드는 헬퍼가 있다 — **그 헬퍼가 헤더 `typ` 을 지정할 수 있게 확장**한 뒤 아래 두 테스트를 추가한다. 헬퍼 이름과 시그니처는 기존 파일을 읽고 맞춘다.

```java
	// typ 이 at+jwt 가 아니면 거부한다. 같은 키로 서명된 id token·logout token 이 access token 으로
	// 통하는 것을 막는 구조적 방어이며, scope claim 부재 같은 우연한 결손에 의존하지 않는다.
	@Test
	void rejectsTokenWhoseTypIsNotAccessToken() {
		String idTokenLike = signWithTyp("JWT");

		assertThatThrownBy(() -> verifier.verify(idTokenLike))
				.isInstanceOf(AccessTokenVerifier.InvalidTokenException.class);
	}

	@Test
	void rejectsLogoutTokenPresentedAsAccessToken() {
		String logoutTokenLike = signWithTyp("logout+jwt");

		assertThatThrownBy(() -> verifier.verify(logoutTokenLike))
				.isInstanceOf(AccessTokenVerifier.InvalidTokenException.class);
	}

	@Test
	void acceptsTokenWithAccessTokenTyp() {
		String accessToken = signWithTyp("at+jwt");

		assertThat(verifier.verify(accessToken).sub()).isEqualTo("user-sub-0001");
	}
```

- [ ] **Step 2: 실패 확인**

Run (token 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks --tests '*AccessTokenVerifierTest*'`
Expected: `rejectsTokenWhoseTypIsNotAccessToken` · `rejectsLogoutTokenPresentedAsAccessToken` FAIL (검증이 없어 통과해 버린다)

- [ ] **Step 3: SigningClient 에 typ 파라미터 추가**

```java
	/**
	 * signing 서비스에 claims 와 typ 을 넘겨 서명된 JWT 를 받는다. jwks 도 signing 이 소유하므로 여기서 프록시한다.
	 *
	 * 주의. typ 없는 오버로드를 만들지 않는다. 만들면 호출자가 typ 을 잊었을 때 조용히 JWT 로 서명되고,
	 *      그 토큰은 at+jwt 를 요구하는 검증기에서만 뒤늦게 거부된다.
	 */

	public String sign(Map<String, Object> claims, String typ) {
		Map<String, Object> body = Map.of("claims", claims, "typ", typ);
		Map<?, ?> response = restClient.post().uri("/internal/sign").body(body).retrieve().body(Map.class);
		return (String) response.get("jwt");
	}
```

- [ ] **Step 4: 두 발급자가 typ 을 지정한다**

`AccessTokenIssuer.issue` 의 마지막 줄을 바꾸고 상수를 둔다.

```java
	// RFC 9068 2.1 이 access token 에 규정한 typ 이다.
	private static final String ACCESS_TOKEN_TYP = "at+jwt";

	...
		return signingClient.sign(claims, ACCESS_TOKEN_TYP);
```

`IdTokenIssuer.issue` 의 마지막 줄을 바꾼다.

```java
	// OIDC Core 는 id token 에 별도 typ 을 요구하지 않는다. 일반 JWT 로 둔다.
	private static final String ID_TOKEN_TYP = "JWT";

	...
		return signingClient.sign(claims, ID_TOKEN_TYP);
```

- [ ] **Step 5: AccessTokenVerifier 가 typ 을 강제한다**

`verify` 의 서명 검증 직후, `exp` 검사 앞에 넣는다.

```java
		JOSEObjectType type = signedJWT.getHeader().getType();
		if (type == null || !ACCESS_TOKEN_TYP.equals(type.toString())) {
			throw new InvalidTokenException("unexpected token type");
		}
```

클래스 상단에 상수와 import 를 추가한다.

```java
import com.nimbusds.jose.JOSEObjectType;

	private static final String ACCESS_TOKEN_TYP = "at+jwt";
```

클래스 javadoc 에 추가한다.

```
	 * 주의. typ 이 at+jwt 인지 확인한다(RFC 9068 2.1). 같은 키로 id token 과 logout token 도 서명되므로,
	 *      이 검사가 없으면 세 토큰이 서로 통한다. id token 에 scope claim 이 없고 logout token 에 exp 가
	 *      없어서 지금은 우연히 막히지만, 그 결손이 메워지는 순간 방어가 사라진다.
```

- [ ] **Step 6: 테스트 통과 확인**

Run (token 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks`
Expected: PASS. 기존 테스트 중 `SigningClient.sign` 을 mock 한 곳은 인자 개수가 늘어 컴파일 오류가 난다 — `any(), any()` 로 맞춘다. **단언은 바꾸지 않는다.**

- [ ] **Step 7: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/token/src
git commit -m "$(cat <<'EOF'
microservice: stamp and enforce the at+jwt token type

access token 은 at+jwt, id token 은 JWT 로 서명하고 AccessTokenVerifier 가
at+jwt 를 강제한다. 토큰 타입 혼동을 막는 방어가 우연한 claim 결손이 아니라
헤더 검증에서 나오게 한다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 4: auth — `sid` 생성과 code 레코드 확장

**Files:**
- Create: `microservice/auth/src/main/java/dev/starryeye/auth/security/SessionIdIssuer.java`
- Modify: `microservice/auth/src/main/java/dev/starryeye/auth/security/SecurityConfig.java`
- Modify: `microservice/auth/src/main/java/dev/starryeye/auth/AuthorizationCodeIssuer.java`
- Modify: `microservice/auth/src/main/java/dev/starryeye/auth/PendingAuthorization.java`
- Modify: `microservice/auth/src/main/java/dev/starryeye/auth/AuthorizeController.java`
- Modify: `microservice/auth/src/main/java/dev/starryeye/auth/ConsentPageController.java`
- Test: `microservice/auth/src/test/java/dev/starryeye/auth/security/SessionIdIssuerTest.java` (신규)

**Interfaces:**
- Produces: `SessionIdIssuer.issue(HttpSession) → String` — `sid` 를 만들어 세션 속성에 저장하고 돌려준다. 이미 있으면 기존 값을 돌려준다(멱등)
- Produces: `SessionIdIssuer.currentSid(HttpSession) → String` — 없으면 `null`
- Produces: `AuthorizationCodeIssuer.issue(clientId, redirectUri, scope, sub, codeChallenge, nonce, authTime, sid)` — `sid` 가 **맨 뒤**
- Produces: Redis 레코드에 `sid` 키가 추가된다 (token 이 Task 5 에서 읽는다)

- [ ] **Step 1: 실패하는 테스트 작성**

`SessionIdIssuerTest.java`:

```java
package dev.starryeye.auth.security;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

class SessionIdIssuerTest {

	private final SessionIdIssuer issuer = new SessionIdIssuer();

	// 같은 세션에서 여러 RP 가 authorize 해도 sid 는 하나여야 한다. OP 세션 하나 = sid 하나다.
	@Test
	void issuesTheSameSidForOneSession() {
		HttpSession session = new MockHttpSession();

		String first = issuer.issue(session);
		String second = issuer.issue(session);

		assertThat(second).isEqualTo(first);
	}

	@Test
	void issuesDifferentSidForDifferentSessions() {
		assertThat(issuer.issue(new MockHttpSession()))
				.isNotEqualTo(issuer.issue(new MockHttpSession()));
	}

	// sid 는 HTTP 세션 id 가 아니다. id token 에 실려 RP 로 나가고 로그에도 남으므로,
	// 실제 세션 id 를 노출하면 세션 탈취 표면이 된다.
	@Test
	void sidIsNotTheHttpSessionId() {
		MockHttpSession session = new MockHttpSession();

		assertThat(issuer.issue(session)).isNotEqualTo(session.getId());
	}

	@Test
	void sidIsUrlSafeAndLongEnoughToResistGuessing() {
		String sid = issuer.issue(new MockHttpSession());

		assertThat(sid).matches("[A-Za-z0-9_-]+").hasSizeGreaterThanOrEqualTo(22);
	}

	@Test
	void currentSidIsNullBeforeIssue() {
		assertThat(issuer.currentSid(new MockHttpSession())).isNull();
	}

	@Test
	void currentSidReturnsIssuedValue() {
		HttpSession session = new MockHttpSession();
		String issued = issuer.issue(session);

		assertThat(issuer.currentSid(session)).isEqualTo(issued);
	}
}
```

- [ ] **Step 2: 실패 확인**

Run (auth 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks --tests '*SessionIdIssuerTest*'`
Expected: FAIL — `SessionIdIssuer` 가 없어 컴파일 실패

- [ ] **Step 3: SessionIdIssuer 구현**

```java
package dev.starryeye.auth.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SessionIdIssuer {

	/**
	 * OP 세션 식별자(sid)를 만들어 세션 속성에 담는다. (OIDC Back-Channel Logout 1.0 의 sid claim)
	 *      로그인 한 번에 sid 하나가 나오고, 그 세션에서 authorize 하는 모든 RP 가 같은 sid 를 받는다.
	 *
	 * 주의. sid 는 HTTP 세션 id 가 아니다. sid 는 id token 에 실려 RP 로 나가고 로그에도 남으므로,
	 *      실제 세션 id 를 그대로 쓰면 세션 탈취 표면이 된다.
	 *
	 * 주의. 추측 가능한 값이면 남의 세션을 지목하는 logout token 을 위조할 근거가 되므로 SecureRandom 을 쓴다.
	 */

	static final String SESSION_ATTRIBUTE = "OP_SID";

	private static final int BYTE_LENGTH = 16; // base64url 22자
	private static final SecureRandom RANDOM = new SecureRandom();

	public String issue(HttpSession session) {
		String existing = currentSid(session);
		if (existing != null) {
			return existing;
		}
		byte[] bytes = new byte[BYTE_LENGTH];
		RANDOM.nextBytes(bytes);
		String sid = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		session.setAttribute(SESSION_ATTRIBUTE, sid);
		return sid;
	}

	public String currentSid(HttpSession session) {
		return (String) session.getAttribute(SESSION_ATTRIBUTE);
	}
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run (auth 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks --tests '*SessionIdIssuerTest*'`
Expected: PASS, 6 tests

- [ ] **Step 5: 로그인 성공 시 sid 를 만든다**

`SecurityConfig.filterChain` 의 `formLogin` 설정에 성공 핸들러를 붙인다.

```java
	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http, RemoteAuthenticationProvider provider,
			SessionIdIssuer sessionIdIssuer) throws Exception {
		SavedRequestAwareAuthenticationSuccessHandler successHandler =
				new SavedRequestAwareAuthenticationSuccessHandler();

		http
				.authenticationProvider(provider)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/login", "/error").permitAll()
						.anyRequest().authenticated()
				)
				.formLogin(form -> form
						.permitAll()
						.successHandler((request, response, authentication) -> {
							// 세션 고정 방어로 세션이 새로 만들어진 뒤이므로 여기가 sid 를 만들 자리다.
							sessionIdIssuer.issue(request.getSession(true));
							successHandler.onAuthenticationSuccess(request, response, authentication);
						})
				)
				.csrf(csrf -> csrf.ignoringRequestMatchers("/oauth2/authorize"));

		return http.build();
	}
```

import 를 추가한다.

```java
import dev.starryeye.auth.security.SessionIdIssuer;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
```

(`SecurityConfig` 가 이미 `dev.starryeye.auth.security` 패키지이므로 `SessionIdIssuer` import 는 불필요하다. 패키지가 다르면 추가한다.)

- [ ] **Step 6: code 레코드에 sid 를 싣는다**

`AuthorizationCodeIssuer.issue` 시그니처 마지막에 `String sid` 를 추가하고 `data.put("sid", sid)` 를 넣는다.

```java
	public String issue(String clientId, String redirectUri, String scope, String sub, String codeChallenge,
			String nonce, long authTime, String sid) {
		String code = UUID.randomUUID().toString().replace("-", "");
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("clientId", clientId);
		data.put("redirectUri", redirectUri);
		data.put("scope", scope);
		data.put("sub", sub);
		data.put("codeChallenge", codeChallenge);
		data.put("nonce", nonce);
		data.put("authTime", authTime);
		data.put("sid", sid);
		try {
			redisTemplate.opsForValue().set(KEY_PREFIX + code, objectMapper.writeValueAsString(data), Duration.ofSeconds(ttlSeconds));
		} catch (Exception e) {
			throw new IllegalStateException("failed to store authorization code", e);
		}
		return code;
	}
```

javadoc 의 nonce/authTime 설명 문단에 한 줄을 덧붙인다.

```
	 *      sid 도 같은 이유로 여기 담는다 — OP 세션 식별자는 로그인한 이 서비스만 알고, 필요한 곳은 token 이다.
```

- [ ] **Step 7: PendingAuthorization 에 sid 추가 (맨 뒤)**

```java
public record PendingAuthorization(
		String clientId,
		String redirectUri,
		String scope,
		String sub,
		String codeChallenge,
		String state,
		String nonce,
		long authTime,
		String sid
) {
}
```

- [ ] **Step 8: 두 컨트롤러가 sid 를 전달한다**

`AuthorizeController.authorize` 에 `HttpSession session` 파라미터를 추가하고, `authTime` 계산 옆에서 `sid` 를 읽는다.

```java
		long authTime = java.time.Instant.now().getEpochSecond();
		String sid = sessionIdIssuer.issue(session); // 로그인 시 만들어져 있다. 없으면(세션 재생성 등) 여기서 만든다
```

`pendingStore.save(...)` 마지막 인자와 `codeIssuer.issue(...)` 마지막 인자에 `sid` 를 넣는다. 필드에 `private final SessionIdIssuer sessionIdIssuer;` 를 추가한다.

`ConsentPageController` 의 `codeIssuer.issue(...)` 마지막 인자에 `pending.sid()` 를 넣는다.

```java
		String code = codeIssuer.issue(pending.clientId(), pending.redirectUri(),
				String.join(" ", finalScopes), pending.sub(), pending.codeChallenge(),
				pending.nonce(), pending.authTime(), pending.sid());
```

- [ ] **Step 9: 전체 테스트 실행**

Run (auth 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks`
Expected: PASS, 22 tests (기존 16 + 신규 6). 기존 테스트가 `issue(...)` 인자 개수로 컴파일 실패하면 인자만 맞추고 **단언은 바꾸지 않는다.**

- [ ] **Step 10: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/auth/src
git commit -m "$(cat <<'EOF'
microservice: mint an OP session id and carry it on the code record

로그인 성공 시 sid 를 만들어 세션에 담고, authorize 가 그것을 code 레코드에 실어
token 으로 넘긴다. sid 는 HTTP 세션 id 가 아니라 별도 난수다 — id token 으로
나가는 값이라 실제 세션 id 를 노출할 수 없다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 5: token — code 레코드에서 `sid` 를 읽어 id token 에 싣는다

**Files:**
- Modify: `microservice/token/src/main/java/dev/starryeye/token/AuthorizationCodeData.java`
- Modify: `microservice/token/src/main/java/dev/starryeye/token/IdTokenIssuer.java`
- Modify: `microservice/token/src/main/java/dev/starryeye/token/TokenEndpointController.java`
- Modify: `microservice/token/src/main/java/dev/starryeye/token/RefreshTokenGrantService.java`
- Test: `microservice/token/src/test/java/dev/starryeye/token/IdTokenIssuerTest.java` (수정)

**Interfaces:**
- Consumes: Redis code 레코드에 `sid` 키 (Task 4)
- Produces: `AuthorizationCodeData(clientId, redirectUri, scope, sub, codeChallenge, nonce, authTime, sid)` — `sid` **맨 뒤**
- Produces: `IdTokenIssuer.issue(sub, clientId, scope, nonce, authTime, accessToken, sid) → String` — `sid` **맨 뒤**

- [ ] **Step 1: 실패하는 테스트 작성**

`IdTokenIssuerTest` 에 추가한다. 이 파일에는 이미 claim 을 실물로 단언하는 방식이 있으므로 같은 방식을 따른다.

```java
	// RP 는 id token 의 sid 로 자기 세션을 색인한다. 이 claim 이 없으면 세션 단위 로그아웃이 불가능하고
	// sub 로 그 사용자의 모든 세션을 통째로 죽이는 것만 가능해진다.
	@Test
	void idTokenCarriesSid() {
		issuer.issue("user-sub-0001", "demo-rp", "openid", null, 1700000000L, "access-token", "SID-ABC");

		assertThat(capturedClaims()).containsEntry("sid", "SID-ABC");
	}

	// refresh 로 재발급하는 id token 에도 원래 세션의 sid 가 그대로 실려야 한다.
	// 새 값을 만들면 RP 가 색인해 둔 세션과 어긋나 로그아웃 통지가 그 세션을 못 찾는다.
	@Test
	void idTokenOmitsSidWhenSessionIsUnknown() {
		issuer.issue("user-sub-0001", "demo-rp", "openid", null, 1700000000L, "access-token", null);

		assertThat(capturedClaims()).doesNotContainKey("sid");
	}
```

`capturedClaims()` 는 `ArgumentCaptor` 로 `signingClient.sign(claims, typ)` 의 첫 인자를 잡는 헬퍼다. 기존 파일에 유사 헬퍼가 있으면 재사용하고, 없으면 아래를 추가한다.

```java
	@SuppressWarnings("unchecked")
	private Map<String, Object> capturedClaims() {
		ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
		verify(signingClient).sign(captor.capture(), eq("JWT"));
		return captor.getValue();
	}
```

- [ ] **Step 2: 실패 확인**

Run (token 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks --tests '*IdTokenIssuerTest*'`
Expected: FAIL — `issue` 인자 개수가 안 맞아 컴파일 실패

- [ ] **Step 3: AuthorizationCodeData 에 sid 추가 (맨 뒤)**

```java
package dev.starryeye.token;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record AuthorizationCodeData(
		String clientId,
		String redirectUri,
		String scope,
		String sub,
		String codeChallenge,
		String nonce,
		long authTime,
		String sid
) {
}
```

- [ ] **Step 4: IdTokenIssuer 가 sid 를 싣는다**

`issue` 시그니처 마지막에 `String sid` 를 추가하고, `nonce` 처리 아래에 넣는다.

```java
		if (StringUtils.hasText(nonce)) {
			claims.put("nonce", nonce); // 요청에 있었으면 그대로 되돌려준다 (표준 요구)
		}
		if (StringUtils.hasText(sid)) {
			claims.put("sid", sid); // RP 가 자기 세션을 색인하는 키다 (Back-Channel Logout 1.0)
		}
```

클래스 javadoc 에 추가한다.

```
	 * 주의. sid 는 authorize 시점의 OP 세션 식별자를 그대로 나른다. 여기서 새로 만들면 RP 가 색인해 둔 값과
	 *      어긋나 로그아웃 통지가 그 세션을 찾지 못한다.
```

- [ ] **Step 5: 호출부 두 곳에 sid 를 전달한다**

`TokenEndpointController` 의 authorization_code 경로에서 `idTokenIssuer.issue(...)` 마지막 인자로 `data.sid()` 를 넘긴다.

`RefreshTokenGrantService` 에서 id token 을 재발급하는 지점은 `sid` 를 알 방법이 없다. **`null` 을 넘기고 아래 주석을 남긴다.**

```java
		// 주의. refresh 경로에는 sid 가 없다. refresh token 레코드가 sid 를 보관하지 않기 때문이며,
		//      그 결과 refresh 로 재발급한 id token 에는 sid 가 빠진다(알려진 한계).
```

- [ ] **Step 6: 테스트 통과 확인**

Run (token 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks`
Expected: PASS. 기존 테스트가 인자 개수로 깨지면 인자만 맞춘다.

- [ ] **Step 7: 뮤테이션 확인**

`IdTokenIssuer` 의 `claims.put("sid", sid)` 줄을 지우고 테스트를 돌린다.
Expected: `idTokenCarriesSid` FAIL
확인 후 되돌리고 `git diff` 로 원복을 증명한다.

- [ ] **Step 8: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/token/src
git commit -m "$(cat <<'EOF'
microservice: put the OP session id in the id token

RP 는 id token 의 sid 로 자기 세션을 색인한다. 이 claim 이 없으면 세션 단위
로그아웃이 성립하지 않는다. refresh 재발급 경로는 sid 를 모르므로 생략한다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 6: session(8088) 스캐폴드와 레지스트리 API

**Files:**
- Create: `microservice/session/build.gradle` · `settings.gradle` · `gradlew` · `gradle/wrapper/*` · `.gitignore`
- Create: `microservice/session/src/main/java/dev/starryeye/session/SessionApplication.java`
- Create: `microservice/session/src/main/java/dev/starryeye/session/jpa/OidcSessionEntity.java`
- Create: `microservice/session/src/main/java/dev/starryeye/session/jpa/OidcSessionEntityRepository.java`
- Create: `microservice/session/src/main/java/dev/starryeye/session/SessionService.java`
- Create: `microservice/session/src/main/java/dev/starryeye/session/LogoutTargets.java`
- Create: `microservice/session/src/main/java/dev/starryeye/session/dto/RegisterSessionRequest.java`
- Create: `microservice/session/src/main/resources/application.yml`
- Create: `microservice/session/src/test/resources/application.yml`
- Test: `microservice/session/src/test/java/dev/starryeye/session/SessionServiceTest.java`

**Interfaces:**
- Produces: `SessionService.register(String sid, String sub, String clientId)` — 멱등
- Produces: `SessionService.consumeForLogout(String sid) → LogoutTargets` — 행을 읽어 **삭제하고** 대상을 돌려준다
- Produces: `LogoutTargets(String sub, List<String> clientIds)` — 세션이 없으면 `sub` 가 `null`, `clientIds` 가 빈 목록

- [ ] **Step 1: 스캐폴드 복사**

`token-state` 를 원본으로 복사한 뒤 이름을 바꾼다. gradle wrapper 는 그대로 쓴다.

```bash
cd oauth-2/authorization-server/practice/microservice
cp -R token-state session
rm -rf session/build session/.gradle session/src/main/java session/src/test/java session/src/test/resources
mkdir -p session/src/main/java/dev/starryeye/session session/src/test/java/dev/starryeye/session session/src/test/resources
```

`session/settings.gradle` 의 프로젝트 이름을 `session` 으로 바꾼다.

```gradle
rootProject.name = 'session'
```

`session/build.gradle` 에 RestClient 호출이 필요하므로 `spring-boot-starter-web` 은 이미 있다. 그대로 둔다.

- [ ] **Step 2: application.yml 두 개**

`session/src/main/resources/application.yml`:

```yaml
server:
  port: 8088

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
  issuer: http://localhost:9000
  signing-base-url: http://localhost:8083
  client-registry-base-url: http://localhost:8085

logging:
  level:
    dev.starryeye: DEBUG
```

`session/src/test/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:session;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop

my:
  issuer: http://localhost:9000
  signing-base-url: http://localhost:8083
  client-registry-base-url: http://localhost:8085
```

- [ ] **Step 3: 실패하는 테스트 작성**

`SessionServiceTest.java`:

```java
package dev.starryeye.session;

import dev.starryeye.session.jpa.OidcSessionEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SessionServiceTest {

	@Autowired SessionService service;
	@Autowired OidcSessionEntityRepository repository;

	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	@Test
	void registersOneRowPerClient() {
		service.register("SID-1", "user-sub-0001", "demo-rp");
		service.register("SID-1", "user-sub-0001", "other-rp");

		assertThat(repository.findBySid("SID-1")).hasSize(2);
	}

	// 같은 RP 가 여러 번 code 를 교환할 수 있다. 그때마다 행이 늘면 로그아웃 때 같은 RP 로 여러 번 보내게 된다.
	@Test
	void registerIsIdempotentForTheSameClient() {
		service.register("SID-1", "user-sub-0001", "demo-rp");
		service.register("SID-1", "user-sub-0001", "demo-rp");

		assertThat(repository.findBySid("SID-1")).hasSize(1);
	}

	@Test
	void consumeForLogoutReturnsEveryClientOfThatSession() {
		service.register("SID-1", "user-sub-0001", "demo-rp");
		service.register("SID-1", "user-sub-0001", "other-rp");

		LogoutTargets targets = service.consumeForLogout("SID-1");

		assertThat(targets.sub()).isEqualTo("user-sub-0001");
		assertThat(targets.clientIds()).containsExactlyInAnyOrder("demo-rp", "other-rp");
	}

	// 세션은 로그아웃 시점에 끝난다. 발송 성공 여부와 무관하게 행을 지운다 —
	// 남겨두면 다음 로그아웃에서 이미 끝난 세션으로 다시 보낸다.
	@Test
	void consumeForLogoutDeletesTheRows() {
		service.register("SID-1", "user-sub-0001", "demo-rp");

		service.consumeForLogout("SID-1");

		assertThat(repository.findBySid("SID-1")).isEmpty();
	}

	@Test
	void consumeForLogoutDoesNotTouchOtherSessions() {
		service.register("SID-1", "user-sub-0001", "demo-rp");
		service.register("SID-2", "user-sub-0002", "demo-rp");

		service.consumeForLogout("SID-1");

		assertThat(repository.findBySid("SID-2")).hasSize(1);
	}

	@Test
	void unknownSessionYieldsEmptyTargets() {
		LogoutTargets targets = service.consumeForLogout("SID-NONE");

		assertThat(targets.sub()).isNull();
		assertThat(targets.clientIds()).isEmpty();
	}
}
```

- [ ] **Step 4: 실패 확인**

Run (session 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks`
Expected: FAIL — 클래스가 없어 컴파일 실패

- [ ] **Step 5: 진입점과 엔티티**

`SessionApplication.java`:

```java
package dev.starryeye.session;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SessionApplication {

	/**
	 * OP 세션과 RP 의 대응을 소유하고, 로그아웃 시 각 RP 에게 logout token 을 보낸다.
	 *      (OIDC Back-Channel Logout 1.0)
	 */

	public static void main(String[] args) {
		SpringApplication.run(SessionApplication.class, args);
	}
}
```

`jpa/OidcSessionEntity.java`:

```java
package dev.starryeye.session.jpa;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@Table(name = "oidc_sessions", uniqueConstraints =
		@UniqueConstraint(name = "uk_sid_client", columnNames = {"sid", "client_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OidcSessionEntity {

	/**
	 * "OP 세션 sid 에서 client_id 가 세션을 갖고 있다" 는 사실 하나를 담는다.
	 *      한 sid 에 RP 수만큼 행이 생긴다.
	 *
	 * 주의. (sid, client_id) 가 unique 다. 같은 RP 가 여러 번 code 를 교환해도 행이 늘면 안 된다.
	 *      늘어나면 로그아웃 때 같은 RP 로 logout token 을 여러 번 보내게 된다.
	 */

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 64)
	private String sid;

	@Column(nullable = false, length = 64)
	private String sub;

	@Column(name = "client_id", nullable = false, length = 100)
	private String clientId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Builder
	private OidcSessionEntity(String sid, String sub, String clientId, Instant createdAt) {
		this.sid = sid;
		this.sub = sub;
		this.clientId = clientId;
		this.createdAt = createdAt;
	}
}
```

`jpa/OidcSessionEntityRepository.java`:

```java
package dev.starryeye.session.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OidcSessionEntityRepository extends JpaRepository<OidcSessionEntity, Long> {

	List<OidcSessionEntity> findBySid(String sid);

	boolean existsBySidAndClientId(String sid, String clientId);

	void deleteBySid(String sid);
}
```

- [ ] **Step 6: LogoutTargets 와 SessionService**

`LogoutTargets.java`:

```java
package dev.starryeye.session;

import java.util.List;

public record LogoutTargets(String sub, List<String> clientIds) {
}
```

`SessionService.java`:

```java
package dev.starryeye.session;

import dev.starryeye.session.jpa.OidcSessionEntity;
import dev.starryeye.session.jpa.OidcSessionEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionService {

	/**
	 * OP 세션 레지스트리를 소유한다. 등록은 token 이, 로그아웃 통지는 auth 가 호출한다.
	 *
	 * 주의. consumeForLogout 은 조회와 삭제를 한 번에 한다. 세션은 로그아웃 시점에 끝나므로,
	 *      발송 성공 여부와 무관하게 행을 남기지 않는다. 남기면 다음 로그아웃에서 이미 끝난 세션으로 다시 보낸다.
	 */

	private final OidcSessionEntityRepository repository;

	@Transactional
	public void register(String sid, String sub, String clientId) {
		if (repository.existsBySidAndClientId(sid, clientId)) {
			return;
		}
		repository.save(OidcSessionEntity.builder()
				.sid(sid)
				.sub(sub)
				.clientId(clientId)
				.createdAt(Instant.now())
				.build());
	}

	@Transactional
	public LogoutTargets consumeForLogout(String sid) {
		List<OidcSessionEntity> sessions = repository.findBySid(sid);
		if (sessions.isEmpty()) {
			return new LogoutTargets(null, List.of());
		}
		List<String> clientIds = sessions.stream().map(OidcSessionEntity::getClientId).toList();
		String sub = sessions.get(0).getSub();
		repository.deleteBySid(sid);
		return new LogoutTargets(sub, clientIds);
	}
}
```

`dto/RegisterSessionRequest.java`:

```java
package dev.starryeye.session.dto;

public record RegisterSessionRequest(String sid, String sub, String clientId) {
}
```

- [ ] **Step 7: 테스트 통과 확인**

Run (session 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks`
Expected: PASS, 6 tests

- [ ] **Step 8: 뮤테이션 확인**

`SessionService.consumeForLogout` 의 `repository.deleteBySid(sid)` 를 지우고 테스트를 돌린다.
Expected: `consumeForLogoutDeletesTheRows` FAIL
확인 후 되돌리고 `git diff` 로 원복을 증명한다.

- [ ] **Step 9: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/session
git commit -m "$(cat <<'EOF'
microservice: add the session service with an OP session registry

sid 하나에 RP 수만큼 행을 두고, 로그아웃 시 조회와 삭제를 한 번에 한다.
(sid, client_id) unique 로 같은 RP 의 중복 교환이 행을 늘리지 않게 한다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 7: session — logout token 생성과 비동기 발송

**Files:**
- Create: `microservice/session/src/main/java/dev/starryeye/session/LogoutTokenFactory.java`
- Create: `microservice/session/src/main/java/dev/starryeye/session/LogoutTokenSender.java`
- Create: `microservice/session/src/main/java/dev/starryeye/session/SessionController.java`
- Create: `microservice/session/src/main/java/dev/starryeye/session/client/ClientRegistryClient.java`
- Create: `microservice/session/src/main/java/dev/starryeye/session/client/ClientInfo.java`
- Create: `microservice/session/src/main/java/dev/starryeye/session/client/SigningClient.java`
- Create: `microservice/session/src/main/java/dev/starryeye/session/config/RestClientConfig.java`
- Create: `microservice/session/src/main/java/dev/starryeye/session/config/AsyncConfig.java`
- Create: `microservice/session/src/main/java/dev/starryeye/session/dto/LogoutRequest.java`
- Test: `microservice/session/src/test/java/dev/starryeye/session/LogoutTokenFactoryTest.java`
- Test: `microservice/session/src/test/java/dev/starryeye/session/SessionControllerTest.java`

**Interfaces:**
- Consumes: `SessionService.consumeForLogout(String) → LogoutTargets` (Task 6)
- Consumes: client-registry `GET /internal/clients/{clientId}` 가 `backchannelLogoutUri` 를 준다 (Task 1)
- Consumes: signing `POST /internal/sign` 이 `{claims, typ}` 를 받는다 (Task 2)
- Produces: `LogoutTokenFactory.create(String sid, String sub, String clientId) → Map<String,Object>`
- Produces: `POST /internal/sessions`, `POST /internal/sessions/logout`

- [ ] **Step 1: 실패하는 claim 계약 테스트 작성**

`LogoutTokenFactoryTest.java`. **claim 을 하나씩 격리해 단언한다** — 각각이 RP 의 독립적인 거부 사유다.

```java
package dev.starryeye.session;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogoutTokenFactoryTest {

	private static final String BACKCHANNEL_LOGOUT_EVENT =
			"http://schemas.openid.net/event/backchannel-logout";

	private final LogoutTokenFactory factory = new LogoutTokenFactory("http://localhost:9000");

	private Map<String, Object> create() {
		return factory.create("SID-ABC", "user-sub-0001", "demo-rp");
	}

	// RP 는 자기가 discovery 로 알아낸 issuer 와 문자열 정확 일치로 대조한다.
	@Test
	void issuerMatchesTheAdvertisedIssuer() {
		assertThat(create()).containsEntry("iss", "http://localhost:9000");
	}

	// aud 에 그 RP 의 client_id 가 없으면 거부된다.
	@Test
	void audienceIsTheTargetClient() {
		assertThat(create()).containsEntry("aud", "demo-rp");
	}

	@Test
	void subjectAndSessionAreBothPresent() {
		assertThat(create()).containsEntry("sub", "user-sub-0001").containsEntry("sid", "SID-ABC");
	}

	@Test
	void issuedAtIsPresent() {
		assertThat(create()).containsKey("iat");
	}

	// jti 는 RP 가 재생을 판정하는 근거다. 두 번 만들면 서로 달라야 한다.
	@Test
	void jtiIsPresentAndUnique() {
		assertThat(create().get("jti")).isNotNull().isNotEqualTo(create().get("jti"));
	}

	// events 에 back-channel logout 키가 없으면 RP 는 이것을 로그아웃 사건으로 인정하지 않는다.
	@Test
	@SuppressWarnings("unchecked")
	void eventsContainsTheBackchannelLogoutKey() {
		Map<String, Object> events = (Map<String, Object>) create().get("events");

		assertThat(events).containsKey(BACKCHANNEL_LOGOUT_EVENT);
	}

	// nonce 가 있으면 RP 가 거부한다. logout token 이 id token 검증 경로로 흘러들어가는 것을 막는 규칙이다.
	@Test
	void nonceIsAbsent() {
		assertThat(create()).doesNotContainKey("nonce");
	}

	// exp 를 싣지 않는다. 검증기가 요구하지 않으며, exp 없는 JWT 는 access token 검증도 통과하지 못한다.
	@Test
	void expirationIsAbsent() {
		assertThat(create()).doesNotContainKey("exp");
	}
}
```

- [ ] **Step 2: 실패 확인**

Run (session 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks --tests '*LogoutTokenFactoryTest*'`
Expected: FAIL — `LogoutTokenFactory` 가 없어 컴파일 실패

- [ ] **Step 3: LogoutTokenFactory 구현**

```java
package dev.starryeye.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class LogoutTokenFactory {

	/**
	 * logout token 의 claim 을 구성한다. (OIDC Back-Channel Logout 1.0 2.4)
	 *      서명은 signing 이 하고, 이 클래스는 claim 계약만 책임진다.
	 *
	 * 주의. nonce 를 싣지 않는다. 스펙이 금지하며 RP 가 거부한다. nonce 가 있으면 이 토큰이 id token 검증
	 *      경로에서 통과할 여지가 생겨 두 토큰 타입이 서로 통하게 된다.
	 *
	 * 주의. exp 를 싣지 않는다. 검증기가 요구하지 않고, exp 없는 JWT 는 access token 검증도 통과하지 못한다.
	 *
	 * 주의. iss 는 RP 가 discovery 로 알아낸 issuer 와 문자열 정확 일치로 대조한다. 포트 하나만 어긋나도
	 *      logout token 이 통째로 거부된다.
	 */

	static final String BACKCHANNEL_LOGOUT_EVENT = "http://schemas.openid.net/event/backchannel-logout";
	static final String LOGOUT_TOKEN_TYP = "logout+jwt";

	private final String issuer;

	public LogoutTokenFactory(@Value("${my.issuer}") String issuer) {
		this.issuer = issuer;
	}

	public Map<String, Object> create(String sid, String sub, String clientId) {
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("iss", issuer);
		claims.put("sub", sub);
		claims.put("aud", clientId);
		claims.put("iat", Instant.now().getEpochSecond());
		claims.put("jti", UUID.randomUUID().toString());
		claims.put("sid", sid);
		claims.put("events", Map.of(BACKCHANNEL_LOGOUT_EVENT, Map.of()));
		return claims;
	}
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run (session 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks --tests '*LogoutTokenFactoryTest*'`
Expected: PASS, 8 tests

- [ ] **Step 5: 외부 호출 클라이언트**

`client/ClientInfo.java`:

```java
package dev.starryeye.session.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientInfo(String clientId, String backchannelLogoutUri) {
}
```

`config/RestClientConfig.java`:

```java
package dev.starryeye.session.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

	/**
	 * 내부 호출용 RestClient. 타임아웃을 두어 한 RP 가 느릴 때 발송 스레드가 묶이지 않게 한다.
	 */

	@Bean
	public RestClient.Builder restClientBuilder() {
		ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
				.withConnectTimeout(Duration.ofSeconds(2))
				.withReadTimeout(Duration.ofSeconds(2));
		return RestClient.builder().requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
	}
}
```

`client/ClientRegistryClient.java`:

```java
package dev.starryeye.session.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ClientRegistryClient {

	private final RestClient restClient;

	public ClientRegistryClient(RestClient.Builder builder,
			@Value("${my.client-registry-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public ClientInfo getClient(String clientId) {
		return restClient.get().uri("/internal/clients/{clientId}", clientId).retrieve().body(ClientInfo.class);
	}
}
```

`client/SigningClient.java`:

```java
package dev.starryeye.session.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class SigningClient {

	private final RestClient restClient;

	public SigningClient(RestClient.Builder builder, @Value("${my.signing-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public String sign(Map<String, Object> claims, String typ) {
		Map<String, Object> body = Map.of("claims", claims, "typ", typ);
		Map<?, ?> response = restClient.post().uri("/internal/sign").body(body).retrieve().body(Map.class);
		return (String) response.get("jwt");
	}
}
```

- [ ] **Step 6: 비동기 설정과 발송기**

`config/AsyncConfig.java`:

```java
package dev.starryeye.session.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {

	/**
	 * logout token 발송을 사용자 응답 경로에서 떼어낸다.
	 *
	 * 주의. 로그아웃 한 번에 RP 수만큼 POST 가 나간다. 동기로 두면 느린 RP 하나가 사용자의 로그아웃을 붙잡는다.
	 */
}
```

`LogoutTokenSender.java`:

```java
package dev.starryeye.session;

import dev.starryeye.session.client.ClientInfo;
import dev.starryeye.session.client.ClientRegistryClient;
import dev.starryeye.session.client.SigningClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogoutTokenSender {

	/**
	 * 각 RP 의 backchannel_logout_uri 로 logout token 을 form POST 한다. (Back-Channel Logout 1.0 2.5)
	 *
	 * 주의. best-effort 다. 실패는 로그만 남기고 재시도하지 않는다. 세션은 이미 끝났으므로 발송 실패가
	 *      로그아웃을 되돌리지는 않는다 — 다만 그 RP 의 세션은 살아남는다.
	 *
	 * 주의. 한 RP 의 실패가 나머지 발송을 막지 않도록 client 단위로 예외를 가둔다.
	 */

	private final LogoutTokenDelivery delivery;

	@Async
	public void send(String sid, String sub, List<String> clientIds) {
		for (String clientId : clientIds) {
			try {
				delivery.deliver(sid, sub, clientId);
			} catch (Exception e) {
				log.warn("logout token 발송 실패. sid={} clientId={}", sid, clientId, e);
			}
		}
	}
}
```

`LogoutTokenDelivery.java` — RP 하나에 대한 발송이다. `LogoutTokenSender` 의 중첩 클래스로 두지 않는다. Lombok 의 `@Slf4j` 는 클래스마다 `log` 를 만들므로 중첩 클래스가 바깥의 `log` 를 쓰면 컴파일되지 않는다.

```java
package dev.starryeye.session;

import dev.starryeye.session.client.ClientInfo;
import dev.starryeye.session.client.ClientRegistryClient;
import dev.starryeye.session.client.SigningClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogoutTokenDelivery {

	/**
	 * RP 한 곳에 logout token 을 form POST 한다. (Back-Channel Logout 1.0 2.5)
	 *
	 * 주의. backchannel_logout_uri 가 없는 client 는 통지 대상이 아니다. 사용자 세션이 없는
	 *      client_credentials 전용 client 가 그런 경우다.
	 */

	private static final String LOGOUT_TOKEN_PARAMETER = "logout_token";

	private final ClientRegistryClient clientRegistryClient;
	private final SigningClient signingClient;
	private final LogoutTokenFactory logoutTokenFactory;
	private final RestClient.Builder restClientBuilder;

	public void deliver(String sid, String sub, String clientId) {
		ClientInfo client = clientRegistryClient.getClient(clientId);
		if (!StringUtils.hasText(client.backchannelLogoutUri())) {
			log.debug("backchannel_logout_uri 가 없어 건너뛴다. clientId={}", clientId);
			return;
		}

		String logoutToken = signingClient.sign(
				logoutTokenFactory.create(sid, sub, clientId), LogoutTokenFactory.LOGOUT_TOKEN_TYP);

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add(LOGOUT_TOKEN_PARAMETER, logoutToken);

		restClientBuilder.build().post()
				.uri(client.backchannelLogoutUri())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.toBodilessEntity();

		log.info("logout token 발송 완료. sid={} clientId={}", sid, clientId);
	}
}
```

`LogoutTokenSender` 의 import 에서 `MediaType`·`MultiValueMap`·`LinkedMultiValueMap`·`RestClient`·`StringUtils`·`ClientInfo`·`ClientRegistryClient`·`SigningClient` 는 빼고, `List` 와 `@Async`·`@Slf4j`·`@Component`·`@RequiredArgsConstructor` 만 남긴다.

- [ ] **Step 7: 컨트롤러**

`dto/LogoutRequest.java`:

```java
package dev.starryeye.session.dto;

public record LogoutRequest(String sid) {
}
```

`SessionController.java`:

```java
package dev.starryeye.session;

import dev.starryeye.session.dto.LogoutRequest;
import dev.starryeye.session.dto.RegisterSessionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SessionController {

	/**
	 * OP 세션 레지스트리의 내부 API. token 이 등록하고 auth 가 로그아웃을 통지한다.
	 *
	 * 주의. 로그아웃은 즉시 200 을 돌려주고 발송은 비동기다. 사용자의 로그아웃 응답이 RP 들의 응답 속도에
	 *      묶이면 안 된다.
	 */

	private final SessionService sessionService;
	private final LogoutTokenSender logoutTokenSender;

	@PostMapping("/internal/sessions")
	public ResponseEntity<Void> register(@RequestBody RegisterSessionRequest request) {
		sessionService.register(request.sid(), request.sub(), request.clientId());
		return ResponseEntity.ok().build();
	}

	@PostMapping("/internal/sessions/logout")
	public ResponseEntity<Void> logout(@RequestBody LogoutRequest request) {
		LogoutTargets targets = sessionService.consumeForLogout(request.sid());
		if (!targets.clientIds().isEmpty()) {
			logoutTokenSender.send(request.sid(), targets.sub(), targets.clientIds());
		}
		return ResponseEntity.ok().build();
	}
}
```

- [ ] **Step 8: 컨트롤러 테스트 작성**

`SessionControllerTest.java`:

```java
package dev.starryeye.session;

import dev.starryeye.session.jpa.OidcSessionEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SessionControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired OidcSessionEntityRepository repository;
	@Autowired SessionService sessionService;
	@MockitoBean LogoutTokenSender logoutTokenSender;

	@BeforeEach
	void clean() {
		repository.deleteAll();
		reset(logoutTokenSender);
	}

	@Test
	void registerStoresTheSession() throws Exception {
		mockMvc.perform(post("/internal/sessions").contentType(MediaType.APPLICATION_JSON)
						.content("{\"sid\":\"SID-1\",\"sub\":\"user-sub-0001\",\"clientId\":\"demo-rp\"}"))
				.andExpect(status().isOk());

		assertThat(repository.findBySid("SID-1")).hasSize(1);
	}

	@Test
	void logoutDispatchesToEveryClientOfThatSession() throws Exception {
		sessionService.register("SID-1", "user-sub-0001", "demo-rp");
		sessionService.register("SID-1", "user-sub-0001", "other-rp");

		mockMvc.perform(post("/internal/sessions/logout").contentType(MediaType.APPLICATION_JSON)
						.content("{\"sid\":\"SID-1\"}"))
				.andExpect(status().isOk());

		verify(logoutTokenSender).send(eq("SID-1"), eq("user-sub-0001"),
				argThat(ids -> ids.containsAll(List.of("demo-rp", "other-rp")) && ids.size() == 2));
		assertThat(repository.findBySid("SID-1")).isEmpty();
	}

	// 세션이 없어도 200 이다. 이미 로그아웃한 사용자가 다시 로그아웃하는 것은 오류가 아니다.
	@Test
	void logoutOfUnknownSessionSucceedsWithoutDispatch() throws Exception {
		mockMvc.perform(post("/internal/sessions/logout").contentType(MediaType.APPLICATION_JSON)
						.content("{\"sid\":\"SID-NONE\"}"))
				.andExpect(status().isOk());

		verify(logoutTokenSender, never()).send(any(), any(), any());
	}
}
```

- [ ] **Step 9: 전체 테스트 실행**

Run (session 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks`
Expected: PASS, 17 tests (Task 6 의 6 + factory 8 + controller 3)

- [ ] **Step 10: 뮤테이션 확인**

`LogoutTokenFactory.create` 에 `claims.put("nonce", "x");` 를 추가하고 테스트를 돌린다.
Expected: `nonceIsAbsent` FAIL
확인 후 되돌리고 `git diff` 로 원복을 증명한다.

- [ ] **Step 11: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/session
git commit -m "$(cat <<'EOF'
microservice: build and dispatch logout tokens

claim 계약은 LogoutTokenFactory 한 곳에 두고 claim 별로 테스트한다 — 각각이
RP 의 독립적인 거부 사유다. 발송은 비동기 best-effort 이고 한 RP 의 실패가
나머지를 막지 않는다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 8: token — session 등록 (fail-closed)

**Files:**
- Create: `microservice/token/src/main/java/dev/starryeye/token/client/SessionClient.java`
- Modify: `microservice/token/src/main/java/dev/starryeye/token/TokenEndpointController.java`
- Modify: `microservice/token/src/main/resources/application.yml`
- Test: `microservice/token/src/test/java/dev/starryeye/token/TokenEndpointControllerTest.java` (수정)

**Interfaces:**
- Consumes: session `POST /internal/sessions` (Task 7)
- Produces: `SessionClient.register(String sid, String sub, String clientId)` — 실패 시 예외를 던진다

- [ ] **Step 1: 실패하는 테스트 작성**

`TokenEndpointControllerTest` 에 추가한다.

```java
	// RP 세션은 id token 을 내주는 순간 선다. 그 사실을 등록하지 않으면 그 RP 는 로그아웃 통지를 받지 못한다.
	@Test
	void idTokenIssuanceRegistersTheRpSession() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(codeStore.consume("code123")).thenReturn(Optional.of(
				new AuthorizationCodeData("my-client", "http://127.0.0.1:8080/callback", "openid", "user-sub-0001",
						"E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", null, 1700000000L, "SID-ABC")));
		when(signingClient.sign(any(), any())).thenReturn("signed-access-token");
		when(idTokenIssuer.issue(any(), any(), any(), any(), anyLong(), any(), any())).thenReturn("signed-id-token");

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "authorization_code")
						.param("code", "code123")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
				.andExpect(status().isOk());

		verify(sessionClient).register("SID-ABC", "user-sub-0001", "my-client");
	}

	// openid 가 없으면 id token 이 없고 RP 세션도 서지 않는다. 등록할 것이 없다.
	@Test
	void nonOpenidExchangeDoesNotRegisterASession() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(codeStore.consume("code123")).thenReturn(Optional.of(
				new AuthorizationCodeData("my-client", "http://127.0.0.1:8080/callback", "profile", "user-sub-0001",
						"E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", null, 1700000000L, "SID-ABC")));
		when(signingClient.sign(any(), any())).thenReturn("signed-access-token");

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "authorization_code")
						.param("code", "code123")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
				.andExpect(status().isOk());

		verify(sessionClient, never()).register(any(), any(), any());
	}

	// 등록 실패를 삼키면 그 RP 는 영원히 로그아웃 통지를 못 받고, 그 사실을 알 방법도 없다.
	// discovery 가 backchannel_logout_supported: true 를 광고하는 이상 조용히 약속을 깨면 안 된다.
	@Test
	void sessionRegistrationFailureFailsTheExchange() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(codeStore.consume("code123")).thenReturn(Optional.of(
				new AuthorizationCodeData("my-client", "http://127.0.0.1:8080/callback", "openid", "user-sub-0001",
						"E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", null, 1700000000L, "SID-ABC")));
		when(signingClient.sign(any(), any())).thenReturn("signed-access-token");
		when(idTokenIssuer.issue(any(), any(), any(), any(), anyLong(), any(), any())).thenReturn("signed-id-token");
		doThrow(new RuntimeException("session down")).when(sessionClient).register(any(), any(), any());

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "authorization_code")
						.param("code", "code123")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("server_error"));
	}
```

`@MockitoBean SessionClient sessionClient;` 를 필드에 추가한다.

- [ ] **Step 2: 실패 확인**

Run (token 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks --tests '*TokenEndpointControllerTest*'`
Expected: FAIL — `SessionClient` 가 없어 컴파일 실패

- [ ] **Step 3: SessionClient 구현**

```java
package dev.starryeye.token.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class SessionClient {

	/**
	 * OP 세션 레지스트리에 "이 RP 가 이 세션을 갖는다" 를 등록한다.
	 *
	 * 주의. 실패를 삼키지 않는다. 등록이 안 되면 그 RP 는 영원히 로그아웃 통지를 받지 못하고, RP 는 그 사실을
	 *      알 방법이 없다. discovery 가 backchannel_logout_supported 를 광고하는 이상 조용히 약속을 깨면 안 된다.
	 */

	private final RestClient restClient;

	public SessionClient(RestClient.Builder builder, @Value("${my.session-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public void register(String sid, String sub, String clientId) {
		restClient.post().uri("/internal/sessions")
				.body(Map.of("sid", sid, "sub", sub, "clientId", clientId))
				.retrieve()
				.toBodilessEntity();
	}
}
```

`token/src/main/resources/application.yml` 의 `my` 아래에 추가한다.

```yaml
  session-base-url: http://localhost:8088
```

- [ ] **Step 4: 등록 호출을 배선한다**

`TokenEndpointController` 의 authorization_code 경로에서 id token 을 만든 직후에 넣는다.

```java
		String idToken = null;
		if (grantedScopes.contains("openid")) {
			idToken = idTokenIssuer.issue(data.sub(), client.clientId(), data.scope(),
					data.nonce(), data.authTime(), jwt, data.sid());
			// RP 세션은 id token 을 내주는 이 순간 선다. sid 가 없으면(구버전 code 레코드) 등록할 것이 없다.
			if (StringUtils.hasText(data.sid())) {
				sessionClient.register(data.sid(), data.sub(), client.clientId());
			}
		}
```

(기존 코드의 id token 발급 조건과 변수명은 파일을 읽고 맞춘다. `sessionClient` 필드를 추가한다.)

- [ ] **Step 5: 테스트 통과 확인**

Run (token 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks`
Expected: PASS

- [ ] **Step 6: 뮤테이션 확인**

`sessionClient.register(...)` 호출을 `try { ... } catch (Exception ignored) {}` 로 감싸고 테스트를 돌린다.
Expected: `sessionRegistrationFailureFailsTheExchange` FAIL
확인 후 되돌리고 `git diff` 로 원복을 증명한다.

- [ ] **Step 7: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/token/src
git commit -m "$(cat <<'EOF'
microservice: register the RP session when the id token is issued

RP 세션은 id token 을 내주는 순간 서므로 그 자리에서 등록한다. 등록 실패는
삼키지 않는다 — 삼키면 그 RP 가 영원히 로그아웃 통지를 못 받고 알 방법도 없다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 9: auth — `end_session_endpoint`

**Files:**
- Create: `microservice/auth/src/main/java/dev/starryeye/auth/IdTokenHintVerifier.java`
- Create: `microservice/auth/src/main/java/dev/starryeye/auth/LogoutController.java`
- Create: `microservice/auth/src/main/java/dev/starryeye/auth/client/SessionClient.java`
- Create: `microservice/auth/src/main/java/dev/starryeye/auth/client/SigningJwksClient.java`
- Modify: `microservice/auth/src/main/java/dev/starryeye/auth/security/SecurityConfig.java`
- Modify: `microservice/auth/src/main/resources/application.yml`
- Modify: `microservice/auth/build.gradle` (nimbus-jose-jwt)
- Test: `microservice/auth/src/test/java/dev/starryeye/auth/IdTokenHintVerifierTest.java` (신규)
- Test: `microservice/auth/src/test/java/dev/starryeye/auth/LogoutControllerTest.java` (신규)

**Interfaces:**
- Consumes: `SessionIdIssuer.currentSid(HttpSession)` (Task 4)
- Consumes: session `POST /internal/sessions/logout` (Task 7)
- Produces: `IdTokenHintVerifier.verify(String idToken) → String` — 검증 후 `aud`(client_id)를 돌려준다. 실패 시 `InvalidHintException`
- Produces: `GET /oauth2/logout`

- [ ] **Step 1: build.gradle 에 nimbus 추가**

`auth/build.gradle` 의 dependencies 에 추가한다. 버전은 token 서비스와 같은 값을 쓴다(파일을 읽어 확인).

```gradle
	implementation 'com.nimbusds:nimbus-jose-jwt:9.47'
```

- [ ] **Step 2: 실패하는 힌트 검증 테스트 작성**

`IdTokenHintVerifierTest.java`. 실제 RSA 키로 토큰을 만들어 검증한다.

```java
package dev.starryeye.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.starryeye.auth.client.SigningJwksClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdTokenHintVerifierTest {

	private static final String ISSUER = "http://localhost:9000";

	private RSAKey key;
	private IdTokenHintVerifier verifier;

	@BeforeEach
	void setUp() throws Exception {
		key = new RSAKeyGenerator(2048).keyID("test-kid").generate();
		SigningJwksClient jwksClient = mock(SigningJwksClient.class);
		when(jwksClient.jwks()).thenReturn(new com.nimbusds.jose.jwk.JWKSet(key.toPublicJWK()).toJSONObject());
		verifier = new IdTokenHintVerifier(jwksClient, ISSUER);
	}

	private String sign(String aud, String issuer, Instant expiration) throws Exception {
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.issuer(issuer)
				.subject("user-sub-0001")
				.audience(aud)
				.expirationTime(Date.from(expiration))
				.build();
		SignedJWT jwt = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}

	@Test
	void returnsAudienceOfAValidHint() throws Exception {
		assertThat(verifier.verify(sign("demo-rp", ISSUER, Instant.now().plusSeconds(300))))
				.isEqualTo("demo-rp");
	}

	// 로그아웃 시점에 id token 이 만료돼 있는 것은 정상이다. 만료를 이유로 거부하면 정당한 로그아웃이 막힌다.
	// 이 저장소에서 만료를 일부러 무시하는 유일한 검증이다.
	@Test
	void acceptsAnExpiredHint() throws Exception {
		assertThat(verifier.verify(sign("demo-rp", ISSUER, Instant.now().minusSeconds(3600))))
				.isEqualTo("demo-rp");
	}

	@Test
	void rejectsAHintSignedByAnotherKey() throws Exception {
		RSAKey attacker = new RSAKeyGenerator(2048).keyID("test-kid").generate();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.issuer(ISSUER).subject("user-sub-0001").audience("demo-rp").build();
		SignedJWT forged = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-kid").build(), claims);
		forged.sign(new RSASSASigner(attacker));

		assertThatThrownBy(() -> verifier.verify(forged.serialize()))
				.isInstanceOf(IdTokenHintVerifier.InvalidHintException.class);
	}

	@Test
	void rejectsAHintFromAnotherIssuer() throws Exception {
		assertThatThrownBy(() -> verifier.verify(sign("demo-rp", "http://evil.example", Instant.now())))
				.isInstanceOf(IdTokenHintVerifier.InvalidHintException.class);
	}

	@Test
	void rejectsGarbage() {
		assertThatThrownBy(() -> verifier.verify("not-a-jwt"))
				.isInstanceOf(IdTokenHintVerifier.InvalidHintException.class);
	}
}
```

- [ ] **Step 3: 실패 확인**

Run (auth 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks --tests '*IdTokenHintVerifierTest*'`
Expected: FAIL — 클래스가 없어 컴파일 실패

- [ ] **Step 4: SigningJwksClient 와 IdTokenHintVerifier 구현**

`client/SigningJwksClient.java`:

```java
package dev.starryeye.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class SigningJwksClient {

	private final RestClient restClient;

	public SigningJwksClient(RestClient.Builder builder, @Value("${my.signing-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public Map<?, ?> jwks() {
		return restClient.get().uri("/oauth2/jwks").retrieve().body(Map.class);
	}
}
```

`IdTokenHintVerifier.java`:

```java
package dev.starryeye.auth;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.starryeye.auth.client.SigningJwksClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IdTokenHintVerifier {

	/**
	 * RP-Initiated Logout 의 id_token_hint 를 검증하고 aud(client_id)를 돌려준다.
	 *
	 * 주의. exp 를 검사하지 않는다. 로그아웃 시점에 id token 이 만료돼 있는 것은 정상이며, 만료를 이유로
	 *      거부하면 정당한 로그아웃이 막힌다. 이 저장소에서 만료를 일부러 무시하는 유일한 검증이다.
	 *
	 * 주의. 힌트는 사용자 식별에 쓰지 않는다. 사용자는 브라우저로 오므로 세션 쿠키로 이미 누구인지 안다.
	 *      힌트는 post_logout_redirect_uri 를 어느 client 기준으로 검증할지 정하는 데만 쓴다.
	 */

	private final SigningJwksClient jwksClient;
	private final String issuer;

	public IdTokenHintVerifier(SigningJwksClient jwksClient, @Value("${my.issuer}") String issuer) {
		this.jwksClient = jwksClient;
		this.issuer = issuer;
	}

	public String verify(String idToken) {
		SignedJWT signedJWT;
		JWTClaimsSet claims;
		try {
			signedJWT = SignedJWT.parse(idToken);
			claims = signedJWT.getJWTClaimsSet();
		} catch (Exception e) {
			throw new InvalidHintException("malformed id_token_hint");
		}

		JWKSet jwkSet;
		try {
			jwkSet = JWKSet.parse(jwksClient.jwks());
		} catch (Exception e) {
			throw new InvalidHintException("jwks unavailable"); // 키를 못 구하면 리다이렉트를 포기한다
		}

		try {
			RSAKey key = (RSAKey) jwkSet.getKeyByKeyId(signedJWT.getHeader().getKeyID());
			if (key == null || !signedJWT.verify(new RSASSAVerifier(key.toRSAPublicKey()))) {
				throw new InvalidHintException("signature verification failed");
			}
		} catch (InvalidHintException e) {
			throw e;
		} catch (Exception e) {
			throw new InvalidHintException("signature verification failed");
		}

		if (!issuer.equals(claims.getIssuer())) {
			throw new InvalidHintException("issuer mismatch");
		}

		List<String> audience = claims.getAudience();
		if (audience == null || audience.isEmpty()) {
			throw new InvalidHintException("missing aud");
		}
		return audience.get(0);
	}

	public static class InvalidHintException extends RuntimeException {
		public InvalidHintException(String message) {
			super(message);
		}
	}
}
```

- [ ] **Step 5: 실패하는 컨트롤러 테스트 작성**

`LogoutControllerTest.java`:

```java
package dev.starryeye.auth;

import dev.starryeye.auth.client.ClientInfo;
import dev.starryeye.auth.client.ClientRegistryClient;
import dev.starryeye.auth.client.SessionClient;
import dev.starryeye.auth.security.SessionIdIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LogoutControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired SessionIdIssuer sessionIdIssuer;
	@MockitoBean IdTokenHintVerifier hintVerifier;
	@MockitoBean ClientRegistryClient clientRegistryClient;
	@MockitoBean SessionClient sessionClient;

	private MockHttpSession loggedInSession() {
		MockHttpSession session = new MockHttpSession();
		sessionIdIssuer.issue(session);
		return session;
	}

	private ClientInfo demoRp() {
		return new ClientInfo("demo-rp", List.of(), List.of("openid"), List.of("authorization_code"),
				List.of("http://localhost:8095/"));
	}

	@Test
	@WithMockUser("user-sub-0001")
	void redirectsToRegisteredPostLogoutUriAndNotifiesSession() throws Exception {
		when(hintVerifier.verify("hint")).thenReturn("demo-rp");
		when(clientRegistryClient.getClient("demo-rp")).thenReturn(demoRp());

		mockMvc.perform(get("/oauth2/logout").session(loggedInSession())
						.param("id_token_hint", "hint")
						.param("post_logout_redirect_uri", "http://localhost:8095/")
						.param("state", "xyz"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("http://localhost:8095/?state=xyz"));

		verify(sessionClient).logout(any());
	}

	// 미등록 주소로는 돌려보내지 않는다. authorize 의 redirect_uri 정확 일치와 같은 원칙(open redirect 방지).
	// 그래도 로그아웃은 수행한다 — 세션을 살려두는 것이 더 위험하다.
	@Test
	@WithMockUser("user-sub-0001")
	void doesNotRedirectToUnregisteredUriButStillLogsOut() throws Exception {
		when(hintVerifier.verify("hint")).thenReturn("demo-rp");
		when(clientRegistryClient.getClient("demo-rp")).thenReturn(demoRp());

		mockMvc.perform(get("/oauth2/logout").session(loggedInSession())
						.param("id_token_hint", "hint")
						.param("post_logout_redirect_uri", "http://evil.example/"))
				.andExpect(status().isOk());

		verify(sessionClient).logout(any());
	}

	// 힌트 검증이 실패해도 로그아웃은 한다. 검증은 어디로 돌려보낼지를 정할 때만 필요하다.
	@Test
	@WithMockUser("user-sub-0001")
	void logsOutEvenWhenHintIsInvalid() throws Exception {
		when(hintVerifier.verify(any()))
				.thenThrow(new IdTokenHintVerifier.InvalidHintException("signature verification failed"));

		mockMvc.perform(get("/oauth2/logout").session(loggedInSession())
						.param("id_token_hint", "forged")
						.param("post_logout_redirect_uri", "http://localhost:8095/"))
				.andExpect(status().isOk());

		verify(sessionClient).logout(any());
	}

	@Test
	@WithMockUser("user-sub-0001")
	void logsOutWithoutHint() throws Exception {
		mockMvc.perform(get("/oauth2/logout").session(loggedInSession()))
				.andExpect(status().isOk());

		verify(sessionClient).logout(any());
		verify(hintVerifier, never()).verify(any());
	}
}
```

- [ ] **Step 6: SessionClient 와 LogoutController 구현**

`client/SessionClient.java`:

```java
package dev.starryeye.auth.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class SessionClient {

	/**
	 * session 서비스에 로그아웃을 통지한다. 발송은 그쪽에서 비동기로 한다.
	 *
	 * 주의. 통지 실패가 로그아웃을 막으면 안 된다. 세션을 끊는 것이 우선이므로 예외를 로그로 흡수한다.
	 *      이 슬라이스에서 유일하게 fail-open 인 지점이다.
	 */

	private final RestClient restClient;

	public SessionClient(RestClient.Builder builder, @Value("${my.session-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public void logout(String sid) {
		try {
			restClient.post().uri("/internal/sessions/logout")
					.body(Map.of("sid", sid))
					.retrieve()
					.toBodilessEntity();
		} catch (Exception e) {
			log.warn("로그아웃 통지 실패. RP 세션이 살아남는다. sid={}", sid, e);
		}
	}
}
```

`LogoutController.java`:

```java
package dev.starryeye.auth;

import dev.starryeye.auth.client.ClientInfo;
import dev.starryeye.auth.client.ClientRegistryClient;
import dev.starryeye.auth.client.SessionClient;
import dev.starryeye.auth.security.SessionIdIssuer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequiredArgsConstructor
public class LogoutController {

	/**
	 * RP-Initiated Logout 1.0 의 end_session_endpoint 다.
	 *
	 * 주의. 로그아웃 자체는 어떤 경우에도 수행한다. 세션을 끊는 것은 이 서비스 안에서 끝나는 로컬 작업이라
	 *      외부 의존성이 없다. 검증은 오직 "어디로 돌려보낼지" 를 정할 때만 필요하다. 이 저장소가 다른 곳에서
	 *      지키는 fail-closed 가 여기서는 반대다 — 로그아웃 실패는 세션을 살려두므로 더 위험하다.
	 *
	 * 주의. 미등록 post_logout_redirect_uri 로는 돌려보내지 않는다. authorize 의 redirect_uri 정확 일치와
	 *      같은 원칙이다(open redirect 방지).
	 */

	private final SessionIdIssuer sessionIdIssuer;
	private final SessionClient sessionClient;
	private final IdTokenHintVerifier hintVerifier;
	private final ClientRegistryClient clientRegistryClient;

	@GetMapping("/oauth2/logout")
	public Object logout(
			HttpServletRequest request,
			@RequestParam(value = "id_token_hint", required = false) String idTokenHint,
			@RequestParam(value = "post_logout_redirect_uri", required = false) String postLogoutRedirectUri,
			@RequestParam(value = "state", required = false) String state
	) {
		String redirectTo = resolveRedirect(idTokenHint, postLogoutRedirectUri);

		HttpSession session = request.getSession(false);
		if (session != null) {
			String sid = sessionIdIssuer.currentSid(session);
			if (StringUtils.hasText(sid)) {
				sessionClient.logout(sid);
			}
			session.invalidate();
		}
		SecurityContextHolder.clearContext();

		if (redirectTo == null) {
			return ResponseEntity.ok("logged out");
		}
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(redirectTo);
		if (StringUtils.hasText(state)) {
			builder.queryParam("state", state);
		}
		return new RedirectView(builder.encode().build().toUriString());
	}

	private String resolveRedirect(String idTokenHint, String postLogoutRedirectUri) {
		if (!StringUtils.hasText(idTokenHint) || !StringUtils.hasText(postLogoutRedirectUri)) {
			return null;
		}
		String clientId;
		try {
			clientId = hintVerifier.verify(idTokenHint);
		} catch (IdTokenHintVerifier.InvalidHintException e) {
			return null; // 어느 client 기준으로 검증할지 알 수 없으므로 돌려보내지 않는다
		}
		ClientInfo client;
		try {
			client = clientRegistryClient.getClient(clientId);
		} catch (Exception e) {
			return null;
		}
		return client.postLogoutRedirectUris().contains(postLogoutRedirectUri) ? postLogoutRedirectUri : null;
	}
}
```

- [ ] **Step 7: auth 의 ClientInfo 에 postLogoutRedirectUris 추가 (맨 뒤)**

`auth/src/main/java/dev/starryeye/auth/client/ClientInfo.java` 에 필드를 **맨 뒤에** 추가한다.

```java
		List<String> postLogoutRedirectUris
```

**주의.** `backchannelLogoutUri` 는 추가하지 않는다. auth 는 발송을 하지 않으므로 그 값을 알 필요가 없다 — 슬라이스 4에서 `clientSecretHash` 를 auth 의 시야에서 뺀 것과 같은 원칙이다.

- [ ] **Step 8: SecurityConfig 와 설정**

`SecurityConfig` 의 `authorizeHttpRequests` 에 `/oauth2/logout` 을 추가한다. 미인증 사용자가 로그아웃을 눌러도 오류가 나면 안 된다.

```java
						.requestMatchers("/login", "/error", "/oauth2/logout").permitAll()
```

`csrf` 무시 목록에도 추가하지 않는다 — GET 이므로 불필요하다.

`auth/src/main/resources/application.yml` 의 `my` 아래에 추가한다.

```yaml
  issuer: http://localhost:9000
  session-base-url: http://localhost:8088
```

(`signing-base-url` 이 없으면 `http://localhost:8083` 으로 함께 추가한다.)

- [ ] **Step 9: 전체 테스트 실행**

Run (auth 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks`
Expected: PASS, 31 tests (Task 4 후 22 + hint 5 + logout 4)

- [ ] **Step 10: 뮤테이션 확인**

`LogoutController.resolveRedirect` 의 마지막 줄을 `return postLogoutRedirectUri;` 로 바꾸고 테스트를 돌린다.
Expected: `doesNotRedirectToUnregisteredUriButStillLogsOut` FAIL
확인 후 되돌리고 `git diff` 로 원복을 증명한다.

- [ ] **Step 11: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/auth
git commit -m "$(cat <<'EOF'
microservice: add the end_session_endpoint

RP-Initiated Logout 1.0 을 구현한다. 로그아웃은 어떤 경우에도 수행하고, 검증은
post_logout_redirect_uri 로 돌려보낼지만 정한다. id_token_hint 는 exp 를 보지
않는다 — 로그아웃 시점의 만료는 정상이다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 10: discovery 3항목과 gateway 라우팅

**Files:**
- Modify: `microservice/token/src/main/java/dev/starryeye/token/TokenEndpointController.java`
- Modify: `microservice/gateway/nginx.conf`
- Test: `microservice/token/src/test/java/dev/starryeye/token/TokenEndpointControllerTest.java` (수정)

**Interfaces:**
- Produces: discovery 문서에 `end_session_endpoint`, `backchannel_logout_supported`, `backchannel_logout_session_supported`

- [ ] **Step 1: 실패하는 테스트 작성**

`TokenEndpointControllerTest` 의 `openidConfigurationAdvertisesImplementedCapabilities` 에 세 줄을 추가한다.

```java
				.andExpect(jsonPath("$.end_session_endpoint").value(issuer + "/oauth2/logout"))
				.andExpect(jsonPath("$.backchannel_logout_supported").value(true))
				.andExpect(jsonPath("$.backchannel_logout_session_supported").value(true))
```

- [ ] **Step 2: 실패 확인**

Run (token 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks --tests '*openidConfigurationAdvertisesImplementedCapabilities*'`
Expected: FAIL — 세 필드가 없다

- [ ] **Step 3: discovery 에 추가**

`TokenEndpointController.metadata()` 의 `revocation_endpoint` 아래에 넣는다.

```java
		metadata.put("end_session_endpoint", issuer + "/oauth2/logout");
		// back-channel logout 을 지원하고, logout token 에 sid 를 실어 세션 단위 로그아웃이 가능함을 알린다.
		// RP 는 이 두 값을 보고 자기 세션 관리를 설계하므로, 광고한 이상 등록 실패를 삼키면 안 된다.
		metadata.put("backchannel_logout_supported", true);
		metadata.put("backchannel_logout_session_supported", true);
```

- [ ] **Step 4: gateway 라우팅**

`gateway/nginx.conf` 의 front-channel 블록(`/oauth2/authorize` 옆)에 추가한다.

```nginx
    location /oauth2/logout    { proxy_pass http://auth-upstream;  proxy_set_header X-Forwarded-Host $http_host; }
```

**주의.** session(8088)은 `/internal/**` 만 가지므로 gateway 에 라우팅을 추가하지 않는다. 외부에 노출되면 아무나 남의 세션을 로그아웃시킬 수 있다.

- [ ] **Step 5: 테스트 통과 확인**

Run (token 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks`
Expected: PASS

- [ ] **Step 6: nginx 설정 문법 확인**

```bash
cd oauth-2/authorization-server/practice/microservice/docker-compose
docker compose config >/dev/null && echo "compose OK"
```

- [ ] **Step 7: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/token/src \
        oauth-2/authorization-server/practice/microservice/gateway/nginx.conf
git commit -m "$(cat <<'EOF'
microservice: advertise logout capabilities in discovery

end_session_endpoint 와 back-channel logout 지원 여부를 광고하고, gateway 가
로그아웃 요청을 auth 로 보낸다. session 서비스는 라우팅하지 않는다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 11: demo-rp(8095) — 진짜 Spring Security RP

**Files:**
- Create: `microservice/demo-rp/build.gradle` · `settings.gradle` · `gradlew` · `gradle/wrapper/*` · `.gitignore`
- Create: `microservice/demo-rp/src/main/java/dev/starryeye/demo_rp/DemoRpApplication.java`
- Create: `microservice/demo-rp/src/main/java/dev/starryeye/demo_rp/SecurityConfig.java`
- Create: `microservice/demo-rp/src/main/java/dev/starryeye/demo_rp/HomeController.java`
- Create: `microservice/demo-rp/src/main/resources/application.yml`
- Test: `microservice/demo-rp/src/test/java/dev/starryeye/demo_rp/SecurityConfigTest.java`

**Interfaces:**
- Consumes: discovery `http://localhost:9000/.well-known/openid-configuration` (Task 10)
- Produces: `GET /me` — 인증 필요. 로그인 상태면 200, 아니면 302

- [ ] **Step 1: 스캐폴드**

`signing` 을 복사한 뒤 이름을 바꾼다(JPA 가 없는 가장 가벼운 서비스다).

```bash
cd oauth-2/authorization-server/practice/microservice
cp -R signing demo-rp
rm -rf demo-rp/build demo-rp/.gradle demo-rp/src/main/java demo-rp/src/test/java demo-rp/src/main/resources/*
mkdir -p demo-rp/src/main/java/dev/starryeye/demo_rp demo-rp/src/test/java/dev/starryeye/demo_rp
```

`demo-rp/settings.gradle`:

```gradle
rootProject.name = 'demo-rp'
```

`demo-rp/build.gradle` 의 dependencies 를 아래로 바꾼다.

```gradle
dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-web'
	implementation 'org.springframework.boot:spring-boot-starter-security'
	implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
	compileOnly 'org.projectlombok:lombok'
	annotationProcessor 'org.projectlombok:lombok'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testImplementation 'org.springframework.security:spring-security-test'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

**주의.** `spring-boot-starter-oauth2-client` 는 client 측 스타터다. 금지 대상인 `spring-boot-starter-oauth2-authorization-server`(SAS)와 다르다.

- [ ] **Step 2: application.yml**

```yaml
server:
  port: 8095

spring:
  application:
    name: demo-rp
  security:
    oauth2:
      client:
        provider:
          microservice:
            issuer-uri: http://localhost:9000
        registration:
          microservice:
            client-id: demo-rp
            client-secret: secret
            authorization-grant-type: authorization_code
            scope: openid,profile,email
            redirect-uri: http://localhost:8095/login/oauth2/code/microservice

logging:
  level:
    org.springframework.security: DEBUG
```

**주의.** `redirect-uri` 와 back-channel 수신 경로 `/logout/connect/back-channel/microservice` 는 registrationId(`microservice`)에서 나온다. client-registry 의 seed 값과 반드시 같아야 한다.

- [ ] **Step 3: 진입점과 컨트롤러**

`DemoRpApplication.java`:

```java
package dev.starryeye.demo_rp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoRpApplication {

	/**
	 * 이 인가 서버의 back-channel logout 을 검증하는 RP 다.
	 *      검증자가 우리 코드가 아니라 Spring Security 구현이므로, 스펙을 잘못 읽으면 실제로 실패한다.
	 */

	public static void main(String[] args) {
		SpringApplication.run(DemoRpApplication.class, args);
	}
}
```

`HomeController.java`:

```java
package dev.starryeye.demo_rp;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HomeController {

	/**
	 * 로그인이 필요한 보호 페이지. back-channel logout 의 성공 판정에 쓰인다.
	 *      로그아웃 전에는 200, OP 가 logout token 을 보낸 뒤에는 302(로그인으로)여야 한다.
	 */

	@GetMapping("/me")
	public Map<String, Object> me(@AuthenticationPrincipal OidcUser user) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("sub", user.getSubject());
		body.put("sid", user.getClaimAsString("sid"));
		return body;
	}
}
```

- [ ] **Step 4: SecurityConfig**

```java
package dev.starryeye.demo_rp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	/**
	 * oauth2Login 으로 로그인하고, 두 방향의 로그아웃을 모두 켠다.
	 *      logout()      — RP 가 시작하는 로그아웃. OidcClientInitiatedLogoutSuccessHandler 가
	 *                      discovery 의 end_session_endpoint 로 사용자를 보낸다.
	 *      oidcLogout()  — OP 가 보내는 back-channel logout 을 받는다.
	 *                      수신 경로는 /logout/connect/back-channel/{registrationId} 다.
	 *
	 * 주의. back-channel 수신 경로는 인증을 요구하면 안 된다. OP 가 사용자 세션 없이 서버 대 서버로 POST 한다.
	 */

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http,
			ClientRegistrationRepository clientRegistrationRepository) throws Exception {
		OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler =
				new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
		logoutSuccessHandler.setPostLogoutRedirectUri("http://localhost:8095/");

		http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/logout/connect/back-channel/**").permitAll()
						.anyRequest().authenticated()
				)
				.oauth2Login(Customizer.withDefaults())
				.logout(logout -> logout.logoutSuccessHandler(logoutSuccessHandler))
				.oidcLogout(oidc -> oidc.backChannel(Customizer.withDefaults()))
				.csrf(csrf -> csrf.ignoringRequestMatchers("/logout/connect/back-channel/**"));

		return http.build();
	}
}
```

- [ ] **Step 5: 배선 테스트 작성**

```java
package dev.starryeye.demo_rp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		// discovery 를 타면 인가 서버가 떠 있어야 하므로, 테스트에서는 엔드포인트를 직접 지정한다.
		"spring.security.oauth2.client.provider.microservice.issuer-uri=",
		"spring.security.oauth2.client.provider.microservice.authorization-uri=http://localhost:9000/oauth2/authorize",
		"spring.security.oauth2.client.provider.microservice.token-uri=http://localhost:9000/oauth2/token",
		"spring.security.oauth2.client.provider.microservice.jwk-set-uri=http://localhost:9000/oauth2/jwks",
		"spring.security.oauth2.client.provider.microservice.user-info-uri=http://localhost:9000/userinfo",
		"spring.security.oauth2.client.provider.microservice.user-name-attribute=sub"
})
class SecurityConfigTest {

	@Autowired MockMvc mockMvc;

	// 보호 페이지는 미인증이면 로그인으로 보낸다. e2e 의 로그아웃 성공 판정이 이 동작에 기댄다.
	@Test
	void protectedPageRedirectsWhenNotAuthenticated() throws Exception {
		mockMvc.perform(get("/me")).andExpect(status().is3xxRedirection());
	}

	// back-channel 수신 경로는 인증을 요구하지 않아야 한다. 요구하면 OP 의 POST 가 로그인으로 튕겨
	// 로그아웃이 조용히 실패한다. logout_token 이 없으므로 성공하지는 않지만, 거절 사유가
	// "인증이 없다"(302/401)여서는 안 된다. 그 두 가지가 아님을 단언한다.
	@Test
	void backChannelEndpointIsReachableWithoutAuthentication() throws Exception {
		int status = mockMvc.perform(post("/logout/connect/back-channel/microservice"))
				.andReturn().getResponse().getStatus();

		assertThat(status).isNotEqualTo(302).isNotEqualTo(401);
	}
}
```

**주의.** 이 테스트는 특정 상태 코드를 고정하지 않는다. 우리가 확인하려는 성질은 "인증 때문에 거절되지 않는다" 하나이고, `logout_token` 이 없을 때 Spring Security 가 어떤 4xx 를 내는지는 우리 계약이 아니다. 관측한 값을 그대로 기대값으로 적으면 성질이 아니라 구현을 베끼게 된다.

`assertThat` import 를 추가한다.

```java
import static org.assertj.core.api.Assertions.assertThat;
```

- [ ] **Step 6: 테스트 실행**

Run (demo-rp 디렉터리에서): `./gradlew test --no-daemon --rerun-tasks`
Expected: PASS, 2 tests

`backChannelEndpointIsReachableWithoutAuthentication` 의 기대 상태 코드가 400 이 아니면, **실제 상태 코드를 확인하고 그것이 "인증을 요구하지 않는다"를 증명하는지 판단해 기대값을 고친다.** 302 나 401 이면 배선이 틀린 것이므로 설정을 고친다.

- [ ] **Step 7: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/demo-rp
git commit -m "$(cat <<'EOF'
microservice: add a real Spring Security RP for logout verification

검증자가 우리 코드가 아니라 Spring Security 구현이어야 한다. 직접 만든 스텁은
스펙을 잘못 읽어도 그 오해에 그대로 동의한다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 12: e2e 검증과 문서

**Files:**
- Modify: `microservice/README.md`
- Create: `microservice/http/logout.http`

**Interfaces:**
- Consumes: 앞의 모든 태스크

- [ ] **Step 1: 인프라와 전 서비스 기동**

```bash
cd oauth-2/authorization-server/practice/microservice/docker-compose
docker compose up -d
```

각 서비스를 빌드해 띄운다(10개). Java 는 sdkman 21 을 쓴다.

```bash
J=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn/bin/java
for s in signing user-directory client-registry consent token-state session auth token demo-rp; do
  (cd ../$s && ./gradlew bootJar --no-daemon -x test)
done
for s in signing user-directory client-registry consent token-state session auth token demo-rp; do
  (cd ../$s && $J -jar build/libs/*-SNAPSHOT.jar > /tmp/ms-$s.log 2>&1 &)
done
```

- [ ] **Step 2: 스키마 보정 확인**

`ddl-auto: update` 로 not null 컬럼(`post_logout_redirect_uris`)이 기존 `clients` 행에 추가되면 MySQL 이 빈 문자열로 채운다. 기존 두 client 는 그 값이 맞지만, seed 가 `demo-rp` 를 새로 넣는지 확인한다.

```bash
docker exec -i microservice-mysql mysql -uroot -p1111 microservice_as \
  -e "select client_id, backchannel_logout_uri, post_logout_redirect_uris from clients;"
```

`demo-rp` 행이 없으면 client-registry 로그를 확인하고, 컬럼 추가가 실패했으면 수동 `ALTER` 후 재기동한다. 결과를 **raw 출력으로** 보고서에 남긴다.

- [ ] **Step 3: discovery 확인**

```bash
curl -s http://localhost:9000/.well-known/openid-configuration | python3 -m json.tool
```

`end_session_endpoint`·`backchannel_logout_supported`·`backchannel_logout_session_supported` 를 raw 출력으로 확인한다.

- [ ] **Step 4: 브라우저 흐름으로 로그인**

demo-rp 는 브라우저 흐름이 필요하므로 curl 로 쿠키를 이어가며 수행한다. 쿠키 항아리는 **하나만** 쓴다 — demo-rp 세션 쿠키와 auth 세션 쿠키가 도메인은 같고 포트만 달라 한 파일에 함께 담긴다.

```bash
C=/tmp/rp-cookies.txt; rm -f "$C"

# 1) 보호 페이지 -> 로그인으로 302 (아직 미인증)
curl -s -o /dev/null -w "protected(before login)=%{http_code}\n" -c "$C" http://localhost:8095/me

# 2) demo-rp 의 인가 요청을 끝까지 따라가면 auth 의 로그인 폼에서 멈춘다
curl -s -b "$C" -c "$C" -L http://localhost:8095/oauth2/authorization/microservice -o /tmp/login-form.html
grep -o 'name="_csrf" value="[^"]*"' /tmp/login-form.html

# 3) 폼에서 CSRF 토큰을 뽑아 로그인한다. seed 사용자 자격증명은 user-directory 의 UserSeedInitializer 를 읽어 확인한다.
CSRF=$(grep -o 'name="_csrf" value="[^"]*"' /tmp/login-form.html | sed 's/.*value="//; s/"//')
curl -s -b "$C" -c "$C" -L -X POST http://localhost:9000/login \
     -d "username=<seed 사용자>" -d "password=<seed 비밀번호>" -d "_csrf=$CSRF" \
     -o /tmp/after-login.html -w "after-login=%{http_code}\n"
```

3단계 후에도 로그인 폼이 돌아오면 자격증명이나 CSRF 가 틀린 것이다. `/tmp/ms-auth.log` 를 확인한다.

로그인 후 `GET /me` 가 **200 이고 응답에 `sid` 가 실려 있는지** 확인한다.

```bash
curl -s -b "$C" -w "\nstatus=%{http_code}\n" http://localhost:8095/me
```

Expected: `{"sub":"user-sub-0001","sid":"<22자 base64url>"}` 와 `status=200`. `sid` 를 변수에 담아 둔다 — Step 5·7 에서 대조한다.

- [ ] **Step 5: 레지스트리에 행이 생겼는지 확인**

```bash
docker exec -i microservice-mysql mysql -uroot -p1111 microservice_as \
  -e "select sid, sub, client_id from oidc_sessions;"
```

Expected: `demo-rp` 행 1개. `sid` 가 Step 4 의 값과 같아야 한다.

- [ ] **Step 6: 로그아웃 — 이 슬라이스의 핵심 판정**

**주의.** 판정은 반드시 **OP 자체 로그아웃**으로 한다. RP-initiated 로그아웃(`POST http://localhost:8095/logout`)은 Spring Security 의 `LogoutFilter` 가 **demo-rp 세션을 먼저 로컬에서 무효화**한 뒤 사용자를 OP 로 보낸다. 그 경로로 판정하면 back-channel 이 전혀 동작하지 않아도 보호 페이지가 302 가 되어 **통과처럼 보인다.**

demo-rp 를 건드리지 않고 auth 세션 쿠키만으로 OP 를 직접 로그아웃시킨다. 그러면 demo-rp 세션을 끊을 수 있는 것은 back-channel 통지뿐이다.

```bash
# demo-rp 는 이 요청에 관여하지 않는다. auth 세션 쿠키만 쓴다.
curl -s -i -b "$C" http://localhost:9000/oauth2/logout | head -5

sleep 2   # 비동기 발송 대기

# 핵심 판정: demo-rp 쿠키는 그대로인데 보호 페이지가 302 여야 한다
curl -s -o /dev/null -w "protected(after OP logout)=%{http_code}\n" -b "$C" http://localhost:8095/me
```

Expected: **302**. demo-rp 의 쿠키를 지운 적이 없으므로, 이 302 는 **서버 쪽 세션이 사라졌다**는 뜻이고 그것을 일으킬 수 있는 것은 OP 가 보낸 logout token 뿐이다.

200 이 나오면 `/tmp/ms-session.log` 와 `/tmp/ms-demo-rp.log` 를 확인한다. `iss` 불일치가 가장 흔하다.

이어서 **RP-initiated 경로**를 별도로 확인한다(성공 기준 5번). 앞의 판정과 섞지 않기 위해 Step 4 를 다시 수행해 새 세션으로 시작한다.

```bash
LOGOUT_CSRF=$(grep -o 'name="_csrf" value="[^"]*"' /tmp/after-login.html | sed 's/.*value="//; s/"//')
curl -s -i -b "$C" -c "$C" -X POST http://localhost:8095/logout -d "_csrf=$LOGOUT_CSRF" | head -8
```

`Location` 이 `http://localhost:9000/oauth2/logout?id_token_hint=…&post_logout_redirect_uri=http%3A%2F%2Flocalhost%3A8095%2F` 여야 한다. 그 URL 을 그대로 따라가 auth 가 `post_logout_redirect_uri` 로 302 를 내는지, `state` 를 붙여 보내면 그대로 되돌아오는지 헤더에서 확인한다.

```bash
curl -s -i -b "$C" "http://localhost:9000/oauth2/logout?id_token_hint=<id token>&post_logout_redirect_uri=http://localhost:8095/&state=xyz" | head -5
```

Expected: `302` 와 `Location: http://localhost:8095/?state=xyz`

- [ ] **Step 7: 레지스트리 행이 사라졌는지 확인**

```bash
docker exec -i microservice-mysql mysql -uroot -p1111 microservice_as \
  -e "select count(*) from oidc_sessions;"
```

Expected: 0

- [ ] **Step 8: negative 경로 2건**

미등록 `post_logout_redirect_uri`:

```bash
curl -s -i "http://localhost:9000/oauth2/logout?id_token_hint=<유효한 id token>&post_logout_redirect_uri=http://evil.example/" | head -5
```

Expected: 302 가 아니라 200(자체 완료 페이지). `evil.example` 로 가지 않는다.

위조 `id_token_hint`:

```bash
curl -s -i "http://localhost:9000/oauth2/logout?id_token_hint=aaa.bbb.ccc&post_logout_redirect_uri=http://localhost:8095/" | head -5
```

Expected: 302 가 아니라 200.

- [ ] **Step 9: typ 확인**

access token 과 logout token 의 헤더를 디코드한다.

```bash
echo "<access_token>" | cut -d. -f1 | base64 -d 2>/dev/null | python3 -m json.tool
```

Expected: `"typ": "at+jwt"`. logout token 은 session 로그에서 꺼내 같은 방식으로 `logout+jwt` 를 확인한다.

- [ ] **Step 10: 회귀 확인 (슬라이스 1~4)**

curl 기반 `my-client` 흐름으로 아래를 재확인하고 **raw 출력을 남긴다.**

- code 재사용 → `invalid_grant`
- PKCE 변조 → `invalid_grant`
- refresh 회전 → 새 access/refresh token
- refresh 재사용 탐지 → `invalid_grant` + `oidc_sessions` 가 아닌 `refresh_tokens` 계열 전체 `REVOKED`
- introspection: Basic → 401, `introspect` scope 없는 Bearer → 403, 있는 Bearer → 200
- client_credentials → `article-api` 토큰 발급, `openid` 요청 시 `invalid_scope`

- [ ] **Step 11: README 갱신**

다음을 추가한다.

- 서비스 표에 `session`(8088)·`demo-rp`(8095) 행
- 관통 flow 에 로그아웃 절 — `sid` 관통 경로와 로그아웃 역추적 경로
- 시퀀스 다이어그램 2개 (로그인 시 `sid` 등록 / 로그아웃 시 발송)
- logout token claim 표 (Spring Security 검증기 기준)
- e2e 기록 (성공 기준 11개, **raw 출력 포함**)
- 알려진 한계 4건 추가:
  - auth 세션 자연 만료 시 logout token 미발송
  - 발송에 재시도 없음 (best-effort)
  - `jti` 재생 방지는 RP 몫
  - refresh 로 재발급한 id token 에는 `sid` 가 없다
- 기존 한계 **"access token 과 id token 을 구분할 수 있는 표식이 없다" 항목을 제거**하고, `typ` 으로 닫혔음을 반영
- "설계/계획 문서" 절에 슬라이스 5 설계·계획 링크 2줄
- `custom-oidc-logout` 의 TODO 를 이 AS 로 닫는 법 한 문단

- [ ] **Step 12: http/logout.http 신설**

`end_session_endpoint` 호출 예시와 negative 2건, 그리고 `oidc_sessions` 조회 SQL 을 주석으로 담는다.

- [ ] **Step 13: 정리**

```bash
pkill -f "SNAPSHOT.jar"
cd oauth-2/authorization-server/practice/microservice/docker-compose && docker compose stop
lsof -nP -iTCP:8081,8082,8083,8084,8085,8086,8087,8088,8095,9000 -sTCP:LISTEN
```

Expected: 마지막 명령이 아무것도 출력하지 않는다.

- [ ] **Step 14: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/README.md \
        oauth-2/authorization-server/practice/microservice/http/logout.http
git commit -m "$(cat <<'EOF'
microservice: document slice 5 and record the logout e2e

demo-rp 의 보호 페이지가 로그아웃 후 302 로 바뀌는 것을 성공 기준으로 삼았다.
logout token 을 보냈다가 아니라 RP 가 받아서 세션을 끊었다를 증명한다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## 최종 상태

| | 시작 | 완료 |
|---|---|---|
| 바이너리 | 8 | **10** (session 8088, demo-rp 8095) |
| token | 112 | ~121 |
| auth | 16 | ~31 |
| client-registry | 5 | 6 |
| signing | 4 | 8 |
| session | — | 17 |
| demo-rp | — | 2 |
| token-state · user-directory · consent | 42 · 6 · 4 | 변경 없음 |
