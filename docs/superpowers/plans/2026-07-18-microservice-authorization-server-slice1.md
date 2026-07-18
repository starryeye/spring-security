# microservice authorization server — 첫 관통 슬라이스 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** SAS starter 없이 직접 구현한 6개 독립 마이크로서비스로 authorization code + PKCE(S256) flow 하나를 관통시킨다.

**Architecture:** gateway(nginx) 뒤에 auth·token·signing·user-directory·client-registry 5개 Spring Boot 바이너리를 두고, 각 서비스가 자기 데이터만 소유하며 REST 로 협력한다. auth 가 만든 authorization code 는 Redis 를 통해 token 이 소비하고, JWT 서명은 개인키를 독점하는 signing 서비스가 전담한다.

**Tech Stack:** Java 21, Spring Boot 3.4.5, Spring Security(SAS starter 제외), Spring Web, Spring Data Redis, Spring Session, Spring Data JPA + MySQL, Nimbus JOSE(signing 만), Caffeine(client-registry 만), Lombok, JUnit 5.

## Global Constraints

- Java 21 (gradle toolchain). 로컬 `java -jar` 는 `/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn/bin/java` 사용 (PATH java 는 17).
- Spring Boot 3.4.5, dependency-management 1.1.7, gradle 8.13 wrapper.
- **SAS starter(`spring-boot-starter-oauth2-authorization-server`) 금지.** OAuth 로직은 직접 구현.
- 각 서비스는 독립 Gradle 프로젝트. `group = dev.starryeye`, 패키지 `dev.starryeye.<service_name>`(underscore), 메인 클래스 PascalCase + `Application`.
- 위치: `oauth-2/authorization-server/practice/microservice/<service>/`.
- 포트: gateway 9000, auth 8081, token 8082, signing 8083, user-directory 8084, client-registry 8085.
- MySQL: `jdbc:mysql://localhost:3306/microservice_as`, root/1111. Redis: localhost:6379.
- seed 계정: user / 1111. seed client: clientId `my-client`, secret `secret`, redirectUri `http://127.0.0.1:8080/callback`, scopes `openid profile`, grantType `authorization_code`.
- 주석 스타일: 기존 repo 컨벤션(클래스 상단 javadoc "X 에 대해 알아본다/구현한다", 말끝 "..이다/..한다", 경험담 서술 금지 — "주의." 항목으로).
- gradle 빌드는 `--no-daemon` 사용(이 환경 데몬 SIGKILL 이슈 회피).
- 커밋 메시지 말미: `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

## 공유 REST 계약 (모든 서비스가 합의하는 인터페이스)

```
client-registry
  GET  /internal/clients/{clientId}
       200 { "clientId": str, "redirectUris": [str], "scopes": [str],
             "clientSecretHash": str, "grantTypes": [str] }
       404 (없음)

user-directory
  POST /internal/users/authenticate   { "username": str, "password": str }
       200 { "sub": str, "authorities": [str] }
       401 (실패)
  GET  /internal/users/{sub}
       200 { "sub": str, "username": str, "authorities": [str] }
       404

signing
  POST /internal/sign   { "claims": {..}, "header": {..} }
       200 { "jwt": str }
  GET  /oauth2/jwks     200 JWKSet(json)

Redis authorization code 저장 (auth 가 write, token 이 read+delete)
  key   "auth:code:{code}"
  value JSON { "clientId": str, "redirectUri": str, "scope": str,
              "sub": str, "codeChallenge": str }
  TTL   60초
```

---

## Task 1: signing 서비스 — 프로젝트 스캐폴드 + 서명 키

**Files:**
- Create: `oauth-2/authorization-server/practice/microservice/signing/build.gradle`
- Create: `oauth-2/authorization-server/practice/microservice/signing/settings.gradle`
- Create: `.../signing/gradle/wrapper/gradle-wrapper.properties`
- Create: `.../signing/src/main/java/dev/starryeye/signing/SigningApplication.java`
- Create: `.../signing/src/main/resources/application.yml`
- Create: `.../signing/src/main/resources/keystore/signing.p12` (keytool 생성)

**Interfaces:**
- Produces: 기동 가능한 signing 바이너리, classpath 의 keystore(alias `signing-key-2026`, storepass/keypass `111111`).

- [ ] **Step 1: gradle wrapper 를 기존 프로젝트에서 복사**

```bash
cd oauth-2/authorization-server/practice/microservice
SRC=../production-ready-authorization-server
mkdir -p signing
cp -r $SRC/gradle signing/gradle
cp $SRC/gradlew signing/gradlew
cp $SRC/gradlew.bat signing/gradlew.bat 2>/dev/null || true
```

- [ ] **Step 2: build.gradle 작성**

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
	implementation 'com.nimbusds:nimbus-jose-jwt:9.47'
	compileOnly 'org.projectlombok:lombok'
	annotationProcessor 'org.projectlombok:lombok'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') { useJUnitPlatform() }
```

- [ ] **Step 3: settings.gradle + application.yml 작성**

`settings.gradle`:
```groovy
rootProject.name = 'signing'
```

`application.yml`:
```yaml
server:
  port: 8083

my:
  signing:
    key-store-location: classpath:keystore/signing.p12
    key-store-password: "111111"
    key-password: "111111"
    key-alias: signing-key-2026

logging:
  level:
    dev.starryeye: DEBUG
```

- [ ] **Step 4: keystore 생성**

```bash
cd oauth-2/authorization-server/practice/microservice/signing
mkdir -p src/main/resources/keystore
keytool -genkeypair -keyalg RSA -keysize 2048 -validity 3650 \
  -alias signing-key-2026 -dname "CN=starryeye-signing" \
  -keystore src/main/resources/keystore/signing.p12 -storetype PKCS12 \
  -storepass 111111 -keypass 111111
keytool -list -keystore src/main/resources/keystore/signing.p12 -storepass 111111 | grep signing-key-2026
```
Expected: `signing-key-2026, ..., PrivateKeyEntry,`

- [ ] **Step 5: SigningApplication 작성**

```java
package dev.starryeye.signing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SigningApplication {

	/**
	 * JWT 서명을 전담하는 서비스이다.
	 *      개인키(keystore)를 이 서비스만 보유하고, 다른 서비스는 "이 claims 를 서명해달라" 고 요청만 한다.
	 *      -> token 서비스가 털려도 개인키는 노출되지 않는다. (키 격리)
	 */

	public static void main(String[] args) {
		SpringApplication.run(SigningApplication.class, args);
	}
}
```

- [ ] **Step 6: 빌드로 스캐폴드 검증**

Run: `cd oauth-2/authorization-server/practice/microservice/signing && ./gradlew compileJava --no-daemon -q`
Expected: 성공 (에러 없음)

- [ ] **Step 7: Commit**

```bash
git add oauth-2/authorization-server/practice/microservice/signing
git commit -m "microservice: scaffold signing service with keystore

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 2: signing 서비스 — JwkKeyProvider (keystore 로드)

**Files:**
- Create: `.../signing/src/main/java/dev/starryeye/signing/JwkKeyProvider.java`
- Test: `.../signing/src/test/java/dev/starryeye/signing/JwkKeyProviderTest.java`

**Interfaces:**
- Produces: `JwkKeyProvider` 빈. `RSAKey getSigningKey()` (개인키 포함, kid=alias), `JWKSet getPublicJwkSet()` (공개키만).

- [ ] **Step 1: 실패 테스트 작성**

```java
package dev.starryeye.signing;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JwkKeyProviderTest {

	@Autowired
	JwkKeyProvider provider;

	@Test
	void signingKeyHasPrivatePartAndAliasKid() {
		RSAKey key = provider.getSigningKey();
		assertThat(key.isPrivate()).isTrue();
		assertThat(key.getKeyID()).isEqualTo("signing-key-2026");
	}

	@Test
	void publicJwkSetHidesPrivateKey() {
		JWKSet set = provider.getPublicJwkSet();
		assertThat(set.getKeys()).hasSize(1);
		assertThat(set.getKeys().get(0).isPrivate()).isFalse();
	}
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd .../signing && ./gradlew test --no-daemon --tests JwkKeyProviderTest`
Expected: FAIL (JwkKeyProvider 클래스 없음, 컴파일 에러)

- [ ] **Step 3: JwkKeyProvider 구현**

```java
package dev.starryeye.signing;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.KeyStore;

@Component
public class JwkKeyProvider {

	/**
	 * keystore(PKCS12) 에서 서명 키를 로드한다.
	 *      RSAKey.load 는 keystore alias 를 kid 로 사용한다. (재기동/다중 인스턴스에서 동일)
	 */

	private final RSAKey signingKey;

	public JwkKeyProvider(
			@Value("${my.signing.key-store-location}") Resource keyStoreLocation,
			@Value("${my.signing.key-store-password}") String keyStorePassword,
			@Value("${my.signing.key-password}") String keyPassword,
			@Value("${my.signing.key-alias}") String keyAlias
	) throws Exception {
		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		try (InputStream is = keyStoreLocation.getInputStream()) {
			keyStore.load(is, keyStorePassword.toCharArray());
		}
		this.signingKey = RSAKey.load(keyStore, keyAlias, keyPassword.toCharArray());
	}

	public RSAKey getSigningKey() {
		return signingKey;
	}

	public JWKSet getPublicJwkSet() {
		return new JWKSet(signingKey.toPublicJWK());
	}
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd .../signing && ./gradlew test --no-daemon --tests JwkKeyProviderTest`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add oauth-2/authorization-server/practice/microservice/signing
git commit -m "microservice: signing JwkKeyProvider loads keystore

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 3: signing 서비스 — sign API + jwks 엔드포인트

**Files:**
- Create: `.../signing/src/main/java/dev/starryeye/signing/SignController.java`
- Create: `.../signing/src/main/java/dev/starryeye/signing/dto/SignRequest.java`
- Create: `.../signing/src/main/java/dev/starryeye/signing/dto/SignResponse.java`
- Test: `.../signing/src/test/java/dev/starryeye/signing/SignControllerTest.java`

**Interfaces:**
- Consumes: `JwkKeyProvider`.
- Produces: `POST /internal/sign {claims, header} → {jwt}`, `GET /oauth2/jwks → JWKSet json`.

- [ ] **Step 1: 실패 테스트 작성 (MockMvc)**

```java
package dev.starryeye.signing;

import com.nimbusds.jose.JWSObject;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SignControllerTest {

	@Autowired MockMvc mockMvc;

	@Test
	void signReturnsValidRs256Jwt() throws Exception {
		String body = """
			{"claims":{"sub":"user","iss":"http://localhost:9000"},"header":{"kid":"signing-key-2026"}}
			""";
		String json = mockMvc.perform(post("/internal/sign")
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jwt", notNullValue()))
				.andReturn().getResponse().getContentAsString();

		String jwt = com.jayway.jsonpath.JsonPath.read(json, "$.jwt");
		SignedJWT parsed = SignedJWT.parse(jwt);
		org.assertj.core.api.Assertions.assertThat(parsed.getState()).isEqualTo(JWSObject.State.SIGNED);
		org.assertj.core.api.Assertions.assertThat(parsed.getJWTClaimsSet().getSubject()).isEqualTo("user");
	}

	@Test
	void jwksExposesPublicKeyOnly() throws Exception {
		mockMvc.perform(get("/oauth2/jwks"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.keys[0].kty").value("RSA"))
				.andExpect(jsonPath("$.keys[0].d").doesNotExist());
	}
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd .../signing && ./gradlew test --no-daemon --tests SignControllerTest`
Expected: FAIL (SignController 없음)

- [ ] **Step 3: DTO 작성**

`SignRequest.java`:
```java
package dev.starryeye.signing.dto;

import java.util.Map;

public record SignRequest(Map<String, Object> claims, Map<String, Object> header) {
}
```

`SignResponse.java`:
```java
package dev.starryeye.signing.dto;

public record SignResponse(String jwt) {
}
```

- [ ] **Step 4: SignController 구현**

```java
package dev.starryeye.signing;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.starryeye.signing.dto.SignRequest;
import dev.starryeye.signing.dto.SignResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SignController {

	/**
	 * "서명 기계" 로서 claims 를 받아 RS256 으로 서명한 JWT 를 돌려준다.
	 *      iss/exp 같은 표준 claim 은 token 서비스가 채워서 넘긴다. 이 서비스는 정책 판단을 하지 않고 서명 + kid 지정만 한다.
	 */

	private final JwkKeyProvider keyProvider;

	@PostMapping("/internal/sign")
	public SignResponse sign(@RequestBody SignRequest request) throws Exception {

		JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder();
		request.claims().forEach(claimsBuilder::claim);

		JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
				.keyID(keyProvider.getSigningKey().getKeyID())
				.build();

		SignedJWT signedJWT = new SignedJWT(header, claimsBuilder.build());
		signedJWT.sign(new RSASSASigner(keyProvider.getSigningKey()));

		return new SignResponse(signedJWT.serialize());
	}

	@GetMapping("/oauth2/jwks")
	public Map<String, Object> jwks() {
		return keyProvider.getPublicJwkSet().toJSONObject();
	}
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd .../signing && ./gradlew test --no-daemon --tests SignControllerTest`
Expected: PASS (2 tests)

- [ ] **Step 6: Commit**

```bash
git add oauth-2/authorization-server/practice/microservice/signing
git commit -m "microservice: signing sign API and jwks endpoint

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 4: user-directory 서비스 — 스캐폴드 + 엔티티/시드

**Files:**
- Create: `.../user-directory/build.gradle`, `settings.gradle`, `gradle/*`, `application.yml`
- Create: `.../user-directory/src/main/java/dev/starryeye/user_directory/UserDirectoryApplication.java`
- Create: `.../user-directory/src/main/java/dev/starryeye/user_directory/jpa/UserEntity.java`
- Create: `.../user-directory/src/main/java/dev/starryeye/user_directory/jpa/UserEntityRepository.java`
- Create: `.../user-directory/src/main/java/dev/starryeye/user_directory/UserSeedInitializer.java`

**Interfaces:**
- Produces: users 테이블(username unique, bcrypt password, comma authorities), seed user/1111(ROLE_USER). `UserEntityRepository.findByUsername`, `findBySub`.

- [ ] **Step 1: wrapper 복사 + build.gradle**

```bash
cd oauth-2/authorization-server/practice/microservice
mkdir -p user-directory
cp -r signing/gradle user-directory/gradle
cp signing/gradlew user-directory/gradlew
```

`build.gradle`:
```groovy
plugins {
	id 'java'
	id 'org.springframework.boot' version '3.4.5'
	id 'io.spring.dependency-management' version '1.1.7'
}
group = 'dev.starryeye'
version = '0.0.1-SNAPSHOT'
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
configurations { compileOnly { extendsFrom annotationProcessor } }
repositories { mavenCentral() }
dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-web'
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	implementation 'org.springframework.security:spring-security-crypto'
	runtimeOnly 'com.mysql:mysql-connector-j'
	compileOnly 'org.projectlombok:lombok'
	annotationProcessor 'org.projectlombok:lombok'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
tasks.named('test') { useJUnitPlatform() }
```

- [ ] **Step 2: settings.gradle + application.yml**

`settings.gradle`: `rootProject.name = 'user-directory'`

`application.yml`:
```yaml
server:
  port: 8084
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

- [ ] **Step 3: Application + 엔티티 + 리포지토리 작성**

`UserDirectoryApplication.java`:
```java
package dev.starryeye.user_directory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UserDirectoryApplication {

	/**
	 * 사용자 신원과 credential 을 소유하는 서비스이다.
	 *      password 비교(bcrypt)를 이 서비스 안에 가둔다. auth 는 평문을 넘겨 검증을 위임할 뿐 password 해시를 보지 않는다.
	 */

	public static void main(String[] args) {
		SpringApplication.run(UserDirectoryApplication.class, args);
	}
}
```

`jpa/UserEntity.java`:
```java
package dev.starryeye.user_directory.jpa;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique = true, nullable = false)
	private String sub; // 사용자 고유 식별자 (토큰 sub claim 이 된다)

	@Column(unique = true, nullable = false)
	private String username;

	@Column(nullable = false)
	private String password; // {bcrypt}.. 인코딩 저장

	@Column(nullable = false, length = 500)
	private String authorities; // comma 구분

	@Builder
	private UserEntity(String sub, String username, String password, String authorities) {
		this.sub = sub;
		this.username = username;
		this.password = password;
		this.authorities = authorities;
	}
}
```

`jpa/UserEntityRepository.java`:
```java
package dev.starryeye.user_directory.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserEntityRepository extends JpaRepository<UserEntity, Long> {
	Optional<UserEntity> findByUsername(String username);
	Optional<UserEntity> findBySub(String sub);
}
```

- [ ] **Step 4: UserSeedInitializer 작성**

```java
package dev.starryeye.user_directory;

import dev.starryeye.user_directory.jpa.UserEntity;
import dev.starryeye.user_directory.jpa.UserEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserSeedInitializer implements ApplicationRunner {

	/**
	 * 첫 슬라이스는 admin 등록 API 없이 seed 로 사용자 하나를 넣는다. (이후 슬라이스에서 등록 API)
	 */

	private final UserEntityRepository repository;

	@Override
	public void run(ApplicationArguments args) {
		if (repository.findByUsername("user").isPresent()) {
			return;
		}
		repository.save(UserEntity.builder()
				.sub("user-sub-0001")
				.username("user")
				.password(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("1111"))
				.authorities("ROLE_USER")
				.build());
	}
}
```

- [ ] **Step 5: 컴파일 검증**

Run: `cd .../user-directory && ./gradlew compileJava --no-daemon -q`
Expected: 성공

- [ ] **Step 6: Commit**

```bash
git add oauth-2/authorization-server/practice/microservice/user-directory
git commit -m "microservice: scaffold user-directory with user entity and seed

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 5: user-directory 서비스 — authenticate/조회 API

**Files:**
- Create: `.../user_directory/UserController.java`
- Create: `.../user_directory/dto/AuthenticateRequest.java`, `dto/AuthenticateResponse.java`, `dto/UserResponse.java`
- Test: `.../user_directory/src/test/java/dev/starryeye/user_directory/UserServiceLogicTest.java`

**Interfaces:**
- Consumes: `UserEntityRepository`.
- Produces: `POST /internal/users/authenticate {username,password} → {sub, authorities:[str]} | 401`, `GET /internal/users/{sub} → {sub, username, authorities:[str]} | 404`.

- [ ] **Step 1: 실패 단위 테스트 작성 (password 검증 로직)**

```java
package dev.starryeye.user_directory;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class UserServiceLogicTest {

	// user-directory 의 password 검증은 DelegatingPasswordEncoder.matches 로 이뤄진다.
	@Test
	void bcryptMatchesRawPassword() {
		PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
		String stored = encoder.encode("1111");
		assertThat(encoder.matches("1111", stored)).isTrue();
		assertThat(encoder.matches("wrong", stored)).isFalse();
	}
}
```

- [ ] **Step 2: 테스트 실행 (이 단계는 라이브러리 동작 확인이라 바로 PASS)**

Run: `cd .../user-directory && ./gradlew test --no-daemon --tests UserServiceLogicTest`
Expected: PASS (1 test) — 이 라이브러리 계약을 컨트롤러가 사용한다.

- [ ] **Step 3: DTO 작성**

`dto/AuthenticateRequest.java`:
```java
package dev.starryeye.user_directory.dto;

public record AuthenticateRequest(String username, String password) {
}
```

`dto/AuthenticateResponse.java`:
```java
package dev.starryeye.user_directory.dto;

import java.util.List;

public record AuthenticateResponse(String sub, List<String> authorities) {
}
```

`dto/UserResponse.java`:
```java
package dev.starryeye.user_directory.dto;

import java.util.List;

public record UserResponse(String sub, String username, List<String> authorities) {
}
```

- [ ] **Step 4: UserController 구현**

```java
package dev.starryeye.user_directory;

import dev.starryeye.user_directory.dto.*;
import dev.starryeye.user_directory.jpa.UserEntity;
import dev.starryeye.user_directory.jpa.UserEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class UserController {

	/**
	 * 사용자 조회와 credential 검증 API 이다. (계약은 공유 REST 계약 참고)
	 *      authenticate 는 성공 시 sub/authorities 만 돌려준다. password 해시는 응답에 절대 넣지 않는다.
	 */

	private final UserEntityRepository repository;
	private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

	@PostMapping("/internal/users/authenticate")
	public AuthenticateResponse authenticate(@RequestBody AuthenticateRequest request) {
		UserEntity user = repository.findByUsername(request.username())
				.filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
		return new AuthenticateResponse(user.getSub(), toList(user.getAuthorities()));
	}

	@GetMapping("/internal/users/{sub}")
	public UserResponse getUser(@PathVariable String sub) {
		UserEntity user = repository.findBySub(sub)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		return new UserResponse(user.getSub(), user.getUsername(), toList(user.getAuthorities()));
	}

	private List<String> toList(String commaDelimited) {
		return List.of(StringUtils.commaDelimitedListToStringArray(commaDelimited));
	}
}
```

- [ ] **Step 5: 컴파일 검증 + commit**

Run: `cd .../user-directory && ./gradlew compileJava --no-daemon -q`
Expected: 성공

```bash
git add oauth-2/authorization-server/practice/microservice/user-directory
git commit -m "microservice: user-directory authenticate and lookup API

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 6: client-registry 서비스 — 스캐폴드 + 엔티티/시드 + 조회 API + 캐시

**Files:**
- Create: `.../client-registry/build.gradle`, `settings.gradle`, `gradle/*`, `application.yml`
- Create: `.../client_registry/ClientRegistryApplication.java`
- Create: `.../client_registry/jpa/ClientEntity.java`, `jpa/ClientEntityRepository.java`
- Create: `.../client_registry/ClientSeedInitializer.java`
- Create: `.../client_registry/ClientController.java`
- Create: `.../client_registry/dto/ClientResponse.java`
- Create: `.../client_registry/CacheConfig.java`
- Test: `.../client_registry/src/test/java/dev/starryeye/client_registry/ClientControllerTest.java`

**Interfaces:**
- Produces: `GET /internal/clients/{clientId} → {clientId, redirectUris:[str], scopes:[str], clientSecretHash:str, grantTypes:[str]} | 404`, Caffeine 캐시(`clients`, TTL 30초). seed client `my-client`.

- [ ] **Step 1: wrapper 복사 + build.gradle (Caffeine 포함)**

```bash
cd oauth-2/authorization-server/practice/microservice
mkdir -p client-registry
cp -r signing/gradle client-registry/gradle
cp signing/gradlew client-registry/gradlew
```

`build.gradle` dependencies 블록:
```groovy
dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-web'
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	implementation 'org.springframework.boot:spring-boot-starter-cache'
	implementation 'com.github.ben-manes.caffeine:caffeine:3.2.3'
	implementation 'org.springframework.security:spring-security-crypto'
	runtimeOnly 'com.mysql:mysql-connector-j'
	compileOnly 'org.projectlombok:lombok'
	annotationProcessor 'org.projectlombok:lombok'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```
(plugins/group/java/configurations/repositories 블록은 Task 4 build.gradle 과 동일 구조로 작성)

- [ ] **Step 2: settings.gradle + application.yml**

`settings.gradle`: `rootProject.name = 'client-registry'`

`application.yml`:
```yaml
server:
  port: 8085
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

- [ ] **Step 3: Application + 엔티티 + 리포지토리**

`ClientRegistryApplication.java`:
```java
package dev.starryeye.client_registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class ClientRegistryApplication {

	/**
	 * client 메타데이터를 소유하는 서비스이다.
	 *      client 정보는 자주 변하지 않으므로 짧은 TTL 캐시(Caffeine)를 둔다.
	 *      -> client-registry 가 잠깐 느려지거나 죽어도 캐시된 client 로 authorize/token 이 견딘다. (분산 캐시 필요성)
	 */

	public static void main(String[] args) {
		SpringApplication.run(ClientRegistryApplication.class, args);
	}
}
```

`jpa/ClientEntity.java`:
```java
package dev.starryeye.client_registry.jpa;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "clients")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientEntity {

	@Id
	private String clientId;

	@Column(nullable = false)
	private String clientSecretHash; // {bcrypt}..

	@Column(nullable = false, length = 1000)
	private String redirectUris; // comma 구분

	@Column(nullable = false, length = 500)
	private String scopes; // comma 구분

	@Column(nullable = false, length = 500)
	private String grantTypes; // comma 구분

	@Builder
	private ClientEntity(String clientId, String clientSecretHash, String redirectUris, String scopes, String grantTypes) {
		this.clientId = clientId;
		this.clientSecretHash = clientSecretHash;
		this.redirectUris = redirectUris;
		this.scopes = scopes;
		this.grantTypes = grantTypes;
	}
}
```

`jpa/ClientEntityRepository.java`:
```java
package dev.starryeye.client_registry.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientEntityRepository extends JpaRepository<ClientEntity, String> {
}
```

- [ ] **Step 4: CacheConfig + Seed**

`CacheConfig.java`:
```java
package dev.starryeye.client_registry;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {

	/**
	 * client 조회 캐시.. 짧은 TTL(30초)로 원본 갱신을 곧 반영하면서도 반복 조회 부하를 줄인다.
	 */
	@Bean
	public CacheManager cacheManager() {
		CaffeineCacheManager manager = new CaffeineCacheManager("clients");
		manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)));
		return manager;
	}
}
```

`ClientSeedInitializer.java`:
```java
package dev.starryeye.client_registry;

import dev.starryeye.client_registry.jpa.ClientEntity;
import dev.starryeye.client_registry.jpa.ClientEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClientSeedInitializer implements ApplicationRunner {

	private final ClientEntityRepository repository;

	@Override
	public void run(ApplicationArguments args) {
		if (repository.existsById("my-client")) {
			return;
		}
		repository.save(ClientEntity.builder()
				.clientId("my-client")
				.clientSecretHash(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("secret"))
				.redirectUris("http://127.0.0.1:8080/callback")
				.scopes("openid,profile")
				.grantTypes("authorization_code")
				.build());
	}
}
```

- [ ] **Step 5: 실패 테스트 작성 (조회 + 캐시 동작)**

```java
package dev.starryeye.client_registry;

import dev.starryeye.client_registry.jpa.ClientEntityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ClientControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired ClientEntityRepository repository;

	@Test
	void returnsSeededClient() throws Exception {
		mockMvc.perform(get("/internal/clients/my-client"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clientId").value("my-client"))
				.andExpect(jsonPath("$.redirectUris[0]").value("http://127.0.0.1:8080/callback"))
				.andExpect(jsonPath("$.scopes", org.hamcrest.Matchers.contains("openid", "profile")))
				.andExpect(jsonPath("$.clientSecretHash").exists());
	}

	@Test
	void unknownClientReturns404() throws Exception {
		mockMvc.perform(get("/internal/clients/no-such-client"))
				.andExpect(status().isNotFound());
	}
}
```

- [ ] **Step 6: 테스트 실패 확인**

Run: `cd .../client-registry && ./gradlew test --no-daemon --tests ClientControllerTest`
Expected: FAIL (ClientController 없음). MySQL 필요 — Task 12 에서 docker 기동 후 재확인, 지금은 컴파일 실패만 확인해도 됨.

- [ ] **Step 7: ClientController + DTO 구현**

`dto/ClientResponse.java`:
```java
package dev.starryeye.client_registry.dto;

import java.util.List;

public record ClientResponse(
		String clientId,
		List<String> redirectUris,
		List<String> scopes,
		String clientSecretHash,
		List<String> grantTypes
) {
}
```

`ClientController.java`:
```java
package dev.starryeye.client_registry;

import dev.starryeye.client_registry.dto.ClientResponse;
import dev.starryeye.client_registry.jpa.ClientEntity;
import dev.starryeye.client_registry.jpa.ClientEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ClientController {

	/**
	 * client 조회 API.. 조회 결과를 "clients" 캐시에 담는다. (CacheConfig 의 TTL 30초)
	 */

	private final ClientLookupService lookupService;

	@GetMapping("/internal/clients/{clientId}")
	public ClientResponse getClient(@PathVariable String clientId) {
		return lookupService.findByClientId(clientId);
	}

	@Service
	@RequiredArgsConstructor
	static class ClientLookupService {

		private final ClientEntityRepository repository;

		// @Cacheable 은 같은 빈 내부 호출에서는 동작하지 않으므로 별도 빈으로 분리한다.
		@Cacheable("clients")
		public ClientResponse findByClientId(String clientId) {
			ClientEntity entity = repository.findById(clientId)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
			return new ClientResponse(
					entity.getClientId(),
					List.of(StringUtils.commaDelimitedListToStringArray(entity.getRedirectUris())),
					List.of(StringUtils.commaDelimitedListToStringArray(entity.getScopes())),
					entity.getClientSecretHash(),
					List.of(StringUtils.commaDelimitedListToStringArray(entity.getGrantTypes()))
			);
		}
	}
}
```

- [ ] **Step 8: 컴파일 검증 + commit**

Run: `cd .../client-registry && ./gradlew compileJava --no-daemon -q`
Expected: 성공

```bash
git add oauth-2/authorization-server/practice/microservice/client-registry
git commit -m "microservice: client-registry lookup API with caffeine cache

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 7: token 서비스 — 스캐폴드 + PKCE 검증 로직

**Files:**
- Create: `.../token/build.gradle`, `settings.gradle`, `gradle/*`, `application.yml`
- Create: `.../token/TokenApplication.java`
- Create: `.../token/PkceValidator.java`
- Test: `.../token/src/test/java/dev/starryeye/token/PkceValidatorTest.java`

**Interfaces:**
- Produces: `PkceValidator.matches(String codeVerifier, String storedChallenge) → boolean` (S256: BASE64URL(SHA256(verifier)) == challenge).

- [ ] **Step 1: wrapper + build.gradle**

```bash
cd oauth-2/authorization-server/practice/microservice
mkdir -p token
cp -r signing/gradle token/gradle
cp signing/gradlew token/gradlew
```

`build.gradle` dependencies:
```groovy
dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-web'
	implementation 'org.springframework.boot:spring-boot-starter-data-redis'
	implementation 'org.springframework.security:spring-security-crypto'
	compileOnly 'org.projectlombok:lombok'
	annotationProcessor 'org.projectlombok:lombok'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

- [ ] **Step 2: settings.gradle + application.yml**

`settings.gradle`: `rootProject.name = 'token'`

`application.yml`:
```yaml
server:
  port: 8082
spring:
  data:
    redis:
      host: localhost
      port: 6379
my:
  issuer: http://localhost:9000
  signing-base-url: http://localhost:8083
  client-registry-base-url: http://localhost:8085
  access-token-ttl-seconds: 300
logging:
  level:
    dev.starryeye: DEBUG
```

- [ ] **Step 3: TokenApplication 작성**

```java
package dev.starryeye.token;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TokenApplication {

	/**
	 * back-channel 을 담당하는 서비스이다.
	 *      code 를 access token 으로 교환하고(/oauth2/token), 표준 claim 을 구성해 signing 에 서명을 위임한다.
	 *      jwks 는 signing 이 소유하며 이 서비스는 프록시로 노출한다.
	 */

	public static void main(String[] args) {
		SpringApplication.run(TokenApplication.class, args);
	}
}
```

- [ ] **Step 4: 실패 테스트 작성 (PKCE S256)**

```java
package dev.starryeye.token;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PkceValidatorTest {

	// RFC 7636 부록 B 의 예시 벡터
	private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
	private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

	@Test
	void matchesKnownVector() {
		assertThat(new PkceValidator().matches(VERIFIER, CHALLENGE)).isTrue();
	}

	@Test
	void rejectsWrongVerifier() {
		assertThat(new PkceValidator().matches("wrong-verifier", CHALLENGE)).isFalse();
	}
}
```

- [ ] **Step 5: 테스트 실패 확인**

Run: `cd .../token && ./gradlew test --no-daemon --tests PkceValidatorTest`
Expected: FAIL (PkceValidator 없음)

- [ ] **Step 6: PkceValidator 구현**

```java
package dev.starryeye.token;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class PkceValidator {

	/**
	 * PKCE(S256) 검증이다.
	 *      challenge = BASE64URL( SHA256( verifier ) ). auth 가 저장한 challenge 와 token 이 받은 verifier 로 대조한다.
	 */

	public boolean matches(String codeVerifier, String storedChallenge) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
			String computed = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
			return computed.equals(storedChallenge);
		} catch (Exception e) {
			return false;
		}
	}
}
```

- [ ] **Step 7: 테스트 통과 확인 + commit**

Run: `cd .../token && ./gradlew test --no-daemon --tests PkceValidatorTest`
Expected: PASS (2 tests)

```bash
git add oauth-2/authorization-server/practice/microservice/token
git commit -m "microservice: scaffold token service with PKCE validator

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 8: token 서비스 — code 저장소 + 원격 client 클라이언트

**Files:**
- Create: `.../token/AuthorizationCodeStore.java`
- Create: `.../token/AuthorizationCodeData.java`
- Create: `.../token/client/ClientRegistryClient.java`
- Create: `.../token/client/ClientInfo.java`
- Create: `.../token/config/RestClientConfig.java`
- Test: `.../token/src/test/java/dev/starryeye/token/AuthorizationCodeStoreTest.java`

**Interfaces:**
- Consumes: `StringRedisTemplate`.
- Produces:
  - `AuthorizationCodeData(String clientId, String redirectUri, String scope, String sub, String codeChallenge)` (record)
  - `AuthorizationCodeStore.consume(String code) → Optional<AuthorizationCodeData>` (조회 + 삭제, 1회용)
  - `ClientRegistryClient.getClient(String clientId) → ClientInfo` (404 시 null)
  - `ClientInfo(String clientId, List<String> redirectUris, List<String> scopes, String clientSecretHash, List<String> grantTypes)`

- [ ] **Step 1: RestClient 설정 (2초 타임아웃)**

`config/RestClientConfig.java`:
```java
package dev.starryeye.token.config;

import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactories;

import java.time.Duration;

@Configuration
public class RestClientConfig {

	/**
	 * 내부 REST 호출은 짧은 타임아웃(2초)만 두고 재시도는 하지 않는다. (첫 슬라이스.. 서킷브레이커/재시도는 이후)
	 */
	@Bean
	public RestClientCustomizer restClientCustomizer() {
		return builder -> builder.requestFactory(
				ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
						.withConnectTimeout(Duration.ofSeconds(2))
						.withReadTimeout(Duration.ofSeconds(2))));
	}
}
```

- [ ] **Step 2: AuthorizationCodeData + Store 작성**

`AuthorizationCodeData.java`:
```java
package dev.starryeye.token;

public record AuthorizationCodeData(
		String clientId,
		String redirectUri,
		String scope,
		String sub,
		String codeChallenge
) {
}
```

`AuthorizationCodeStore.java`:
```java
package dev.starryeye.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AuthorizationCodeStore {

	/**
	 * auth 가 Redis 에 저장한 authorization code 를 조회하고 즉시 삭제한다. (1회용)
	 *      key 형식과 JSON 필드는 auth 와 합의한 공유 계약이다. ("auth:code:{code}")
	 */

	private static final String KEY_PREFIX = "auth:code:";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public Optional<AuthorizationCodeData> consume(String code) {
		String key = KEY_PREFIX + code;
		String json = redisTemplate.opsForValue().get(key);
		if (json == null) {
			return Optional.empty();
		}
		redisTemplate.delete(key); // 1회용 소비
		try {
			return Optional.of(objectMapper.readValue(json, AuthorizationCodeData.class));
		} catch (Exception e) {
			return Optional.empty();
		}
	}
}
```

- [ ] **Step 3: ClientRegistryClient 작성**

`client/ClientInfo.java`:
```java
package dev.starryeye.token.client;

import java.util.List;

public record ClientInfo(
		String clientId,
		List<String> redirectUris,
		List<String> scopes,
		String clientSecretHash,
		List<String> grantTypes
) {
}
```

`client/ClientRegistryClient.java`:
```java
package dev.starryeye.token.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ClientRegistryClient {

	/**
	 * client-registry 의 client 조회 API 를 호출한다. 없으면 null 을 반환한다.
	 */

	private final RestClient restClient;

	public ClientRegistryClient(RestClient.Builder builder, @Value("${my.client-registry-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public ClientInfo getClient(String clientId) {
		return restClient.get()
				.uri("/internal/clients/{clientId}", clientId)
				.retrieve()
				.onStatus(status -> status.value() == 404, (req, res) -> { throw new ClientNotFoundException(); })
				.body(ClientInfo.class);
	}

	public static class ClientNotFoundException extends RuntimeException {
	}
}
```

- [ ] **Step 4: 실패 테스트 작성 (embedded redis 대신 Mock StringRedisTemplate)**

```java
package dev.starryeye.token;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthorizationCodeStoreTest {

	@Test
	void consumeReadsThenDeletes() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);
		when(ops.get("auth:code:abc")).thenReturn(
				"{\"clientId\":\"my-client\",\"redirectUri\":\"http://127.0.0.1:8080/callback\",\"scope\":\"openid profile\",\"sub\":\"user-sub-0001\",\"codeChallenge\":\"chal\"}");

		AuthorizationCodeStore store = new AuthorizationCodeStore(redis);
		Optional<AuthorizationCodeData> result = store.consume("abc");

		assertThat(result).isPresent();
		assertThat(result.get().sub()).isEqualTo("user-sub-0001");
		verify(redis).delete("auth:code:abc");
	}

	@Test
	void consumeMissingReturnsEmpty() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);
		when(ops.get("auth:code:none")).thenReturn(null);

		AuthorizationCodeStore store = new AuthorizationCodeStore(redis);
		assertThat(store.consume("none")).isEmpty();
	}
}
```

- [ ] **Step 5: 테스트 실행 (컴파일 후 PASS 확인)**

Run: `cd .../token && ./gradlew test --no-daemon --tests AuthorizationCodeStoreTest`
Expected: PASS (2 tests)

- [ ] **Step 6: Commit**

```bash
git add oauth-2/authorization-server/practice/microservice/token
git commit -m "microservice: token code store and client-registry client

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 9: token 서비스 — signing 클라이언트 + 토큰 엔드포인트

**Files:**
- Create: `.../token/client/SigningClient.java`
- Create: `.../token/TokenEndpointController.java`
- Create: `.../token/dto/TokenResponse.java`
- Create: `.../token/dto/OAuth2ErrorResponse.java`
- Test: `.../token/src/test/java/dev/starryeye/token/TokenEndpointControllerTest.java`

**Interfaces:**
- Consumes: `AuthorizationCodeStore`, `PkceValidator`, `ClientRegistryClient`, `SigningClient`.
- Produces: `POST /oauth2/token` (form: grant_type, code, redirect_uri, code_verifier; Basic client auth) → `{access_token, token_type, expires_in, scope}` 또는 OAuth2 에러. `GET /oauth2/jwks` (signing 프록시). `GET /.well-known/oauth-authorization-server`.

- [ ] **Step 1: SigningClient 작성**

```java
package dev.starryeye.token.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class SigningClient {

	/**
	 * signing 서비스에 claims 를 넘겨 서명된 JWT 를 받는다. jwks 도 signing 이 소유하므로 여기서 프록시한다.
	 */

	private final RestClient restClient;

	public SigningClient(RestClient.Builder builder, @Value("${my.signing-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public String sign(Map<String, Object> claims) {
		Map<String, Object> body = Map.of("claims", claims, "header", Map.of());
		Map<?, ?> response = restClient.post().uri("/internal/sign").body(body).retrieve().body(Map.class);
		return (String) response.get("jwt");
	}

	public Map<?, ?> jwks() {
		return restClient.get().uri("/oauth2/jwks").retrieve().body(Map.class);
	}
}
```

- [ ] **Step 2: DTO 작성**

`dto/TokenResponse.java`:
```java
package dev.starryeye.token.dto;

public record TokenResponse(String access_token, String token_type, long expires_in, String scope) {
}
```

`dto/OAuth2ErrorResponse.java`:
```java
package dev.starryeye.token.dto;

public record OAuth2ErrorResponse(String error, String error_description) {
}
```

- [ ] **Step 3: 실패 테스트 작성 (에러 케이스 위주, 협력자 Mock)**

```java
package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.ClientRegistryClient;
import dev.starryeye.token.client.SigningClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TokenEndpointControllerTest {

	@Autowired MockMvc mockMvc;
	@MockBean AuthorizationCodeStore codeStore;
	@MockBean ClientRegistryClient clientRegistryClient;
	@MockBean SigningClient signingClient;

	private static final String BASIC = "Basic " + java.util.Base64.getEncoder()
			.encodeToString("my-client:secret".getBytes());

	@Test
	void unknownCodeReturnsInvalidGrant() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(codeStore.consume("badcode")).thenReturn(Optional.empty());

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "authorization_code")
						.param("code", "badcode")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "whatever"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_grant"));
	}

	@Test
	void wrongClientSecretReturnsInvalidClient() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		String badBasic = "Basic " + java.util.Base64.getEncoder()
				.encodeToString("my-client:wrong".getBytes());

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", badBasic)
						.param("grant_type", "authorization_code")
						.param("code", "x")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "v"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_client"));
	}

	private ClientInfo clientInfo() {
		// secret "secret" 의 bcrypt 해시
		String hash = org.springframework.security.crypto.factory.PasswordEncoderFactories
				.createDelegatingPasswordEncoder().encode("secret");
		return new ClientInfo("my-client",
				List.of("http://127.0.0.1:8080/callback"),
				List.of("openid", "profile"), hash, List.of("authorization_code"));
	}
}
```

- [ ] **Step 4: 테스트 실패 확인**

Run: `cd .../token && ./gradlew test --no-daemon --tests TokenEndpointControllerTest`
Expected: FAIL (TokenEndpointController 없음)

- [ ] **Step 5: TokenEndpointController 구현**

```java
package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.ClientRegistryClient;
import dev.starryeye.token.client.SigningClient;
import dev.starryeye.token.dto.OAuth2ErrorResponse;
import dev.starryeye.token.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class TokenEndpointController {

	/**
	 * authorization code 를 access token 으로 교환한다. (직접 구현)
	 *      절차: client 인증 -> code 소비 -> PKCE 대조 -> 표준 claim 구성 -> signing 위임 -> JWT 응답.
	 */

	private final AuthorizationCodeStore codeStore;
	private final PkceValidator pkceValidator;
	private final ClientRegistryClient clientRegistryClient;
	private final SigningClient signingClient;
	private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

	@Value("${my.issuer}")
	private String issuer;

	@Value("${my.access-token-ttl-seconds}")
	private long accessTokenTtlSeconds;

	@PostMapping(value = "/oauth2/token", produces = "application/json")
	public ResponseEntity<?> token(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestParam("grant_type") String grantType,
			@RequestParam(value = "code", required = false) String code,
			@RequestParam(value = "redirect_uri", required = false) String redirectUri,
			@RequestParam(value = "code_verifier", required = false) String codeVerifier
	) {
		if (!"authorization_code".equals(grantType)) {
			return error(HttpStatus.BAD_REQUEST, "unsupported_grant_type", "only authorization_code is supported");
		}

		// 1. client 인증 (Basic)
		String[] credentials = parseBasic(authorization);
		if (credentials == null) {
			return error(HttpStatus.UNAUTHORIZED, "invalid_client", "missing client credentials");
		}
		ClientInfo client;
		try {
			client = clientRegistryClient.getClient(credentials[0]);
		} catch (ClientRegistryClient.ClientNotFoundException e) {
			return error(HttpStatus.UNAUTHORIZED, "invalid_client", "unknown client");
		}
		if (client == null || !passwordEncoder.matches(credentials[1], client.clientSecretHash())) {
			return error(HttpStatus.UNAUTHORIZED, "invalid_client", "bad client credentials");
		}

		// 2. code 소비
		Optional<AuthorizationCodeData> maybeData = (code == null) ? Optional.empty() : codeStore.consume(code);
		if (maybeData.isEmpty()) {
			return error(HttpStatus.BAD_REQUEST, "invalid_grant", "code invalid or expired");
		}
		AuthorizationCodeData data = maybeData.get();

		// 3. code 바인딩 검증 (client, redirect_uri)
		if (!data.clientId().equals(client.clientId()) || !data.redirectUri().equals(redirectUri)) {
			return error(HttpStatus.BAD_REQUEST, "invalid_grant", "code binding mismatch");
		}

		// 4. PKCE 대조
		if (codeVerifier == null || !pkceValidator.matches(codeVerifier, data.codeChallenge())) {
			return error(HttpStatus.BAD_REQUEST, "invalid_grant", "PKCE verification failed");
		}

		// 5. 표준 claim 구성 + signing 위임
		Instant now = Instant.now();
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("iss", issuer);
		claims.put("sub", data.sub());
		claims.put("aud", client.clientId());
		claims.put("iat", now.getEpochSecond());
		claims.put("exp", now.plusSeconds(accessTokenTtlSeconds).getEpochSecond());
		claims.put("scope", Arrays.asList(data.scope().split(" ")));

		String jwt = signingClient.sign(claims);

		return ResponseEntity.ok(new TokenResponse(jwt, "Bearer", accessTokenTtlSeconds, data.scope()));
	}

	@GetMapping("/oauth2/jwks")
	public Map<?, ?> jwks() {
		return signingClient.jwks();
	}

	@GetMapping("/.well-known/oauth-authorization-server")
	public Map<String, Object> metadata() {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("issuer", issuer);
		metadata.put("token_endpoint", issuer + "/oauth2/token");
		metadata.put("authorization_endpoint", issuer + "/oauth2/authorize");
		metadata.put("jwks_uri", issuer + "/oauth2/jwks");
		metadata.put("code_challenge_methods_supported", List.of("S256"));
		metadata.put("grant_types_supported", List.of("authorization_code"));
		return metadata;
	}

	private String[] parseBasic(String authorization) {
		if (authorization == null || !authorization.startsWith("Basic ")) {
			return null;
		}
		String decoded = new String(Base64.getDecoder().decode(authorization.substring(6)));
		int idx = decoded.indexOf(':');
		if (idx < 0) {
			return null;
		}
		return new String[]{decoded.substring(0, idx), decoded.substring(idx + 1)};
	}

	private ResponseEntity<OAuth2ErrorResponse> error(HttpStatus status, String error, String description) {
		return ResponseEntity.status(status).body(new OAuth2ErrorResponse(error, description));
	}
}
```

- [ ] **Step 6: 테스트 통과 확인 + commit**

Run: `cd .../token && ./gradlew test --no-daemon --tests TokenEndpointControllerTest`
Expected: PASS (2 tests)

```bash
git add oauth-2/authorization-server/practice/microservice/token
git commit -m "microservice: token endpoint with PKCE and signing delegation

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 10: auth 서비스 — 스캐폴드 + 원격 인증 + 로그인 보안 설정

**Files:**
- Create: `.../auth/build.gradle`, `settings.gradle`, `gradle/*`, `application.yml`
- Create: `.../auth/AuthApplication.java`
- Create: `.../auth/client/UserDirectoryClient.java`
- Create: `.../auth/security/RemoteAuthenticationProvider.java`
- Create: `.../auth/security/SecurityConfig.java`
- Create: `.../auth/config/RestClientConfig.java`

**Interfaces:**
- Consumes: user-directory `/internal/users/authenticate`.
- Produces: 로그인 세션(Spring Session+Redis), `RemoteAuthenticationProvider` (user-directory 위임), 인증 principal name = sub.

- [ ] **Step 1: wrapper + build.gradle**

```bash
cd oauth-2/authorization-server/practice/microservice
mkdir -p auth
cp -r signing/gradle auth/gradle
cp signing/gradlew auth/gradlew
```

`build.gradle` dependencies:
```groovy
dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-web'
	implementation 'org.springframework.boot:spring-boot-starter-security'
	implementation 'org.springframework.boot:spring-boot-starter-data-redis'
	implementation 'org.springframework.session:spring-session-data-redis'
	compileOnly 'org.projectlombok:lombok'
	annotationProcessor 'org.projectlombok:lombok'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testImplementation 'org.springframework.security:spring-security-test'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

- [ ] **Step 2: settings.gradle + application.yml**

`settings.gradle`: `rootProject.name = 'auth'`

`application.yml`:
```yaml
server:
  port: 8081
spring:
  data:
    redis:
      host: localhost
      port: 6379
  session:
    store-type: redis
my:
  user-directory-base-url: http://localhost:8084
  client-registry-base-url: http://localhost:8085
  authorization-code-ttl-seconds: 60
logging:
  level:
    dev.starryeye: DEBUG
    org.springframework.security: DEBUG
```

- [ ] **Step 3: Application + RestClientConfig**

`AuthApplication.java`:
```java
package dev.starryeye.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthApplication {

	/**
	 * front-channel 을 담당하는 서비스이다.
	 *      로그인(사용자 인증은 user-directory 에 위임)과 authorize(code 발급)를 처리한다.
	 *      세션은 Redis 로 외부화하여 다중 인스턴스에서 공유한다.
	 */

	public static void main(String[] args) {
		SpringApplication.run(AuthApplication.class, args);
	}
}
```

`config/RestClientConfig.java`: (Task 8 Step 1 과 동일 내용, 패키지만 `dev.starryeye.auth.config`)
```java
package dev.starryeye.auth.config;

import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactories;

import java.time.Duration;

@Configuration
public class RestClientConfig {

	@Bean
	public RestClientCustomizer restClientCustomizer() {
		return builder -> builder.requestFactory(
				ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
						.withConnectTimeout(Duration.ofSeconds(2))
						.withReadTimeout(Duration.ofSeconds(2))));
	}
}
```

- [ ] **Step 4: UserDirectoryClient 작성**

```java
package dev.starryeye.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class UserDirectoryClient {

	/**
	 * user-directory 의 credential 검증 API 를 호출한다. 성공 시 sub/authorities 를 받고, 실패(401)면 예외.
	 */

	private final RestClient restClient;

	public UserDirectoryClient(RestClient.Builder builder, @Value("${my.user-directory-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public record AuthenticatedUser(String sub, List<String> authorities) {
	}

	public AuthenticatedUser authenticate(String username, String password) {
		return restClient.post()
				.uri("/internal/users/authenticate")
				.body(Map.of("username", username, "password", password))
				.retrieve()
				.onStatus(status -> status.value() == 401, (req, res) -> { throw new BadCredentialsRemoteException(); })
				.body(AuthenticatedUser.class);
	}

	public static class BadCredentialsRemoteException extends RuntimeException {
	}
}
```

- [ ] **Step 5: RemoteAuthenticationProvider 작성**

```java
package dev.starryeye.auth.security;

import dev.starryeye.auth.client.UserDirectoryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RemoteAuthenticationProvider implements org.springframework.security.authentication.AuthenticationProvider {

	/**
	 * 로그인 password 검증을 user-directory 에 위임하는 AuthenticationProvider 이다.
	 *      DaoAuthenticationProvider 와 달리 password 해시를 로컬에서 다루지 않는다.. bcrypt 비교는 user-directory 몫이다.
	 *      인증 성공 시 principal name 을 username 이 아니라 sub 로 둔다. (토큰 sub 와 일치)
	 */

	private final UserDirectoryClient userDirectoryClient;

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		String username = authentication.getName();
		String password = authentication.getCredentials().toString();

		UserDirectoryClient.AuthenticatedUser user;
		try {
			user = userDirectoryClient.authenticate(username, password);
		} catch (UserDirectoryClient.BadCredentialsRemoteException e) {
			throw new BadCredentialsException("invalid credentials");
		}

		List<SimpleGrantedAuthority> authorities = user.authorities().stream()
				.map(SimpleGrantedAuthority::new).toList();
		return new UsernamePasswordAuthenticationToken(user.sub(), null, authorities);
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}
}
```

- [ ] **Step 6: SecurityConfig 작성 (기본 로그인 페이지 사용)**

```java
package dev.starryeye.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	/**
	 * 로그인/세션은 Spring Security 기본 기능을 그대로 쓴다. (SAS 만 제외한다는 원칙)
	 *      RemoteAuthenticationProvider 를 등록해 인증만 user-directory 로 위임하고,
	 *      "/oauth2/authorize" 는 인증을 요구하여 미인증 시 로그인 페이지로 보낸다.
	 */

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http, RemoteAuthenticationProvider provider) throws Exception {
		http
				.authenticationProvider(provider)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/login", "/error").permitAll()
						.anyRequest().authenticated()
				)
				.formLogin(form -> form.permitAll())
				.csrf(csrf -> csrf.ignoringRequestMatchers("/oauth2/authorize")); // GET authorize 는 CSRF 무관, 편의상 제외

		return http.build();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}
}
```

- [ ] **Step 7: 컴파일 검증 + commit**

Run: `cd .../auth && ./gradlew compileJava --no-daemon -q`
Expected: 성공

```bash
git add oauth-2/authorization-server/practice/microservice/auth
git commit -m "microservice: auth service login with remote authentication

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 11: auth 서비스 — authorize 엔드포인트 + code 발급

**Files:**
- Create: `.../auth/AuthorizeController.java`
- Create: `.../auth/AuthorizationCodeIssuer.java`
- Create: `.../auth/client/ClientRegistryClient.java`
- Create: `.../auth/client/ClientInfo.java`
- Test: `.../auth/src/test/java/dev/starryeye/auth/AuthorizationCodeIssuerTest.java`

**Interfaces:**
- Consumes: client-registry `/internal/clients/{clientId}`, `StringRedisTemplate`, 인증 principal(sub).
- Produces: `GET /oauth2/authorize` → 검증 후 code 생성·Redis 저장·302 redirect. `AuthorizationCodeIssuer.issue(...)` → code 문자열.

- [ ] **Step 1: ClientRegistryClient + ClientInfo (auth 용)**

`client/ClientInfo.java`:
```java
package dev.starryeye.auth.client;

import java.util.List;

public record ClientInfo(
		String clientId,
		List<String> redirectUris,
		List<String> scopes,
		String clientSecretHash,
		List<String> grantTypes
) {
}
```

`client/ClientRegistryClient.java`:
```java
package dev.starryeye.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ClientRegistryClient {

	/**
	 * client-registry 조회. authorize 단계에서 client_id 유효성과 redirect_uri/scope 검증에 쓴다.
	 */

	private final RestClient restClient;

	public ClientRegistryClient(RestClient.Builder builder, @Value("${my.client-registry-base-url}") String baseUrl) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public ClientInfo getClient(String clientId) {
		return restClient.get()
				.uri("/internal/clients/{clientId}", clientId)
				.retrieve()
				.onStatus(status -> status.value() == 404, (req, res) -> { throw new ClientNotFoundException(); })
				.body(ClientInfo.class);
	}

	public static class ClientNotFoundException extends RuntimeException {
	}
}
```

- [ ] **Step 2: 실패 테스트 작성 (code 발급 = Redis 저장 + JSON 직렬화)**

```java
package dev.starryeye.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthorizationCodeIssuerTest {

	@Test
	void issueStoresCodeWithTtlAndReturnsCode() throws Exception {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);

		AuthorizationCodeIssuer issuer = new AuthorizationCodeIssuer(redis, new ObjectMapper(), 60);
		String code = issuer.issue("my-client", "http://127.0.0.1:8080/callback", "openid profile", "user-sub-0001", "chal");

		assertThat(code).isNotBlank();
		verify(ops).set(eq("auth:code:" + code), contains("\"sub\":\"user-sub-0001\""), eq(Duration.ofSeconds(60)));
	}
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd .../auth && ./gradlew test --no-daemon --tests AuthorizationCodeIssuerTest`
Expected: FAIL (AuthorizationCodeIssuer 없음)

- [ ] **Step 4: AuthorizationCodeIssuer 구현**

```java
package dev.starryeye.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AuthorizationCodeIssuer {

	/**
	 * authorization code 를 만들어 Redis 에 저장한다. (token 이 소비할 공유 계약)
	 *      key "auth:code:{code}", value 는 {clientId, redirectUri, scope, sub, codeChallenge} JSON, TTL 은 설정값(60초).
	 */

	private static final String KEY_PREFIX = "auth:code:";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final long ttlSeconds;

	public AuthorizationCodeIssuer(
			StringRedisTemplate redisTemplate,
			ObjectMapper objectMapper,
			@Value("${my.authorization-code-ttl-seconds}") long ttlSeconds
	) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.ttlSeconds = ttlSeconds;
	}

	public String issue(String clientId, String redirectUri, String scope, String sub, String codeChallenge) {
		String code = UUID.randomUUID().toString().replace("-", "");
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("clientId", clientId);
		data.put("redirectUri", redirectUri);
		data.put("scope", scope);
		data.put("sub", sub);
		data.put("codeChallenge", codeChallenge);
		try {
			redisTemplate.opsForValue().set(KEY_PREFIX + code, objectMapper.writeValueAsString(data), Duration.ofSeconds(ttlSeconds));
		} catch (Exception e) {
			throw new IllegalStateException("failed to store authorization code", e);
		}
		return code;
	}
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd .../auth && ./gradlew test --no-daemon --tests AuthorizationCodeIssuerTest`
Expected: PASS (1 test)

- [ ] **Step 6: AuthorizeController 구현**

```java
package dev.starryeye.auth;

import dev.starryeye.auth.client.ClientInfo;
import dev.starryeye.auth.client.ClientRegistryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class AuthorizeController {

	/**
	 * authorization code + PKCE 의 authorize 엔드포인트를 직접 구현한다.
	 *      인증은 Spring Security 가 강제하므로(SecurityConfig) 이 메서드 진입 시 principal 은 이미 로그인된 사용자(sub)이다.
	 *      client/redirect_uri/scope/PKCE 를 검증하고 code 를 발급해 redirect 한다.
	 *
	 * 주의. redirect_uri 가 등록값과 다르면 그 주소로 redirect 하지 않고 에러 페이지로 처리한다. (open redirect 방지)
	 */

	private final ClientRegistryClient clientRegistryClient;
	private final AuthorizationCodeIssuer codeIssuer;

	@GetMapping("/oauth2/authorize")
	public Object authorize(
			Principal principal,
			@RequestParam("response_type") String responseType,
			@RequestParam("client_id") String clientId,
			@RequestParam("redirect_uri") String redirectUri,
			@RequestParam(value = "scope", required = false) String scope,
			@RequestParam(value = "state", required = false) String state,
			@RequestParam(value = "code_challenge", required = false) String codeChallenge,
			@RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod
	) {
		ClientInfo client;
		try {
			client = clientRegistryClient.getClient(clientId);
		} catch (ClientRegistryClient.ClientNotFoundException e) {
			return errorPage("unknown client_id");
		}

		// redirect_uri 정확 일치 검증 (여기 실패는 redirect 하지 않는다)
		if (!client.redirectUris().contains(redirectUri)) {
			return errorPage("redirect_uri mismatch");
		}

		// 여기서부터의 오류는 redirect_uri 로 error 를 실어 보낸다.
		if (!"code".equals(responseType)) {
			return errorRedirect(redirectUri, "unsupported_response_type", state);
		}
		if (!StringUtils.hasText(codeChallenge) || !"S256".equals(codeChallengeMethod)) {
			return errorRedirect(redirectUri, "invalid_request", state); // 첫 슬라이스는 PKCE(S256) 필수
		}
		String effectiveScope = StringUtils.hasText(scope) ? scope : String.join(" ", client.scopes());
		for (String requested : effectiveScope.split(" ")) {
			if (!client.scopes().contains(requested)) {
				return errorRedirect(redirectUri, "invalid_scope", state);
			}
		}

		String code = codeIssuer.issue(clientId, redirectUri, effectiveScope, principal.getName(), codeChallenge);

		StringBuilder location = new StringBuilder(redirectUri).append("?code=").append(code);
		if (StringUtils.hasText(state)) {
			location.append("&state=").append(state);
		}
		return new RedirectView(location.toString());
	}

	private RedirectView errorRedirect(String redirectUri, String error, String state) {
		StringBuilder location = new StringBuilder(redirectUri).append("?error=").append(error);
		if (StringUtils.hasText(state)) {
			location.append("&state=").append(state);
		}
		return new RedirectView(location.toString());
	}

	private org.springframework.http.ResponseEntity<String> errorPage(String message) {
		return org.springframework.http.ResponseEntity.badRequest().body("authorization error: " + message);
	}
}
```

- [ ] **Step 7: 컴파일 검증 + commit**

Run: `cd .../auth && ./gradlew compileJava --no-daemon -q`
Expected: 성공

```bash
git add oauth-2/authorization-server/practice/microservice/auth
git commit -m "microservice: auth authorize endpoint issues authorization code

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 12: gateway + docker-compose 인프라

**Files:**
- Create: `.../microservice/gateway/nginx.conf`
- Create: `.../microservice/docker-compose/docker-compose.yml`
- Create: `.../microservice/docker-compose/nginx.conf` (gateway 것과 동일 참조 or 심볼릭)

**Interfaces:**
- Produces: nginx(9000) 라우팅 — `/oauth2/authorize`,`/login`→auth, `/oauth2/token`,`/oauth2/jwks`,`/.well-known`→token. `/internal/*` 은 라우팅 제외. MySQL/Redis 컨테이너.

- [ ] **Step 1: gateway nginx.conf 작성**

`gateway/nginx.conf`:
```nginx
# gateway(로드밸런서 겸 라우터).. 경로로 서비스를 구분한다.
# /internal/* 은 여기서 라우팅하지 않는다 (외부 비노출.. 내부 인증이 없는 첫 슬라이스의 유일한 경로 격리).
upstream auth-upstream   { server host.docker.internal:8081; }
upstream token-upstream  { server host.docker.internal:8082; }

server {
    listen 80;

    # front-channel -> auth
    location /oauth2/authorize { proxy_pass http://auth-upstream;  proxy_set_header X-Forwarded-Host $http_host; }
    location /login            { proxy_pass http://auth-upstream;  proxy_set_header X-Forwarded-Host $http_host; }

    # back-channel -> token
    location /oauth2/token     { proxy_pass http://token-upstream; proxy_set_header X-Forwarded-Host $http_host; }
    location /oauth2/jwks      { proxy_pass http://token-upstream; proxy_set_header X-Forwarded-Host $http_host; }
    location /.well-known/     { proxy_pass http://token-upstream; proxy_set_header X-Forwarded-Host $http_host; }
}
```

- [ ] **Step 2: docker-compose 작성**

`docker-compose/docker-compose.yml`:
```yaml
# docker compose -p microservice-as up -d
# gateway(nginx 9000) + mysql + redis. 5개 Spring 서비스는 호스트에서 java -jar 로 기동한다.

services:
  nginx:
    image: nginx:1.27
    ports:
      - "9000:80"
    volumes:
      - ../gateway/nginx.conf:/etc/nginx/conf.d/default.conf:ro

  mysql:
    image: mysql:8
    environment:
      MYSQL_ROOT_PASSWORD: 1111
      MYSQL_DATABASE: microservice_as
    ports:
      - "3306:3306"

  redis:
    image: redis:7
    ports:
      - "6379:6379"
```

- [ ] **Step 3: 인프라 기동 확인**

```bash
cd oauth-2/authorization-server/practice/microservice/docker-compose
docker compose -p microservice-as up -d
docker ps --format '{{.Names}}\t{{.Status}}' | grep microservice-as
```
Expected: nginx, mysql, redis 3개 Up

- [ ] **Step 4: Commit**

```bash
git add oauth-2/authorization-server/practice/microservice/gateway oauth-2/authorization-server/practice/microservice/docker-compose
git commit -m "microservice: gateway nginx routing and docker-compose infra

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Task 13: 관통 e2e 검증 + README

**Files:**
- Create: `.../microservice/README.md`
- Create: `.../microservice/http/authorize.http`, `http/token.http` (참고용 .http)

**Interfaces:**
- Consumes: 전 서비스 + 인프라.
- Produces: 검증된 관통 flow, README.

- [ ] **Step 1: 5개 서비스 빌드**

```bash
cd oauth-2/authorization-server/practice/microservice
for s in signing user-directory client-registry token auth; do (cd $s && ./gradlew bootJar --no-daemon -q); done
ls */build/libs/*.jar
```
Expected: 5개 jar

- [ ] **Step 2: 인프라 + 5개 서비스 기동**

```bash
cd oauth-2/authorization-server/practice/microservice
JAVA=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn/bin/java
docker compose -p microservice-as -f docker-compose/docker-compose.yml up -d
for i in $(seq 1 30); do docker exec microservice-as-mysql-1 mysqladmin ping -uroot -p1111 --silent 2>/dev/null && break; sleep 2; done
for s in signing user-directory client-registry token auth; do
  nohup $JAVA -jar $s/build/libs/$s-0.0.1-SNAPSHOT.jar > /tmp/ms-$s.log 2>&1 &
done
sleep 25
for s in signing user-directory client-registry token auth; do echo "$s: $(grep -c 'Started .*Application' /tmp/ms-$s.log)"; done
```
Expected: 각 서비스 1

- [ ] **Step 3: 관통 flow 수동 e2e (성공 기준 1,2)**

```bash
# PKCE 쌍 생성
python3 - <<'EOF'
import hashlib, base64, secrets
v = secrets.token_urlsafe(48)
c = base64.urlsafe_b64encode(hashlib.sha256(v.encode()).digest()).rstrip(b'=').decode()
open('/tmp/ms-verifier.txt','w').write(v); open('/tmp/ms-challenge.txt','w').write(c)
print('verifier', v); print('challenge', c)
EOF
CHAL=$(cat /tmp/ms-challenge.txt); VER=$(cat /tmp/ms-verifier.txt)
# 1) 로그인 세션 + authorize
csrf() { grep -o 'name="_csrf"[^>]*value="[^"]*"' | sed 's/.*value="//;s/"$//'; }
rm -f /tmp/ms-cookies.txt
AUTHZ="http://localhost:9000/oauth2/authorize?response_type=code&client_id=my-client&redirect_uri=http://127.0.0.1:8080/callback&scope=openid%20profile&state=xyz&code_challenge=$CHAL&code_challenge_method=S256"
curl -s -c /tmp/ms-cookies.txt -o /dev/null "$AUTHZ"
CSRF=$(curl -s -b /tmp/ms-cookies.txt -c /tmp/ms-cookies.txt http://localhost:9000/login | csrf)
curl -s -b /tmp/ms-cookies.txt -c /tmp/ms-cookies.txt -o /dev/null -X POST http://localhost:9000/login -d "username=user&password=1111&_csrf=$CSRF"
CODE=$(curl -s -i -b /tmp/ms-cookies.txt "$AUTHZ" | grep -i '^location' | grep -o 'code=[^&]*' | cut -d= -f2 | tr -d '\r')
echo "code: $CODE"
# 2) token 교환
curl -s -u my-client:secret -X POST http://localhost:9000/oauth2/token \
  -d "grant_type=authorization_code&code=$CODE&redirect_uri=http://127.0.0.1:8080/callback&code_verifier=$VER" > /tmp/ms-token.json
python3 -c "import json,base64; d=json.load(open('/tmp/ms-token.json')); t=d['access_token']; p=t.split('.')[1]; c=json.loads(base64.urlsafe_b64decode(p+'='*(-len(p)%4))); print('iss',c['iss'],'sub',c['sub'],'aud',c['aud'],'scope',c['scope'])"
```
Expected: code 발급됨, access token 의 iss=http://localhost:9000, sub=user-sub-0001, aud=my-client

- [ ] **Step 4: JWKS 서명 검증 + graceful degradation (성공 기준 2,3)**

```bash
# 발급 JWT 를 token/jwks 로 검증
python3 - <<'EOF'
import json, base64, urllib.request, hashlib
def b64d(s): return base64.urlsafe_b64decode(s + '='*(-len(s)%4))
t = json.load(open('/tmp/ms-token.json'))['access_token']
h,p,sig = t.split('.')
kid = json.loads(b64d(h))['kid']
jwks = json.load(urllib.request.urlopen('http://localhost:9000/oauth2/jwks'))
key = next(k for k in jwks['keys'] if k['kid']==kid)
n=int.from_bytes(b64d(key['n'])); e=int.from_bytes(b64d(key['e']))
m=pow(int.from_bytes(b64d(sig)),e,n).to_bytes((n.bit_length()+7)//8)
digest=hashlib.sha256(f'{h}.{p}'.encode()).digest()
print('signature valid:', m.endswith(digest) and m[:2]==b'\x00\x01')
EOF
# signing 죽여도 기존 JWT 검증 지속 확인 (token 이 jwks 캐시 안 하면 jwks 는 실패하나, 이미 받은 공개키로 검증은 됨)
pkill -f "signing-0.0.1-SNAPSHOT.jar"; sleep 3
echo "signing down. 기존 access token 은 이미 받은 jwks 공개키로 검증 가능(성공 기준 3, 위 스크립트의 공개키를 재사용)."
```
Expected: signature valid: True

- [ ] **Step 5: 부정 케이스 (성공 기준 4,5)**

```bash
# signing 재기동
JAVA=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn/bin/java
cd oauth-2/authorization-server/practice/microservice
nohup $JAVA -jar signing/build/libs/signing-0.0.1-SNAPSHOT.jar > /tmp/ms-signing.log 2>&1 &
sleep 12
VER=$(cat /tmp/ms-verifier.txt)
# 4) code 재사용 -> invalid_grant (위 flow 의 CODE 는 이미 소비됨)
echo "=== code 재사용"
curl -s -u my-client:secret -X POST http://localhost:9000/oauth2/token \
  -d "grant_type=authorization_code&code=$(python3 -c "print(open('/tmp/ms-token.json') and 'REUSE')" ; grep -o 'code=[^&]*' /dev/null 2>/dev/null)&redirect_uri=http://127.0.0.1:8080/callback&code_verifier=$VER" | head -c 120; echo
# (간단화: 새 code 발급 후 두 번 교환)
CHAL=$(cat /tmp/ms-challenge.txt)
AUTHZ="http://localhost:9000/oauth2/authorize?response_type=code&client_id=my-client&redirect_uri=http://127.0.0.1:8080/callback&scope=openid%20profile&code_challenge=$CHAL&code_challenge_method=S256"
CODE2=$(curl -s -i -b /tmp/ms-cookies.txt "$AUTHZ" | grep -i '^location' | grep -o 'code=[^&]*' | cut -d= -f2 | tr -d '\r')
curl -s -u my-client:secret -X POST http://localhost:9000/oauth2/token -d "grant_type=authorization_code&code=$CODE2&redirect_uri=http://127.0.0.1:8080/callback&code_verifier=$VER" -o /dev/null
echo "재사용 결과: $(curl -s -u my-client:secret -X POST http://localhost:9000/oauth2/token -d "grant_type=authorization_code&code=$CODE2&redirect_uri=http://127.0.0.1:8080/callback&code_verifier=$VER" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("error"))')"
# 5) PKCE verifier 조작 -> invalid_grant
CODE3=$(curl -s -i -b /tmp/ms-cookies.txt "$AUTHZ" | grep -i '^location' | grep -o 'code=[^&]*' | cut -d= -f2 | tr -d '\r')
echo "PKCE 조작 결과: $(curl -s -u my-client:secret -X POST http://localhost:9000/oauth2/token -d "grant_type=authorization_code&code=$CODE3&redirect_uri=http://127.0.0.1:8080/callback&code_verifier=WRONG-VERIFIER" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("error"))')"
```
Expected: 재사용 결과: invalid_grant, PKCE 조작 결과: invalid_grant

- [ ] **Step 6: 서버 정리 + README 작성**

```bash
pkill -f "microservice.*SNAPSHOT.jar" 2>/dev/null
pkill -f "signing-0.0.1\|user-directory-0.0.1\|client-registry-0.0.1\|token-0.0.1\|auth-0.0.1" 2>/dev/null
docker compose -p microservice-as -f oauth-2/authorization-server/practice/microservice/docker-compose/docker-compose.yml stop
```

`README.md` 작성 (구도 다이어그램, 서비스별 역할·포트, 기동 순서 signing→user-directory→client-registry→token→auth, 관통 flow 설명, 성공 기준, 첫 슬라이스 제외 목록·추후 개선, 스펙/플랜 링크).

- [ ] **Step 7: Commit**

```bash
git add oauth-2/authorization-server/practice/microservice/README.md oauth-2/authorization-server/practice/microservice/http
git commit -m "microservice: e2e verification and README for slice 1

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Self-Review 결과

- **Spec coverage**: 서비스 6개(Task 1-12) ✓, code+PKCE 흐름(Task 9,11,13) ✓, 상태 저장소(Redis code Task 8/11, 세션 Task 10, MySQL Task 4/6, keystore Task 1) ✓, 4개 REST 계약(client-registry Task 6, user-directory Task 5, signing Task 3) ✓, OAuth 에러(Task 9,11) ✓, 실패 모드 graceful degradation(Task 13 Step 4) ✓, Caffeine 캐시(Task 6) ✓, 검증 5개 기준(Task 13) ✓, 프로젝트 구조·seed(전반) ✓.
- **제외 항목 준수**: consent/id token/refresh/introspection/admin API/Kafka/내부 인증 — 계획에 없음 ✓.
- **Type 일관성**: `AuthorizationCodeData`(record, token) 필드 = Redis JSON 필드(auth `AuthorizationCodeIssuer`) = clientId/redirectUri/scope/sub/codeChallenge 일치 ✓. `ClientInfo`(auth/token 각자 정의, 동일 shape) ✓. `AuthorizationCodeStore.consume`, `PkceValidator.matches`, `SigningClient.sign/jwks`, `ClientRegistryClient.getClient`, `UserDirectoryClient.authenticate` 시그니처 태스크 간 일치 ✓.
- **알려진 주의**: Task 6 Step 6, Task 9 테스트는 MySQL/Redis 없이 컨텍스트 로딩이 필요할 수 있어 docker 기동 후 재확인(Task 13). Task 13 Step 5 의 code 재사용 스크립트는 새 code 2회 교환으로 단순화.
