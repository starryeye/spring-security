# 마이크로서비스 인가 서버 슬라이스 7 — 서비스별 DB 분리와 outbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 서비스별 스키마·계정으로 데이터 소유권을 구조로 만들고, 그래서 불가능해진 "로그아웃 시 refresh token 폐기"를 Kafka 이벤트 + outbox 로 푼다.

**Architecture:** MySQL 한 인스턴스 안에 스키마 5개와 계정 5개를 만들어 각 서비스가 자기 스키마만 보게 한다. `refresh_tokens` 에 `sid` 를 추가해 폐기 범위를 세션 단위로 만든다. `session` 이 로그아웃 사실을 Kafka 로 발행하고 `token-state` 가 소비해 폐기한다. 먼저 직접 발행으로 만들어 두 실패(로그아웃이 통째로 롤백, 유령 이벤트)를 재현한 뒤 outbox 로 닫는다.

**Tech Stack:** Spring Boot 3.4.5, Java 21, Spring for Apache Kafka, MySQL 8, Apache Kafka(KRaft), h2(테스트), EmbeddedKafka(테스트)

**설계 문서:** [2026-08-08-microservice-db-per-service-outbox-slice7-design.md](../specs/2026-08-08-microservice-db-per-service-outbox-slice7-design.md)

## Global Constraints

- Spring Boot **3.4.5**, `io.spring.dependency-management` **1.1.7**, Java toolchain **21**. 새 모듈을 만들지 않으므로 기존 `build.gradle` 의 이 값들을 바꾸지 않는다.
- **빌드 명령**: `./gradlew` 가 이 환경에서 SIGKILL(exit 137) 되는 경우가 있다. 그러면 각 서비스 디렉토리에서 `java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon` 로 우회한다. `JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn` 를 먼저 설정한다(PATH 의 java 는 17이다).
- **주석은 한국어**, 기존 파일의 문체를 따른다. 함정·판단 근거는 `주의.` 로 시작하는 문단으로 적는다. 코드가 하는 일보다 **한 칸이라도 세게 주장하지 않는다** — 이 저장소에서 반복된 실패 모드다.
- **`git add` 는 반드시 경로를 명시한다.** `-A`, `-a`, `.` 는 금지다 — 저장소에 영구 미커밋 상태로 두는 자격증명 파일이 있다.
- **커밋마다 `git push origin main` 까지** 한 흐름으로 한다. 강제 푸시는 하지 않는다.
- 커밋 메시지 말미에 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` 를 넣는다.
- **검증하지 않은 것을 검증했다고 쓰지 않는다.** README·주석에 실행하지 않은 결과를 적지 않는다.
- 테스트는 **실제로 물어야** 한다. 새 테스트를 추가하면 구현을 일부러 되돌려 그 테스트가 실패하는 것을 확인하고, `git diff` 로 되돌린 증거를 남긴 뒤 복구한다.

## 이 슬라이스가 손대지 않는 것

- 사용자·client 삭제 기능(존재하지 않음), MySQL 인스턴스 분리, `oidc_sessions` purge, Redis 공유 구조, CDC, 저장소 이기종화
- `auth` 서비스는 **한 줄도 바꾸지 않는다** (Task 5 의 알려진 한계 참고)

---

## File Structure

### 새로 만드는 파일

| 경로 | 책임 |
|---|---|
| `docker-compose/mysql-init/01-schemas-and-accounts.sql` | 스키마 5개 + 계정 5개 + 권한. MySQL 최초 기동 시 1회 실행 |
| `session/src/main/java/dev/starryeye/session/event/SessionLoggedOutEvent.java` | 이벤트 페이로드 record |
| `session/src/main/java/dev/starryeye/session/event/LogoutEventPublisher.java` | Kafka 발행(Task 5 직접 발행 → Task 7 outbox 경유로 바뀜) |
| `session/src/main/java/dev/starryeye/session/event/KafkaTopicConfig.java` | `NewTopic` 빈 — 파티션 3 |
| `session/src/main/java/dev/starryeye/session/jpa/OutboxEntity.java` | outbox 행 |
| `session/src/main/java/dev/starryeye/session/jpa/OutboxEntityRepository.java` | 미발행 행 조회·표시 |
| `session/src/main/java/dev/starryeye/session/outbox/OutboxPublisher.java` | `@Scheduled` 폴러 |
| `token-state/src/main/java/dev/starryeye/token_state/event/SessionLoggedOutEvent.java` | 소비자 쪽 페이로드 record |
| `token-state/src/main/java/dev/starryeye/token_state/event/SessionLoggedOutConsumer.java` | 소비 → `revokeBySid` |
| `token-state/src/main/java/dev/starryeye/token_state/event/KafkaConsumerConfig.java` | 수동 커밋 + 재시도 제한 + DLT |

### 고치는 파일

| 경로 | 무엇을 |
|---|---|
| `docker-compose/docker-compose.yml` | `MYSQL_DATABASE` 제거 · init 볼륨 · kafka 추가 |
| `user-directory`·`client-registry`·`consent`·`token-state`·`session` 의 `src/main/resources/application.yml` | datasource url·username·password |
| `token-state/.../jpa/RefreshTokenEntity.java` | `sid` 컬럼 |
| `token-state/.../jpa/RefreshTokenEntityRepository.java` | `revokeActiveBySid` |
| `token-state/.../dto/IssueRequest.java` | `sid` |
| `token-state/.../RefreshTokenService.java` | `issue` 시그니처 · `rotate` 승계 · `revokeBySid` |
| `token-state/.../RotateResult.java` | `sid` |
| `token-state/.../RefreshTokenController.java` | `issue` 위임 |
| `token/.../client/TokenStateClient.java` | `issue` 에 `sid` (Map.of → LinkedHashMap) |
| `token/.../client/RotateResult.java` | `sid` |
| `token/.../TokenEndpointController.java` | `issue` 호출에 `data.sid()` |
| `token/.../RefreshTokenGrantService.java` | id token 에 `rotation.sid()` |
| `session/.../SessionService.java` | 발행 호출 |
| `session/build.gradle`·`token-state/build.gradle` | spring-kafka |
| `README.md` | 기동 절차 · 한계 · 검증 결과 |

---

## Task 1: 스키마·계정 분리

**Files:**
- Create: `docker-compose/mysql-init/01-schemas-and-accounts.sql`
- Modify: `docker-compose/docker-compose.yml`
- Modify: `user-directory/src/main/resources/application.yml`
- Modify: `client-registry/src/main/resources/application.yml`
- Modify: `consent/src/main/resources/application.yml`
- Modify: `token-state/src/main/resources/application.yml`
- Modify: `session/src/main/resources/application.yml`

**Interfaces:**
- Consumes: 없음 (첫 태스크)
- Produces: 스키마 이름 `ms_user_directory`·`ms_client_registry`·`ms_consent`·`ms_token_state`·`ms_session`, 계정 `svc_<서비스명>` / 비밀번호 `pw_<서비스명>`. 이후 태스크는 이 이름을 그대로 쓴다.

**이 태스크는 자동화 테스트가 없다.** 검증은 실제로 컨테이너를 띄워 권한 오류를 확인하는 것이다. 단계 5~8이 그 테스트에 해당하므로 **건너뛰지 않는다.**

- [ ] **Step 1: 초기화 SQL 작성**

`docker-compose/mysql-init/01-schemas-and-accounts.sql` 을 만든다.

```sql
-- 서비스마다 전용 스키마와 전용 계정을 만든다.
--
-- 주의. 각 계정에는 자기 스키마에만 GRANT 를 준다. 이것이 이 슬라이스의 요점이다 — 소유권을
--      "남의 테이블을 읽지 말자"는 규율이 아니라 "읽을 수 없다"는 구조로 바꾼다. 코드가 실수로
--      남의 테이블을 참조하면 컴파일은 되어도 런타임에 권한 오류로 막힌다.
--
-- 주의. root 는 지우지 않는다. 이 스크립트 자체가 root 로 실행되고, InnoDB 잠금 의미론 검증용
--      테스트(RefreshTokenServiceMySqlLockSemanticsTest)가 token_state_test 스키마를 root 로 쓴다.
--      애플리케이션 설정에서는 root 가 사라진다.

CREATE DATABASE IF NOT EXISTS ms_user_directory  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ms_client_registry CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ms_consent         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ms_token_state     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ms_session         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 잠금 의미론 검증 전용. 운영 스키마와 격리된 채로 유지한다.
CREATE DATABASE IF NOT EXISTS token_state_test   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'svc_user_directory'@'%'  IDENTIFIED BY 'pw_user_directory';
CREATE USER IF NOT EXISTS 'svc_client_registry'@'%' IDENTIFIED BY 'pw_client_registry';
CREATE USER IF NOT EXISTS 'svc_consent'@'%'         IDENTIFIED BY 'pw_consent';
CREATE USER IF NOT EXISTS 'svc_token_state'@'%'     IDENTIFIED BY 'pw_token_state';
CREATE USER IF NOT EXISTS 'svc_session'@'%'         IDENTIFIED BY 'pw_session';

GRANT ALL PRIVILEGES ON ms_user_directory.*  TO 'svc_user_directory'@'%';
GRANT ALL PRIVILEGES ON ms_client_registry.* TO 'svc_client_registry'@'%';
GRANT ALL PRIVILEGES ON ms_consent.*         TO 'svc_consent'@'%';
GRANT ALL PRIVILEGES ON ms_token_state.*     TO 'svc_token_state'@'%';
GRANT ALL PRIVILEGES ON ms_session.*         TO 'svc_session'@'%';

FLUSH PRIVILEGES;
```

- [ ] **Step 2: docker-compose 수정**

`docker-compose/docker-compose.yml` 의 `mysql` 블록을 아래로 바꾼다. `MYSQL_DATABASE` 를 **제거**하고 초기화 디렉토리를 마운트한다.

```yaml
  mysql:
    image: mysql:8
    environment:
      MYSQL_ROOT_PASSWORD: 1111
    ports:
      - "3306:3306"
    volumes:
      - ./mysql-init:/docker-entrypoint-initdb.d:ro
```

파일 머리 주석도 고친다.

```yaml
# docker compose -p microservice-as up -d
# gateway(nginx 9000) + mysql + redis. 5개 Spring 서비스는 호스트에서 java -jar 로 기동한다.
#
# 주의. mysql-init 는 데이터 디렉토리가 비어 있을 때만 실행된다. 스키마·계정을 바꾸면
#      `docker compose -p microservice-as down -v` 로 볼륨까지 지우고 다시 띄워야 반영된다.
```

- [ ] **Step 3: 5개 서비스의 datasource 교체**

각 `src/main/resources/application.yml` 의 `spring.datasource` 세 줄을 바꾼다. **url 의 스키마 이름·username·password 만 다르고 나머지는 같다.**

`user-directory`:
```yaml
  datasource:
    url: jdbc:mysql://localhost:3306/ms_user_directory?useSSL=false&allowPublicKeyRetrieval=true
    username: svc_user_directory
    password: pw_user_directory
```

`client-registry`:
```yaml
  datasource:
    url: jdbc:mysql://localhost:3306/ms_client_registry?useSSL=false&allowPublicKeyRetrieval=true
    username: svc_client_registry
    password: pw_client_registry
```

`consent`:
```yaml
  datasource:
    url: jdbc:mysql://localhost:3306/ms_consent?useSSL=false&allowPublicKeyRetrieval=true
    username: svc_consent
    password: pw_consent
```

`token-state`:
```yaml
  datasource:
    url: jdbc:mysql://localhost:3306/ms_token_state?useSSL=false&allowPublicKeyRetrieval=true
    username: svc_token_state
    password: pw_token_state
```

`session`:
```yaml
  datasource:
    url: jdbc:mysql://localhost:3306/ms_session?useSSL=false&allowPublicKeyRetrieval=true
    username: svc_session
    password: pw_session
```

> `useSSL=false&allowPublicKeyRetrieval=true` 는 MySQL 8 의 `caching_sha2_password` 가 평문 연결에서 최초 인증할 때 공개키를 받아오도록 허용하는 파라미터다. 기존 `application-mysql-verify.yml` 이 같은 이유로 이미 쓰고 있다.

- [ ] **Step 4: 기존 컨테이너·볼륨 제거 후 재기동**

```bash
cd oauth-2/authorization-server/practice/microservice
docker compose -p microservice-as -f docker-compose/docker-compose.yml down -v
docker compose -p microservice-as -f docker-compose/docker-compose.yml up -d mysql
```

Expected: mysql 컨테이너가 뜬다. 초기화 SQL 이 도는 데 몇 초 걸린다.

- [ ] **Step 5: 스키마 5개가 생겼는지 확인**

```bash
docker exec -i microservice-as-mysql-1 mysql -uroot -p1111 -e "SHOW DATABASES LIKE 'ms\_%'"
```

Expected: `ms_client_registry`, `ms_consent`, `ms_session`, `ms_token_state`, `ms_user_directory` 5줄.

- [ ] **Step 6: 자기 스키마에는 접근되는지 확인**

```bash
docker exec -i microservice-as-mysql-1 mysql -usvc_user_directory -ppw_user_directory \
  -e "SELECT DATABASE() FROM DUAL; SHOW DATABASES"
```

Expected: `SHOW DATABASES` 결과에 `ms_user_directory` 는 보이고 `ms_client_registry` 는 **보이지 않는다**.

- [ ] **Step 7: 남의 스키마가 막히는지 확인 — 이 태스크의 핵심 검증**

```bash
docker exec -i microservice-as-mysql-1 mysql -usvc_user_directory -ppw_user_directory \
  -e "SELECT 1 FROM ms_client_registry.clients LIMIT 1"
```

Expected: **오류로 실패한다.** `ERROR 1044 (42000): Access denied for user 'svc_user_directory'@'%' to database 'ms_client_registry'` 형태다(MySQL 버전에 따라 `ERROR 1142` 로 나올 수도 있다 — 어느 쪽이든 접근 거부다). **성공하면 이 태스크는 실패한 것이다.**

이 명령과 출력을 그대로 보고서에 옮긴다.

- [ ] **Step 8: 5개 서비스를 기동해 테이블이 각자 스키마에 생기는지 확인**

```bash
export JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn
export PATH="$JAVA_HOME/bin:$PATH"
for m in user-directory client-registry consent token-state session; do
  (cd $m && java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain bootJar --no-daemon -q)
done
for m in user-directory client-registry consent token-state session; do
  (cd $m && nohup java -jar build/libs/$m-0.0.1-SNAPSHOT.jar > /tmp/$m.log 2>&1 &)
done
sleep 25
docker exec -i microservice-as-mysql-1 mysql -uroot -p1111 -e \
  "SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema LIKE 'ms\_%' ORDER BY 1"
```

Expected: 스키마마다 자기 테이블 하나씩 — `ms_client_registry/clients`, `ms_consent/consents`, `ms_session/oidc_sessions`, `ms_token_state/refresh_tokens`, `ms_user_directory/users`.

확인 후 기동한 프로세스를 정리한다: `pkill -f 'build/libs/.*-0.0.1-SNAPSHOT.jar'`

- [ ] **Step 9: 기존 테스트 회귀 확인**

테스트는 h2 를 쓰므로 영향이 없어야 한다.

```bash
export JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn
for m in user-directory client-registry consent token-state session; do
  (cd $m && $JAVA_HOME/bin/java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon) || echo "FAIL $m"
done
```

Expected: 5개 모듈 전부 BUILD SUCCESSFUL.

- [ ] **Step 10: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/docker-compose/mysql-init/01-schemas-and-accounts.sql \
        oauth-2/authorization-server/practice/microservice/docker-compose/docker-compose.yml \
        oauth-2/authorization-server/practice/microservice/user-directory/src/main/resources/application.yml \
        oauth-2/authorization-server/practice/microservice/client-registry/src/main/resources/application.yml \
        oauth-2/authorization-server/practice/microservice/consent/src/main/resources/application.yml \
        oauth-2/authorization-server/practice/microservice/token-state/src/main/resources/application.yml \
        oauth-2/authorization-server/practice/microservice/session/src/main/resources/application.yml
git commit -m "$(cat <<'EOF'
microservice: give each service its own schema and account

5개 서비스가 한 스키마를 root/1111 로 공유하고 있었다. 남의 테이블을 읽는
코드는 0건이었지만 막는 것이 아무것도 없었다 — 소유권이 규율뿐이었다.

스키마 5개와 계정 5개로 쪼갠다. 각 계정은 자기 스키마에만 권한이 있어
남의 테이블은 접근 자체가 거부된다. 확인:

  mysql -usvc_user_directory -ppw_user_directory \
    -e "SELECT 1 FROM ms_client_registry.clients LIMIT 1"
  ERROR 1044 (42000): Access denied ... to database 'ms_client_registry'

root 는 초기화와 잠금 의미론 테스트(token_state_test)용으로 남기고
애플리케이션 설정에서는 사라진다.

주의. mysql-init 는 데이터 디렉토리가 빌 때만 돈다. 기존 볼륨이 있으면
down -v 로 지우고 다시 띄워야 한다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 2: `refresh_tokens` 에 `sid` 를 넣는다 (token-state 내부)

**Files:**
- Modify: `token-state/src/main/java/dev/starryeye/token_state/jpa/RefreshTokenEntity.java`
- Modify: `token-state/src/main/java/dev/starryeye/token_state/dto/IssueRequest.java`
- Modify: `token-state/src/main/java/dev/starryeye/token_state/RefreshTokenService.java`
- Modify: `token-state/src/main/java/dev/starryeye/token_state/RotateResult.java`
- Modify: `token-state/src/main/java/dev/starryeye/token_state/RefreshTokenController.java`
- Test: `token-state/src/test/java/dev/starryeye/token_state/RefreshTokenServiceSidTest.java` (신규)

**Interfaces:**
- Consumes: Task 1 의 스키마 이름 (설정만, 코드 무관)
- Produces:
  - `RefreshTokenService.issue(String clientId, String sub, String scope, long authTime, String sid)` — `sid` 는 nullable
  - `RotateResult(RotateStatus status, String sub, String scope, long authTime, String refreshToken, long expiresAt, String sid)` — 필드 순서 그대로
  - `IssueRequest(String clientId, String sub, String scope, long authTime, String sid)`
  - `RefreshTokenEntity.getSid()`

- [ ] **Step 1: 실패하는 테스트 작성**

`token-state/src/test/java/dev/starryeye/token_state/RefreshTokenServiceSidTest.java` 를 만든다.

```java
package dev.starryeye.token_state;

import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RefreshTokenServiceSidTest {

	/**
	 * refresh token 레코드가 sid 를 보관해야 폐기 범위를 세션 단위로 잡을 수 있다. sub + client_id 로만
	 *      죽이면 다른 브라우저에서 로그인한 세션까지 함께 죽는다.
	 */

	@Autowired
	private RefreshTokenService service;

	@Autowired
	private RefreshTokenEntityRepository repository;

	@Test
	@DisplayName("발급하면 sid 가 저장된다")
	void issueStoresSid() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

		RefreshTokenEntity saved = repository.findByFamilyId(issued.familyId()).get(0);
		assertThat(saved.getSid()).isEqualTo("SID-A");
	}

	@Test
	@DisplayName("회전하면 새 행이 같은 sid 를 승계한다")
	void rotateInheritsSid() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

		RotateResult rotated = service.rotate(issued.refreshToken(), "my-client", null);
		assertThat(rotated.status()).isEqualTo(RotateStatus.ROTATED);

		List<RefreshTokenEntity> family = repository.findByFamilyId(issued.familyId());
		assertThat(family).hasSize(2);
		assertThat(family).allSatisfy(member -> assertThat(member.getSid()).isEqualTo("SID-A"));
	}

	@Test
	@DisplayName("회전 응답이 sid 를 알려준다 — refresh 로 재발급하는 id token 에 실어야 하기 때문이다")
	void rotateResultCarriesSid() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

		RotateResult rotated = service.rotate(issued.refreshToken(), "my-client", null);

		assertThat(rotated.sid()).isEqualTo("SID-A");
	}

	@Test
	@DisplayName("sid 가 없어도 발급된다 — client_credentials 처럼 세션이 없는 경로가 있다")
	void issueAllowsNullSid() {
		IssueResult issued = service.issue("my-client", "user-sub-0002", "offline_access", 1000L, null);

		RefreshTokenEntity saved = repository.findByFamilyId(issued.familyId()).get(0);
		assertThat(saved.getSid()).isNull();
	}
}
```

- [ ] **Step 2: 테스트가 컴파일 실패하는 것 확인**

```bash
cd oauth-2/authorization-server/practice/microservice/token-state
JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn \
  java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon --tests '*RefreshTokenServiceSidTest'
```

Expected: 컴파일 에러. `issue(...)` 인자 5개짜리가 없고 `getSid()`·`sid()` 도 없다.

- [ ] **Step 3: 엔티티에 `sid` 추가**

`RefreshTokenEntity.java` — `revokedReason` 필드 아래에 컬럼을 추가하고, `@Builder` 생성자의 **맨 뒤에** 파라미터를 추가한다.

```java
	@Column(name = "revoked_reason", length = 30)
	private String revokedReason;

	/**
	 * 이 refresh token 이 속한 OP 세션이다. 로그아웃 폐기의 범위를 세션 단위로 잡기 위해 보관한다.
	 *
	 * 주의. nullable 이다. client_credentials 는 refresh 를 내지 않지만, openid 없이 offline_access 만
	 *      받은 경로처럼 세션이 걸리지 않는 발급이 있을 수 있다. 그런 행은 세션 단위 폐기에 걸리지 않는다.
	 */
	@Column(name = "sid", length = 64)
	private String sid;

	@Builder
	private RefreshTokenEntity(String tokenHash, String familyId, String clientId, String sub, String scopes,
			long authTime, Instant issuedAt, Instant expiresAt, Instant familyExpiresAt, String sid) {
		this.tokenHash = tokenHash;
		this.familyId = familyId;
		this.clientId = clientId;
		this.sub = sub;
		this.scopes = scopes;
		this.authTime = authTime;
		this.issuedAt = issuedAt;
		this.expiresAt = expiresAt;
		this.familyExpiresAt = familyExpiresAt;
		this.sid = sid;
		this.status = RefreshTokenStatus.ACTIVE;
	}
```

> **주의.** 파라미터를 **맨 뒤에** 넣는다. 같은 타입(`String`)끼리 순서가 바뀌면 컴파일은 통과하고 값만 뒤바뀐다 — 슬라이스 4에서 같은 이유로 record 필드를 맨 뒤에 붙이기로 정했다.

- [ ] **Step 4: `IssueRequest` 에 `sid` 추가**

```java
package dev.starryeye.token_state.dto;

public record IssueRequest(String clientId, String sub, String scope, long authTime, String sid) {
}
```

- [ ] **Step 5: `RotateResult` 에 `sid` 추가**

```java
package dev.starryeye.token_state;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RotateResult(
		RotateStatus status,
		String sub,
		String scope,
		long authTime,
		String refreshToken,
		long expiresAt,
		String sid
) {

	public static RotateResult failed(RotateStatus status) {
		return new RotateResult(status, null, null, 0L, null, 0L, null);
	}
}
```

- [ ] **Step 6: `RefreshTokenService.issue` 와 `rotate` 수정**

`issue` 의 시그니처와 빌더에 `sid` 를 넣는다.

```java
	@Transactional
	public IssueResult issue(String clientId, String sub, String scope, long authTime, String sid) {
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
				.sid(sid)
				.build();
		repository.save(entity);

		return new IssueResult(token, entity.getExpiresAt().getEpochSecond(), familyId);
	}
```

`rotate` 의 `rotated` 빌더에 승계를 추가하고, 반환에 `sid` 를 싣는다.

```java
		RefreshTokenEntity rotated = RefreshTokenEntity.builder()
				.tokenHash(tokenGenerator.hash(token))
				.familyId(entity.getFamilyId())
				.clientId(entity.getClientId())
				.sub(entity.getSub())
				.scopes(entity.getScopes()) // 축소 요청이 있어도 저장 scope 는 그대로다
				.authTime(entity.getAuthTime())
				.issuedAt(now)
				.expiresAt(rotatedExpiresAt)
				.familyExpiresAt(entity.getFamilyExpiresAt()) // 절대 상한은 복사만, 연장하지 않는다
				.sid(entity.getSid()) // 계열 전체가 같은 세션에 속한다
				.build();
		repository.save(rotated);

		return new RotateResult(
				RotateStatus.ROTATED,
				entity.getSub(),
				toSpaceDelimited(entity.getScopes()),
				entity.getAuthTime(),
				token,
				rotated.getExpiresAt().getEpochSecond(),
				entity.getSid()
		);
```

- [ ] **Step 7: 컨트롤러 위임 수정**

`RefreshTokenController.issue` 가 `request.sid()` 를 넘기도록 고친다.

```java
	@PostMapping("/internal/refresh-tokens")
	public IssueResult issue(@RequestBody IssueRequest request) {
		return service.issue(request.clientId(), request.sub(), request.scope(), request.authTime(), request.sid());
	}
```

- [ ] **Step 8: 테스트 통과 확인**

```bash
cd oauth-2/authorization-server/practice/microservice/token-state
JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn \
  java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon
```

Expected: BUILD SUCCESSFUL. 기존 테스트도 전부 통과해야 한다(`issue` 호출부가 있으면 컴파일 에러가 나므로 함께 고친다).

- [ ] **Step 9: 테스트가 실제로 무는지 확인**

`rotate` 의 `.sid(entity.getSid())` 한 줄을 지우고 테스트를 다시 돌린다.

Expected: `rotateInheritsSid` 와 `rotateResultCarriesSid` 가 실패한다.

`git diff` 로 되돌린 내용을 확인해 보고서에 붙이고, 원복한 뒤 테스트가 다시 통과하는 것을 확인한다.

- [ ] **Step 10: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/token-state/src
git commit -m "$(cat <<'EOF'
token-state: carry sid on refresh tokens

폐기 범위를 세션 단위로 잡으려면 refresh token 레코드가 sid 를 알아야 한다.
sub + client_id 로만 죽이면 다른 브라우저에서 로그인한 세션까지 함께 죽는다.

- refresh_tokens.sid 추가 (nullable — 세션이 걸리지 않는 발급 경로가 있다)
- 회전은 sid 를 승계한다. 한 계열은 전부 같은 세션에 속한다
- RotateResult 가 sid 를 알려준다 — refresh 로 재발급하는 id token 에
  실어야 하기 때문이다

주의. @Builder 생성자의 파라미터는 맨 뒤에 넣었다. 같은 String 끼리
순서가 바뀌면 컴파일은 통과하고 값만 뒤바뀐다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 3: `token` 이 `sid` 를 실어 보내고, refresh 재발급 id token 에 `sid` 를 넣는다

**Files:**
- Modify: `token/src/main/java/dev/starryeye/token/client/TokenStateClient.java`
- Modify: `token/src/main/java/dev/starryeye/token/client/RotateResult.java`
- Modify: `token/src/main/java/dev/starryeye/token/TokenEndpointController.java`
- Modify: `token/src/main/java/dev/starryeye/token/RefreshTokenGrantService.java`
- Modify: `README.md`
- Test: `token/src/test/java/dev/starryeye/token/RefreshTokenGrantServiceSidTest.java` (신규)

**Interfaces:**
- Consumes: Task 2 의 `IssueRequest(clientId, sub, scope, authTime, sid)`, `RotateResult(..., sid)`
- Produces: `TokenStateClient.issue(String clientId, String sub, String scope, long authTime, String sid)`

- [ ] **Step 1: 실패하는 테스트 작성**

`token/src/test/java/dev/starryeye/token/RefreshTokenGrantServiceSidTest.java` 를 만든다. **두 목 경계를 모두 검증한다** — token-state 로 나가는 값과 IdTokenIssuer 로 들어가는 값 둘 다 본다.

```java
package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.RotateResult;
import dev.starryeye.token.client.TokenStateClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenGrantServiceSidTest {

	/**
	 * refresh 로 재발급하는 id token 에도 sid 를 싣는다. RP 가 나중에 back-channel logout 을 받았을 때
	 *      자기 세션과 대조할 수 있어야 하기 때문이다.
	 *
	 * 주의. 회전 응답의 sid 를 IdTokenIssuer 로 실제로 넘기는지를 ArgumentCaptor 로 본다. 양쪽을 다
	 *      목으로 두고 "호출됐다" 만 보면 값이 null 로 흘러도 통과한다.
	 */

	@Test
	@DisplayName("회전 응답의 sid 가 id token 발급에 그대로 전달된다")
	void refreshCarriesSidIntoIdToken() {
		TokenStateClient tokenStateClient = mock(TokenStateClient.class);
		AccessTokenIssuer accessTokenIssuer = mock(AccessTokenIssuer.class);
		IdTokenIssuer idTokenIssuer = mock(IdTokenIssuer.class);

		when(tokenStateClient.rotate(anyString(), anyString(), any()))
				.thenReturn(new RotateResult("ROTATED", "user-sub-0001", "openid offline_access",
						1000L, "new-refresh", 9999L, "SID-A"));
		when(accessTokenIssuer.issue(anyString(), anyString(), anyString())).thenReturn("access-jwt");
		when(idTokenIssuer.issue(anyString(), anyString(), anyString(), any(), anyLong(), anyString(), any()))
				.thenReturn("id-jwt");

		RefreshTokenGrantService service = new RefreshTokenGrantService(
				tokenStateClient, accessTokenIssuer, idTokenIssuer, 300L);

		ClientInfo client = new ClientInfo("my-client", List.of("refresh_token"), List.of(), List.of(),
				List.of("openid", "offline_access"), List.of(), null, null);

		service.grant("old-refresh", client, null);

		ArgumentCaptor<String> sidCaptor = ArgumentCaptor.forClass(String.class);
		verify(idTokenIssuer).issue(eq("user-sub-0001"), eq("my-client"), anyString(),
				any(), anyLong(), anyString(), sidCaptor.capture());
		assertThat(sidCaptor.getValue()).isEqualTo("SID-A");
	}
}
```

> **주의.** `RefreshTokenGrantService` 의 생성자 시그니처와 `ClientInfo` 의 필드 구성은 실제 코드에 맞춘다. 위 코드가 컴파일되지 않으면 **실제 시그니처를 확인해 테스트를 맞추고**, 그 사실을 보고서에 적는다 — 이 계획서의 추정이 틀린 것이지 코드를 바꿀 이유가 아니다.

- [ ] **Step 2: 테스트 실패 확인**

```bash
cd oauth-2/authorization-server/practice/microservice/token
JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn \
  java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon --tests '*RefreshTokenGrantServiceSidTest'
```

Expected: `RotateResult` 생성자 인자 개수 불일치로 컴파일 실패.

- [ ] **Step 3: `token` 쪽 `RotateResult` 에 `sid` 추가**

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
		long expiresAt,
		String sid
) {
```

(기존 `isRotated()` 등 메서드는 그대로 둔다.)

- [ ] **Step 4: `TokenStateClient.issue` 에 `sid` 추가**

`Map.of` 는 null 값을 담지 못하므로 `LinkedHashMap` 으로 바꾼다. `rotate` 가 이미 같은 이유로 그렇게 하고 있다.

```java
	public IssuedRefreshToken issue(String clientId, String sub, String scope, long authTime, String sid) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("clientId", clientId);
		body.put("sub", sub);
		body.put("scope", scope);
		body.put("authTime", authTime);
		body.put("sid", sid); // Map.of 는 null 값을 담지 못한다 — sid 는 nullable 이다

		return restClient.post()
				.uri("/internal/refresh-tokens")
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(IssuedRefreshToken.class);
	}
```

- [ ] **Step 5: `TokenEndpointController` 의 호출부 수정**

```java
			refreshToken = tokenStateClient
					.issue(client.clientId(), data.sub(), data.scope(), data.authTime(), data.sid())
					.refreshToken();
```

- [ ] **Step 6: `RefreshTokenGrantService` 가 `sid` 를 싣도록 수정**

주석도 함께 고친다 — 더 이상 "sid 가 없다"가 사실이 아니다.

```java
		String idToken = null;
		if (Arrays.asList(effectiveScope.split(" ")).contains("openid")) {
			try {
				// nonce 는 넣지 않는다. 원래 authorization 요청에 묶인 값이라 재발급 토큰에 실으면 리플레이 방어가 깨진다.
				// auth_time 은 최초 인증 시각을 그대로 유지한다. (OIDC Core 12.2)
				// sid 는 회전 응답이 알려준 값을 그대로 싣는다 — refresh token 레코드가 자기가 속한 세션을
				// 보관하므로, RP 가 나중에 back-channel logout 을 받았을 때 자기 세션과 대조할 수 있다.
				// 주의. 세션이 걸리지 않은 발급 경로에서는 sid 가 null 이고, 그때는 claim 자체가 빠진다.
				idToken = idTokenIssuer.issue(rotation.sub(), client.clientId(), effectiveScope,
						null, rotation.authTime(), accessToken, rotation.sid());
```

- [ ] **Step 7: 테스트 통과 확인**

```bash
cd oauth-2/authorization-server/practice/microservice/token
JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn \
  java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: 테스트가 실제로 무는지 확인**

`RefreshTokenGrantService` 의 `rotation.sid()` 를 `null` 로 되돌리고 테스트를 다시 돌린다.

Expected: `refreshCarriesSidIntoIdToken` 이 실패한다(`expected "SID-A" but was null`).

`git diff` 증거를 보고서에 붙이고 원복한다.

- [ ] **Step 9: README 의 거짓이 된 한계 문장 수정**

`README.md` 에서 아래 항목을 찾아 고친다.

기존:
> - **refresh 로 재발급한 id token 에는 `sid` 가 없다** — refresh token 레코드(token-state)가 애초에 `sid` 를 보관하지 않으므로, `grant_type=refresh_token` 경로에서 재발급되는 id token 은 `sid` claim 자체가 빠진다. RP 세션 등록도 code 교환 경로에서만 일어나므로, refresh 만으로 받은 id token 으로는 로그아웃 통지 대상 세션을 새로 특정할 방법이 없다.

새로:
> - **refresh 만으로는 RP 세션이 새로 등록되지 않는다** — 슬라이스 7부터 refresh token 레코드가 `sid` 를 보관하므로 `grant_type=refresh_token` 으로 재발급되는 id token 에도 `sid` claim 이 실린다. 다만 `oidc_sessions` 등록은 여전히 code 교환 경로에서만 일어난다 — 그 세션이 이미 등록돼 있다는 전제 위에서만 성립한다.

- [ ] **Step 10: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/token/src \
        oauth-2/authorization-server/practice/microservice/README.md
git commit -m "$(cat <<'EOF'
token: pass sid to token-state and into refreshed id tokens

code 교환에서 이미 갖고 있던 sid 를 refresh token 발급에 실어 보내고,
회전 응답이 돌려준 sid 를 재발급 id token 에 넣는다.

이 변경으로 README 의 한계 하나가 원인을 잃는다 — "refresh 로 재발급한
id token 에는 sid 가 없다"의 이유가 "레코드가 sid 를 보관하지 않아서"
였는데 이제 보관한다. 문장을 실제 남는 한계(세션 등록은 여전히 code
교환 경로에서만 일어난다)로 다시 썼다.

주의. TokenStateClient.issue 의 본문을 Map.of 에서 LinkedHashMap 으로
바꿨다. sid 가 nullable 이고 Map.of 는 null 값을 담지 못한다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 4: `token-state` 의 세션 단위 폐기

**Files:**
- Modify: `token-state/src/main/java/dev/starryeye/token_state/jpa/RefreshTokenEntityRepository.java`
- Modify: `token-state/src/main/java/dev/starryeye/token_state/RefreshTokenService.java`
- Test: `token-state/src/test/java/dev/starryeye/token_state/RefreshTokenServiceRevokeBySidTest.java` (신규)

**Interfaces:**
- Consumes: Task 2 의 `RefreshTokenEntity.sid`
- Produces: `RefreshTokenService.revokeBySid(String sid)` → `int` (폐기된 행 수)

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package dev.starryeye.token_state;

import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RefreshTokenServiceRevokeBySidTest {

	/**
	 * 로그아웃은 그 세션에 속한 refresh token 만 죽인다. 같은 사용자가 다른 브라우저에서 만든 세션은 살아야 한다.
	 *
	 * 주의. 조건부 갱신(status = ACTIVE)이라 두 번 실행해도 결과가 같다. Kafka 가 at-least-once 이므로
	 *      같은 이벤트를 두 번 받는 일이 실제로 일어나는데, 이 성질 덕에 별도 dedupe 표가 필요 없다.
	 */

	@Autowired
	private RefreshTokenService service;

	@Autowired
	private RefreshTokenEntityRepository repository;

	@Test
	@DisplayName("그 세션의 refresh 만 폐기하고 다른 세션은 건드리지 않는다")
	void revokesOnlyThatSession() {
		IssueResult sessionA = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
		IssueResult sessionB = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-B");

		int revoked = service.revokeBySid("SID-A");

		assertThat(revoked).isEqualTo(1);
		assertThat(statusOf(sessionA)).isEqualTo(RefreshTokenStatus.REVOKED);
		assertThat(statusOf(sessionB)).isEqualTo(RefreshTokenStatus.ACTIVE);
	}

	@Test
	@DisplayName("한 세션에 여러 client 가 붙어 있으면 전부 폐기한다")
	void revokesEveryClientOfThatSession() {
		IssueResult first = service.issue("client-one", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
		IssueResult second = service.issue("client-two", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

		int revoked = service.revokeBySid("SID-A");

		assertThat(revoked).isEqualTo(2);
		assertThat(statusOf(first)).isEqualTo(RefreshTokenStatus.REVOKED);
		assertThat(statusOf(second)).isEqualTo(RefreshTokenStatus.REVOKED);
	}

	@Test
	@DisplayName("두 번 폐기해도 결과가 같다 — 두 번째는 아무 행도 바꾸지 않는다")
	void isIdempotent() {
		service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

		assertThat(service.revokeBySid("SID-A")).isEqualTo(1);
		assertThat(service.revokeBySid("SID-A")).isEqualTo(0);
	}

	@Test
	@DisplayName("모르는 sid 는 아무것도 바꾸지 않는다")
	void unknownSidChangesNothing() {
		service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

		assertThat(service.revokeBySid("SID-UNKNOWN")).isEqualTo(0);
	}

	private RefreshTokenStatus statusOf(IssueResult issued) {
		return repository.findByFamilyId(issued.familyId()).get(0).getStatus();
	}
}
```

- [ ] **Step 2: 테스트 실패 확인**

Expected: `revokeBySid` 가 없어 컴파일 실패.

- [ ] **Step 3: 레포지토리에 벌크 갱신 추가**

`RefreshTokenEntityRepository.java` 에 추가한다.

```java
	/**
	 * 한 OP 세션에 속한 ACTIVE refresh token 을 한 번에 폐기한다.
	 *
	 * 주의. where 절의 status = ACTIVE 가 멱등을 만든다. 두 번째 실행은 바꿀 행이 없어 0을 돌려준다.
	 *      Kafka 가 at-least-once 라 같은 로그아웃 이벤트를 두 번 받는 일이 실제로 일어나는데,
	 *      이 조건 덕에 소비자 쪽에 별도 중복 처리 표를 두지 않아도 된다.
	 *
	 * 주의. 이 조건은 감사 기록도 지킨다. 벌크 갱신은 엔티티의 revoke(Instant, String) 을 거치지 않아
	 *      "이미 REVOKED 면 사유를 덮어쓰지 않는다"는 보호가 적용되지 않는데, ACTIVE 만 대상으로 삼으므로
	 *      REUSE_DETECTED 로 폐기된 행의 사유가 지워지지 않는다.
	 *
	 * 주의. sid 가 null 인 행은 이 조건에 걸리지 않는다(SQL 에서 null = null 은 참이 아니다).
	 *      세션이 걸리지 않은 발급 경로의 행은 세션 단위 폐기 대상이 아니므로 의도한 동작이다.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update RefreshTokenEntity r
			   set r.status = :revoked, r.revokedAt = :now, r.revokedReason = :reason
			 where r.sid = :sid and r.status = :active
			""")
	int revokeActiveBySid(@Param("sid") String sid,
			@Param("revoked") RefreshTokenStatus revoked,
			@Param("active") RefreshTokenStatus active,
			@Param("reason") String reason,
			@Param("now") Instant now);
```

import 를 추가한다: `org.springframework.data.jpa.repository.Modifying`, `dev.starryeye.token_state.jpa.RefreshTokenStatus`(같은 패키지라 불필요), `java.time.Instant`.

- [ ] **Step 4: 서비스 메서드 추가**

`RefreshTokenService.java` 에 추가한다.

```java
	/**
	 * OP 세션이 끝났을 때 그 세션의 refresh token 을 폐기한다. 로그아웃 이벤트 소비자가 호출한다.
	 *
	 * 주의. 폐기 범위가 sid 단위다. sub 로 죽이면 같은 사용자가 다른 브라우저에서 만든 세션까지 함께
	 *      죽고, client_id 까지 묶으면 한 세션에 붙은 다른 RP 가 남는다. oidc_sessions 를 sid 로 지우는
	 *      범위와 정확히 같아야 두 저장소가 같은 단위로 움직인다.
	 *
	 * 주의. 계열(family) 단위로 잠그지 않는다. 회전이 쓰는 PESSIMISTIC_WRITE 경로와 달리 이 연산은
	 *      조건부 벌크 갱신 한 번이라 읽고 판정하는 창이 없다. 회전과 동시에 실행되면 둘 중 하나가
	 *      먼저 커밋되는데, 어느 쪽이 이기든 결과는 "그 세션의 토큰이 더는 쓰이지 않는다"로 수렴한다 —
	 *      회전이 먼저면 새 행이 ACTIVE 로 생겼다가 다음 이벤트 재처리 때 잡히고(at-least-once),
	 *      폐기가 먼저면 회전이 REVOKED 를 만나 실패한다.
	 */
	@Transactional
	public int revokeBySid(String sid) {
		return repository.revokeActiveBySid(sid, RefreshTokenStatus.REVOKED, RefreshTokenStatus.ACTIVE,
				"SESSION_LOGGED_OUT", Instant.now());
	}
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
cd oauth-2/authorization-server/practice/microservice/token-state
JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn \
  java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: 테스트가 실제로 무는지 확인**

`where` 절에서 `and r.status = :active` 를 지운다(파라미터 바인딩도 함께 조정).

Expected: `isIdempotent` 가 실패한다 — 두 번째 호출이 0이 아니라 1을 돌려준다.

`git diff` 증거를 보고서에 붙이고 원복한다.

- [ ] **Step 7: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/token-state/src
git commit -m "$(cat <<'EOF'
token-state: revoke a session's refresh tokens in one conditional update

revokeBySid 는 그 세션에 속한 ACTIVE refresh token 만 폐기한다. 같은
사용자의 다른 세션은 건드리지 않고, 한 세션에 붙은 여러 client 는 전부
죽인다 — oidc_sessions 를 sid 로 지우는 범위와 같다.

where 절의 status = ACTIVE 가 멱등을 만든다. 두 번째 실행은 바꿀 행이
없어 0을 돌려준다. Kafka 가 at-least-once 라 같은 이벤트를 두 번 받는
일이 실제로 일어나는데, 이 조건 덕에 소비자에 중복 처리 표가 필요 없다.
같은 조건이 REUSE_DETECTED 폐기 사유도 지켜준다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 5: Kafka 도입과 직접 발행 — 결손을 닫는다

**Files:**
- Modify: `docker-compose/docker-compose.yml`
- Modify: `session/build.gradle`, `token-state/build.gradle`
- Create: `session/src/main/java/dev/starryeye/session/event/SessionLoggedOutEvent.java`
- Create: `session/src/main/java/dev/starryeye/session/event/LogoutEventPublisher.java`
- Create: `session/src/main/java/dev/starryeye/session/event/KafkaTopicConfig.java`
- Modify: `session/src/main/java/dev/starryeye/session/SessionService.java`
- Modify: `session/src/main/resources/application.yml`, `session/src/test/resources/application.yml`
- Create: `token-state/src/main/java/dev/starryeye/token_state/event/SessionLoggedOutEvent.java`
- Create: `token-state/src/main/java/dev/starryeye/token_state/event/SessionLoggedOutConsumer.java`
- Modify: `token-state/src/main/resources/application.yml`, `token-state/src/test/resources/application.yml`

> **주의.** 이 태스크에서 토픽 이름 상수가 두 모듈에 각각 생긴다 — `session` 의 `KafkaTopicConfig.LOGGED_OUT_TOPIC` 과 `token-state` 의 `SessionLoggedOutConsumer.LOGGED_OUT_TOPIC`. 값이 같은 문자열을 두 곳에 두는 것이 맞다. 모듈 간 공유 라이브러리를 만들지 않는 것이 이 저장소의 방식이고(cross-service record 를 슬라이스 1부터 각자 두어 왔다), 토픽 이름은 두 서비스가 합의한 **계약**이지 한쪽의 내부 상수가 아니다. 다만 **두 값이 어긋나면 조용히 아무 일도 일어나지 않으므로**(발행은 성공하고 소비자는 영원히 기다린다) Task 9 의 e2e 가 그 계약을 실제로 확인하는 유일한 지점이다.
- Test: `session/src/test/java/dev/starryeye/session/event/LogoutEventPublishTest.java` (신규)
- Test: `token-state/src/test/java/dev/starryeye/token_state/event/SessionLoggedOutConsumerTest.java` (신규)

**Interfaces:**
- Consumes: Task 4 의 `RefreshTokenService.revokeBySid(String sid)`
- Produces:
  - 토픽 상수 `oidc.session.logged-out.v1`
  - 페이로드 `SessionLoggedOutEvent(String eventId, String sid, String sub, Instant occurredAt)` — **양쪽 모듈에 같은 필드로 각각 둔다**(모듈 간 공유 라이브러리를 만들지 않는 것이 이 저장소의 방식이다)
  - `LogoutEventPublisher.publish(String sid, String sub)`

- [ ] **Step 1: Kafka 를 docker-compose 에 추가**

`docker-compose/docker-compose.yml` 에 서비스를 추가한다.

```yaml
  kafka:
    image: apache/kafka:3.9.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
```

> **주의.** 5개 Spring 서비스는 호스트에서 `java -jar` 로 뜨므로 `KAFKA_ADVERTISED_LISTENERS` 는 `localhost:9092` 다. 컨테이너 안에서 Kafka 를 부르는 서비스는 없다.

기동 확인:

```bash
docker compose -p microservice-as -f docker-compose/docker-compose.yml up -d kafka
sleep 10
docker exec -i microservice-as-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Expected: 오류 없이 실행된다(목록은 비어 있어도 된다).

- [ ] **Step 2: 두 모듈에 spring-kafka 의존성 추가**

`session/build.gradle` 과 `token-state/build.gradle` 의 `dependencies` 에 추가한다.

```groovy
	implementation 'org.springframework.kafka:spring-kafka'
	testImplementation 'org.springframework.kafka:spring-kafka-test'
```

- [ ] **Step 3: 이벤트 record 를 양쪽에 만든다**

`session/src/main/java/dev/starryeye/session/event/SessionLoggedOutEvent.java`:

```java
package dev.starryeye.session.event;

import java.time.Instant;

/**
 * OP 세션이 로그아웃됐다는 사실. 소비자가 무엇을 필요로 하는지가 아니라 일어난 일을 서술한다.
 *
 * 주의. clientId 목록을 넣지 않는다. 그것은 "그 세션에 무엇이 붙어 있었나"라는 session 의 내부 상태지
 *      로그아웃이라는 사실이 아니다. 소비자 요구에 맞춰 페이로드를 깎으면 두 번째 소비자가 붙는 순간 깨진다.
 *
 * 주의. sub 는 nullable 이다. 이 이벤트는 로그아웃될 때마다 발행되는데, 등록된 RP 가 하나도 없는
 *      세션(openid 없이 offline_access 만 받은 경로)은 oidc_sessions 행이 없어 소유자를 알 수 없다.
 *      소비자가 폐기에 쓰는 값은 sid 하나이므로 그 경우에도 폐기는 정상 동작한다.
 *
 * 주의. eventId 는 지금 소비자가 쓰지 않는다. 폐기가 조건부 갱신이라 멱등이 공짜이기 때문이다.
 *      나중에 감사 로그처럼 append-only 인 소비자가 붙으면 그쪽은 멱등이 공짜가 아니고, 그때 필요하다.
 */
public record SessionLoggedOutEvent(String eventId, String sid, String sub, Instant occurredAt) {
}
```

`token-state/src/main/java/dev/starryeye/token_state/event/SessionLoggedOutEvent.java`:

```java
package dev.starryeye.token_state.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * session 이 발행하는 로그아웃 사실의 소비자 쪽 표현이다. 발행자와 같은 필드를 각각 둔다 —
 *      모듈 간 공유 라이브러리를 만들지 않는 것이 이 저장소의 방식이다(cross-service record 는 슬라이스 1부터 그렇다).
 *
 * 주의. 모르는 필드는 무시한다(@JsonIgnoreProperties). 발행자가 필드를 더해도 소비자가 터지지 않게 하려는 것인데,
 *      대가가 있다 — 슬라이스 3에서 검증 필드를 추가했을 때 구버전이 그것을 조용히 무시해 검증이 통째로 뚫렸다.
 *      그래서 보안 판단은 새 필드로 추가하지 않고 토픽 버전을 올린다. 토픽 이름의 .v1 이 그 준비다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionLoggedOutEvent(String eventId, String sid, String sub, Instant occurredAt) {
}
```

- [ ] **Step 4: 토픽 설정과 발행자 작성**

`session/src/main/java/dev/starryeye/session/event/KafkaTopicConfig.java`:

```java
package dev.starryeye.session.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

	/**
	 * 자동 생성에 맡기지 않고 파티션 수를 명시한다. 자동 생성은 기본 1 파티션이라 순서 보장의 단위가
	 *      토픽 전체가 되어버린다 — 세션끼리 줄을 설 이유가 없는데 전부 직렬화된다.
	 */
	public static final String LOGGED_OUT_TOPIC = "oidc.session.logged-out.v1";

	@Bean
	NewTopic sessionLoggedOutTopic() {
		return TopicBuilder.name(LOGGED_OUT_TOPIC).partitions(3).replicas(1).build();
	}
}
```

`session/src/main/java/dev/starryeye/session/event/LogoutEventPublisher.java`:

```java
package dev.starryeye.session.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class LogoutEventPublisher {

	/**
	 * 로그아웃 사실을 Kafka 로 발행한다.
	 *
	 * 주의. 파티션 키는 sid 다. 같은 세션의 이벤트가 같은 파티션에 들어가 순서가 보장된다. sub 로 잡으면
	 *      한 사용자의 모든 세션이 한 파티션에 몰리는데, 세션 간에는 순서 제약이 없으므로 병렬성만 잃는다.
	 *
	 * 주의. send 의 결과를 기다린다(블로킹). 기다리지 않으면 발행 실패가 조용히 사라져 "로그아웃했는데
	 *      refresh 는 살아 있다"가 아무 흔적 없이 일어난다. 다만 이 선택은 SessionService 의 트랜잭션
	 *      안에서 호출되므로 Kafka 장애가 로그아웃 트랜잭션 전체를 롤백시킨다 — 슬라이스 7 Task 7 이
	 *      outbox 로 닫는 문제가 바로 이것이다.
	 */

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;

	public void publish(String sid, String sub) {
		SessionLoggedOutEvent event = new SessionLoggedOutEvent(
				UUID.randomUUID().toString(), sid, sub, Instant.now());
		try {
			String payload = objectMapper.writeValueAsString(event);
			kafkaTemplate.send(KafkaTopicConfig.LOGGED_OUT_TOPIC, sid, payload)
					.get(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("logout event publish interrupted for sid=" + sid, e);
		} catch (Exception e) {
			throw new IllegalStateException("logout event publish failed for sid=" + sid, e);
		}
	}
}
```

- [ ] **Step 5: `SessionService` 가 발행하도록 수정**

`consumeForLogout` 을 고친다. **행이 없어도 발행한다** — 이유를 주석으로 남긴다.

```java
	@Transactional
	public LogoutTargets consumeForLogout(String sid) {
		List<OidcSessionEntity> sessions = repository.findBySid(sid);
		List<LogoutTargets.Target> targets = sessions.stream()
				.map(session -> new LogoutTargets.Target(session.getClientId(), session.getSub()))
				.toList();
		repository.deleteBySid(sid);

		// 등록된 RP 가 하나도 없어도 발행한다. openid 없이 offline_access 만 받은 경로는 oidc_sessions 행이
		// 없지만 그 sid 로 발급된 refresh token 은 존재할 수 있다 — 행이 있을 때만 발행하면 그 토큰이 살아남는다.
		//
		// 주의. sub 는 첫 행에서 가져온다. 한 sid 의 모든 행은 같은 사용자의 것이므로 어느 행을 골라도 같다.
		//      슬라이스 5에서 문제가 됐던 "첫 행의 sub 를 모든 RP 에 재사용"과는 다른 상황이다 — 그때는 RP 마다
		//      자기 sub 가 필요했고, 여기서는 세션 소유자 한 명을 적는 것이다.
		String sub = sessions.isEmpty() ? null : sessions.get(0).getSub();
		logoutEventPublisher.publish(sid, sub);

		return new LogoutTargets(targets);
	}
```

생성자 주입 필드를 추가한다: `private final LogoutEventPublisher logoutEventPublisher;`

- [ ] **Step 6: 설정 추가**

`session/src/main/resources/application.yml` 의 `spring` 아래에 추가한다.

```yaml
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
```

`token-state/src/main/resources/application.yml` 의 `spring` 아래에 추가한다.

```yaml
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: token-state
      auto-offset-reset: earliest
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
    listener:
      ack-mode: record
```

> `token-state` 도 producer 설정이 필요하다. Task 8 의 DLT 발행이 `KafkaTemplate` 을 쓰고, 테스트에서도 이벤트를 직접 밀어 넣는다.

**그리고 테스트 설정에도 같은 블록을 넣는다.** `src/test/resources/application.yml` 은 main 쪽 파일을 **병합하는 것이 아니라 대체한다**(같은 이름이 테스트 클래스패스에서 먼저 잡힌다). main 에만 넣으면 EmbeddedKafka 테스트에서 serializer 가 없어 `ConfigException` 이 난다.

`session/src/test/resources/application.yml` 의 `spring` 아래에 추가한다.

```yaml
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
```

`token-state/src/test/resources/application.yml` 의 `spring` 아래에 추가한다.

```yaml
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: token-state
      auto-offset-reset: earliest
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
    listener:
      ack-mode: record
```

> `bootstrap-servers` 는 각 테스트가 `@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")` 로 덮어쓰므로 여기 값은 쓰이지 않는다. 키를 남겨 두는 것은 설정 모양을 main 과 같게 유지하기 위해서다.

- [ ] **Step 7: 소비자 작성**

`token-state/src/main/java/dev/starryeye/token_state/event/SessionLoggedOutConsumer.java`:

```java
package dev.starryeye.token_state.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.token_state.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionLoggedOutConsumer {

	/**
	 * 로그아웃 사실을 받아 그 세션의 refresh token 을 폐기한다.
	 *
	 * 주의. 페이로드에서 쓰는 값은 sid 하나다. sub 는 사실을 서술하려고 실려 있을 뿐 폐기 판정에 쓰지 않는다.
	 *      그래서 sub 가 null 인 이벤트(등록된 RP 가 없던 세션)도 정상 처리된다.
	 *
	 * 주의. 예외를 잡지 않는다. 삼키면 처리하지 못한 로그아웃이 커밋돼 영원히 사라진다. 전파하면
	 *      컨테이너가 재시도하고, 제한을 넘으면 DLT 로 간다(KafkaConsumerConfig 참고).
	 */

	public static final String LOGGED_OUT_TOPIC = "oidc.session.logged-out.v1";

	private final RefreshTokenService refreshTokenService;
	private final ObjectMapper objectMapper;

	@KafkaListener(topics = LOGGED_OUT_TOPIC, groupId = "token-state")
	public void onSessionLoggedOut(String payload) throws Exception {
		SessionLoggedOutEvent event = objectMapper.readValue(payload, SessionLoggedOutEvent.class);
		int revoked = refreshTokenService.revokeBySid(event.sid());
		log.debug("session logged out: sid={} revokedRefreshTokens={} eventId={}",
				event.sid(), revoked, event.eventId());
	}
}
```

> `Instant` 역직렬화를 위해 `ObjectMapper` 에 `JavaTimeModule` 이 등록돼 있어야 한다. Spring Boot 의 기본 `ObjectMapper` 는 `jackson-datatype-jsr310` 이 클래스패스에 있으면 자동 등록한다 — `spring-boot-starter-web` 이 끌어오므로 두 모듈 모두 이미 있다. 테스트에서 실패하면 그 사실을 보고서에 적고 `@JsonFormat` 이 아니라 모듈 등록으로 해결한다.

- [ ] **Step 8: 발행 테스트 작성 (session)**

`session/src/test/java/dev/starryeye/session/event/LogoutEventPublishTest.java`:

```java
package dev.starryeye.session.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.session.SessionService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 3, topics = KafkaTopicConfig.LOGGED_OUT_TOPIC)
class LogoutEventPublishTest {

	/**
	 * 로그아웃하면 그 사실이 토픽에 실린다. 파티션 키는 sid 여야 한다 — 같은 세션의 이벤트가 같은
	 *      파티션에 들어가야 순서가 보장되기 때문이다.
	 */

	@Autowired
	private SessionService sessionService;

	@Autowired
	private EmbeddedKafkaBroker broker;

	@Autowired
	private ObjectMapper objectMapper;

	private Consumer<String, String> consumer;

	@BeforeEach
	void subscribe() {
		Map<String, Object> props = KafkaTestUtils.consumerProps("test-group", "true", broker);
		consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(props,
				new org.apache.kafka.common.serialization.StringDeserializer(),
				new org.apache.kafka.common.serialization.StringDeserializer());
		broker.consumeFromAnEmbeddedTopic(consumer, KafkaTopicConfig.LOGGED_OUT_TOPIC);
	}

	@AfterEach
	void close() {
		consumer.close();
	}

	@Test
	@DisplayName("로그아웃하면 sid 를 키로 이벤트가 발행된다")
	void publishesWithSidAsKey() throws Exception {
		sessionService.register("SID-A", "user-sub-0001", "my-client");

		sessionService.consumeForLogout("SID-A");

		ConsumerRecord<String, String> record =
				KafkaTestUtils.getSingleRecord(consumer, KafkaTopicConfig.LOGGED_OUT_TOPIC);
		assertThat(record.key()).isEqualTo("SID-A");

		SessionLoggedOutEvent event = objectMapper.readValue(record.value(), SessionLoggedOutEvent.class);
		assertThat(event.sid()).isEqualTo("SID-A");
		assertThat(event.sub()).isEqualTo("user-sub-0001");
		assertThat(event.eventId()).isNotBlank();
		assertThat(event.occurredAt()).isNotNull();
	}

	@Test
	@DisplayName("등록된 RP 가 없는 세션도 발행한다 — 그 sid 의 refresh token 이 있을 수 있다")
	void publishesEvenWithoutRegisteredRps() throws Exception {
		sessionService.consumeForLogout("SID-NO-RP");

		ConsumerRecord<String, String> record =
				KafkaTestUtils.getSingleRecord(consumer, KafkaTopicConfig.LOGGED_OUT_TOPIC);
		SessionLoggedOutEvent event = objectMapper.readValue(record.value(), SessionLoggedOutEvent.class);
		assertThat(event.sid()).isEqualTo("SID-NO-RP");
		assertThat(event.sub()).isNull();
	}
}
```

- [ ] **Step 9: 소비 테스트 작성 (token-state)**

`token-state/src/test/java/dev/starryeye/token_state/event/SessionLoggedOutConsumerTest.java`:

```java
package dev.starryeye.token_state.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.token_state.IssueResult;
import dev.starryeye.token_state.RefreshTokenService;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 3, topics = SessionLoggedOutConsumer.LOGGED_OUT_TOPIC)
class SessionLoggedOutConsumerTest {

	/**
	 * 로그아웃 이벤트를 받으면 그 세션의 refresh token 이 폐기된다 — 이 슬라이스가 닫으려는 결손이다.
	 */

	@Autowired
	private RefreshTokenService service;

	@Autowired
	private RefreshTokenEntityRepository repository;

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("로그아웃 이벤트를 받으면 그 세션의 refresh 가 폐기된다")
	void revokesOnEvent() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
		IssueResult other = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-B");

		String payload = objectMapper.writeValueAsString(new SessionLoggedOutEvent(
				UUID.randomUUID().toString(), "SID-A", "user-sub-0001", Instant.now()));
		kafkaTemplate.send(SessionLoggedOutConsumer.LOGGED_OUT_TOPIC, "SID-A", payload).get(5, TimeUnit.SECONDS);

		await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
				assertThat(repository.findByFamilyId(issued.familyId()).get(0).getStatus())
						.isEqualTo(RefreshTokenStatus.REVOKED));

		assertThat(repository.findByFamilyId(other.familyId()).get(0).getStatus())
				.isEqualTo(RefreshTokenStatus.ACTIVE);
	}
}
```

`token-state/build.gradle` 에 `testImplementation 'org.awaitility:awaitility'` 를 추가한다(Spring Boot BOM 이 버전을 관리한다).

- [ ] **Step 10: 테스트 실행**

```bash
export JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn
for m in session token-state; do
  (cd oauth-2/authorization-server/practice/microservice/$m && \
   $JAVA_HOME/bin/java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon) || echo "FAIL $m"
done
```

Expected: 두 모듈 BUILD SUCCESSFUL.

- [ ] **Step 11: 테스트가 실제로 무는지 확인**

`SessionService.consumeForLogout` 에서 `logoutEventPublisher.publish(sid, sub);` 한 줄을 주석 처리한다.

Expected: `LogoutEventPublishTest` 두 테스트가 실패한다.

원복 후 `SessionLoggedOutConsumer` 의 `revokeBySid` 호출을 주석 처리한다.

Expected: `SessionLoggedOutConsumerTest.revokesOnEvent` 가 실패한다.

`git diff` 증거를 각각 보고서에 붙이고 원복한다.

- [ ] **Step 12: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/docker-compose/docker-compose.yml \
        oauth-2/authorization-server/practice/microservice/session/build.gradle \
        oauth-2/authorization-server/practice/microservice/session/src \
        oauth-2/authorization-server/practice/microservice/token-state/build.gradle \
        oauth-2/authorization-server/practice/microservice/token-state/src
git commit -m "$(cat <<'EOF'
microservice: revoke refresh tokens on logout via a kafka event

로그아웃해도 refresh token 이 살아 있던 결손을 닫는다. session 이
로그아웃 사실을 발행하고 token-state 가 소비해 그 세션의 refresh 를
폐기한다.

- 토픽 oidc.session.logged-out.v1, 파티션 3, 키는 sid
- 페이로드는 일어난 사실만 적는다. clientId 목록은 넣지 않는다
- 등록된 RP 가 없는 세션도 발행한다. openid 없이 offline_access 만 받은
  경로는 oidc_sessions 행이 없지만 그 sid 의 refresh 는 있을 수 있다
- session 은 스키마가 갈려 token-state 의 테이블을 볼 수 없다. 이벤트가
  필연이 된 것이 이 순서의 요점이다

주의. 이번 단계는 트랜잭션 안에서 직접 발행한다. Kafka 장애가 로그아웃
트랜잭션을 통째로 롤백시키고, 커밋이 실패하면 이미 나간 이벤트가 유령이
된다. 다음 태스크에서 그 두 실패를 테스트로 고정한다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 6: 직접 발행의 두 실패를 재현해 고정한다

**Files:**
- Test: `session/src/test/java/dev/starryeye/session/event/DirectPublishFailureModeTest.java` (신규)

**Interfaces:**
- Consumes: Task 5 의 `SessionService.consumeForLogout`, `LogoutEventPublisher`
- Produces: 없음 (테스트만)

이 태스크는 **버그를 고치지 않는다.** 지금 동작이 어떤지를 테스트로 못 박아, Task 7 에서 outbox 로 바꿀 때 무엇이 달라지는지 diff 로 보이게 하는 것이 목적이다.

- [ ] **Step 1: 두 실패를 고정하는 테스트 작성**

```java
package dev.starryeye.session.event;

import dev.starryeye.session.SessionService;
import dev.starryeye.session.jpa.OidcSessionEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
class DirectPublishFailureModeTest {

	/**
	 * 직접 발행이 남기는 두 실패를 현재 동작 그대로 고정한다. 고치지 않는다 —
	 *      Task 7 에서 outbox 로 바꿀 때 이 테스트들이 반대 결과를 요구하도록 뒤집는 것이 목적이다.
	 *
	 * 주의. DB 트랜잭션과 Kafka 전송은 서로 다른 시스템이라 하나의 원자 단위가 될 수 없다.
	 *      순서를 어떻게 잡든 사이에 틈이 남는다.
	 */

	@Autowired
	private SessionService sessionService;

	@Autowired
	private OidcSessionEntityRepository repository;

	@MockitoSpyBean
	private LogoutEventPublisher publisher;

	@Test
	@DisplayName("[현재 동작] Kafka 발행이 실패하면 로그아웃이 통째로 롤백된다 — 세션 행이 그대로 남는다")
	void publishFailureRollsBackTheWholeLogout() {
		sessionService.register("SID-A", "user-sub-0001", "my-client");
		doThrow(new IllegalStateException("kafka down")).when(publisher).publish(anyString(), any());

		assertThatThrownBy(() -> sessionService.consumeForLogout("SID-A"))
				.isInstanceOf(IllegalStateException.class);

		// Kafka 장애가 로그아웃 장애가 됐다. auth 는 이 실패를 fail-open 으로 삼키므로 사용자에게는
		// 로그아웃된 것처럼 보이는데, 서버에는 아무 일도 일어나지 않았다 — 세션 행도 남고 refresh 도 산다.
		assertThat(repository.findBySid("SID-A")).hasSize(1);
	}

	@Test
	@DisplayName("[현재 동작] 발행은 트랜잭션 커밋 전에 일어난다 — 커밋이 실패하면 유령 이벤트가 남는다")
	void publishHappensBeforeCommit() {
		sessionService.register("SID-B", "user-sub-0001", "my-client");

		sessionService.consumeForLogout("SID-B");

		// publish 가 트랜잭션 안에서 호출된다는 사실 자체를 고정한다. 이 위치라면 커밋이 실패했을 때
		// 이미 나간 이벤트를 되돌릴 방법이 없다 — 세션은 살아 있는데 그 사용자의 refresh 만 죽는다.
		verify(publisher).publish("SID-B", "user-sub-0001");
	}

}
```

> **주의.** `@MockitoSpyBean` 은 Spring Boot 3.4 의 `org.springframework.test.context.bean.override.mockito` 패키지에 있다. 컴파일이 안 되면 `@SpyBean`(deprecated) 으로 대체하고 그 사실을 보고서에 적는다.

- [ ] **Step 2: 테스트 실행 — 전부 통과해야 한다**

```bash
cd oauth-2/authorization-server/practice/microservice/session
JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn \
  java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon --tests '*DirectPublishFailureModeTest'
```

Expected: **PASS.** 지금 동작이 그렇다는 것을 고정한 테스트이므로 통과해야 정상이다. 실패하면 Task 5 의 구현이 계획과 다른 것이므로 보고한다.

- [ ] **Step 3: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/session/src/test/java/dev/starryeye/session/event/DirectPublishFailureModeTest.java
git commit -m "$(cat <<'EOF'
session: pin the two failure modes of direct publishing

고치지 않고 현재 동작을 테스트로 못 박는다. 다음 태스크에서 outbox 로
바꿀 때 무엇이 달라지는지 이 테스트들의 diff 로 드러난다.

- Kafka 발행이 실패하면 로그아웃 트랜잭션이 통째로 롤백된다. auth 가
  fail-open 이라 사용자에겐 로그아웃처럼 보이는데 서버엔 아무 일도
  일어나지 않는다
- 발행이 커밋 전에 일어난다. 커밋이 실패하면 이미 나간 이벤트를 되돌릴
  수 없어 세션은 살아 있는데 refresh 만 죽는다

DB 트랜잭션과 Kafka 전송은 다른 시스템이라 하나의 원자 단위가 될 수
없다. 순서를 어떻게 잡든 틈이 남는다는 것이 요점이다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 7: outbox 로 전환한다

**Files:**
- Create: `session/src/main/java/dev/starryeye/session/jpa/OutboxEntity.java`
- Create: `session/src/main/java/dev/starryeye/session/jpa/OutboxEntityRepository.java`
- Create: `session/src/main/java/dev/starryeye/session/outbox/OutboxPublisher.java`
- Modify: `session/src/main/java/dev/starryeye/session/event/LogoutEventPublisher.java`
- Modify: `session/src/main/java/dev/starryeye/session/SessionService.java`
- Modify: `session/src/main/java/dev/starryeye/session/SessionApplication.java` (`@EnableScheduling`)
- Modify: `session/src/main/resources/application.yml`
- Modify: `session/src/test/java/dev/starryeye/session/event/DirectPublishFailureModeTest.java` → 이름과 내용을 바꾼다
- Test: `session/src/test/java/dev/starryeye/session/outbox/OutboxPublisherTest.java` (신규)

**Interfaces:**
- Consumes: Task 5 의 `SessionLoggedOutEvent`, `KafkaTopicConfig.LOGGED_OUT_TOPIC`
- Produces:
  - `LogoutEventPublisher.record(String sid, String sub)` — 트랜잭션 안에서 outbox 행만 만든다(Kafka 를 부르지 않는다)
  - `OutboxPublisher.publishPending()` — `@Scheduled` 가 부르지만 테스트에서 직접 호출할 수 있게 public
  - `OutboxEntityRepository.findTop100ByPublishedAtIsNullOrderByIdAsc()`

- [ ] **Step 1: outbox 엔티티와 레포지토리 작성**

`session/src/main/java/dev/starryeye/session/jpa/OutboxEntity.java`:

```java
package dev.starryeye.session.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "outbox", indexes = @Index(name = "idx_outbox_unpublished", columnList = "published_at, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEntity {

	/**
	 * 아직 Kafka 로 나가지 않은 이벤트를 담는다.
	 *
	 * 주의. 이 테이블의 존재 이유는 원자성 하나다. 상태 변경(oidc_sessions 삭제)과 이벤트 기록이 같은
	 *      트랜잭션에 들어가야 "로그아웃은 됐는데 편지가 안 갔다"와 "편지는 갔는데 로그아웃이 안 됐다"가
	 *      둘 다 불가능해진다. DB 와 Kafka 는 서로 다른 시스템이라 직접 묶을 방법이 없다.
	 *
	 * 주의. published_at 이 null 인 행이 미발행이다. 발행 후 표시 직전에 죽으면 다음 주기에 다시 보내므로
	 *      전달은 at-least-once 다. 소비자의 폐기가 조건부 갱신이라 멱등이므로 그대로 둔다.
	 */

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, length = 36)
	private String eventId;

	@Column(nullable = false, length = 100)
	private String topic;

	@Column(name = "partition_key", nullable = false, length = 64)
	private String partitionKey;

	@Lob
	@Column(nullable = false)
	private String payload;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Builder
	private OutboxEntity(String eventId, String topic, String partitionKey, String payload, Instant createdAt) {
		this.eventId = eventId;
		this.topic = topic;
		this.partitionKey = partitionKey;
		this.payload = payload;
		this.createdAt = createdAt;
	}

	public void markPublished(Instant at) {
		this.publishedAt = at;
	}
}
```

`session/src/main/java/dev/starryeye/session/jpa/OutboxEntityRepository.java`:

```java
package dev.starryeye.session.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEntityRepository extends JpaRepository<OutboxEntity, Long> {

	/**
	 * 미발행 행을 오래된 것부터 가져온다. 한 번에 가져오는 수를 제한해 한 주기가 무한정 길어지지 않게 한다.
	 *
	 * 주의. 잠금을 걸지 않는다. session 인스턴스가 하나뿐이라 폴러도 하나이기 때문이다. 인스턴스를 늘리면
	 *      폴러끼리 같은 행을 집어 중복 발행이 늘어나는데, 소비자가 멱등이라 피해는 없고 낭비만 생긴다.
	 *      정석 해법은 FOR UPDATE SKIP LOCKED 이며, 지금 넣으면 도달 불가능한 코드가 되므로 한계로만 남긴다.
	 */
	List<OutboxEntity> findTop100ByPublishedAtIsNullOrderByIdAsc();
}
```

- [ ] **Step 2: `LogoutEventPublisher` 를 outbox 기록으로 바꾼다**

```java
package dev.starryeye.session.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.session.jpa.OutboxEntity;
import dev.starryeye.session.jpa.OutboxEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LogoutEventPublisher {

	/**
	 * 로그아웃 사실을 outbox 에 기록한다. Kafka 를 직접 부르지 않는다.
	 *
	 * 주의. 호출자(SessionService.consumeForLogout)의 트랜잭션에 참여한다. oidc_sessions 삭제와 이 INSERT 가
	 *      함께 커밋되거나 함께 롤백되므로, 상태 변경과 이벤트 기록 사이의 틈이 사라진다. Kafka 로 옮기는 일은
	 *      OutboxPublisher 가 별도 주기로 하고, 실패해도 행이 DB 에 남아 다음 주기에 다시 시도된다.
	 *
	 * 주의. 파티션 키는 sid 다. 같은 세션의 이벤트가 같은 파티션에 들어가야 순서가 보장된다.
	 */

	private final OutboxEntityRepository outboxRepository;
	private final ObjectMapper objectMapper;

	public void record(String sid, String sub) {
		SessionLoggedOutEvent event = new SessionLoggedOutEvent(
				UUID.randomUUID().toString(), sid, sub, Instant.now());
		String payload;
		try {
			payload = objectMapper.writeValueAsString(event);
		} catch (JsonProcessingException e) {
			// 직렬화 실패는 재시도로 낫지 않는다. 트랜잭션을 죽여 로그아웃 자체를 실패시킨다 —
			// 기록하지 못한 이벤트를 성공한 것처럼 커밋하면 그 사실이 영원히 사라진다.
			throw new IllegalStateException("failed to serialize logout event for sid=" + sid, e);
		}

		outboxRepository.save(OutboxEntity.builder()
				.eventId(event.eventId())
				.topic(KafkaTopicConfig.LOGGED_OUT_TOPIC)
				.partitionKey(sid)
				.payload(payload)
				.createdAt(event.occurredAt())
				.build());
	}
}
```

- [ ] **Step 3: `SessionService` 호출부 수정**

```java
		String sub = sessions.isEmpty() ? null : sessions.get(0).getSub();
		logoutEventPublisher.record(sid, sub);
```

주석도 outbox 를 반영해 고친다.

- [ ] **Step 4: 폴러 작성**

`session/src/main/java/dev/starryeye/session/outbox/OutboxPublisher.java`:

```java
package dev.starryeye.session.outbox;

import dev.starryeye.session.jpa.OutboxEntity;
import dev.starryeye.session.jpa.OutboxEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

	/**
	 * outbox 의 미발행 행을 Kafka 로 옮긴다.
	 *
	 * 주의. 이 주기가 곧 보안 창이다. 로그아웃하고 최대 이만큼은 refresh token 이 아직 살아 있다.
	 *      원자성과 전달 보장을 얻는 대신 즉시성을 잃는 것이 outbox 의 대가다 — 동기 REST 는 정반대였다
	 *      (창이 0이지만 전달 보장이 없다).
	 *
	 * 주의. 한 행의 발행이 실패하면 그 자리에서 멈춘다. 뒤 행을 건너뛰고 계속하면 같은 파티션 키의
	 *      순서가 뒤바뀔 수 있다. 실패한 행은 다음 주기에 다시 시도된다.
	 *
	 * 주의. 발행 성공 후 markPublished 커밋 전에 죽으면 다음 주기에 같은 행을 다시 보낸다.
	 *      전달이 at-least-once 인 지점이 여기다. 소비자의 폐기가 조건부 갱신이라 멱등이므로 그대로 둔다.
	 */

	private final OutboxEntityRepository repository;
	private final KafkaTemplate<String, String> kafkaTemplate;

	@Scheduled(fixedDelayString = "${my.outbox-poll-interval-ms}")
	public void publishPending() {
		List<OutboxEntity> pending = repository.findTop100ByPublishedAtIsNullOrderByIdAsc();
		for (OutboxEntity row : pending) {
			try {
				kafkaTemplate.send(row.getTopic(), row.getPartitionKey(), row.getPayload())
						.get(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception e) {
				log.warn("outbox publish failed, will retry next cycle: id={} eventId={}",
						row.getId(), row.getEventId(), e);
				return; // 순서를 지키려고 그 자리에서 멈춘다
			}
			markPublished(row.getId());
		}
	}

	@Transactional
	public void markPublished(Long id) {
		repository.findById(id).ifPresent(row -> row.markPublished(Instant.now()));
	}
}
```

`session/src/main/resources/application.yml` 의 `my` 아래에 추가한다.

```yaml
  outbox-poll-interval-ms: 500
```

`session/src/test/resources/application.yml` 의 `my` 아래에도 같은 키를 추가한다(값은 `500`).

- [ ] **Step 5: `@EnableScheduling` 추가**

`session/src/main/java/dev/starryeye/session/SessionApplication.java` 에 `@EnableScheduling` 을 붙인다.

```java
@EnableScheduling
@SpringBootApplication
public class SessionApplication {
```

> **주의.** 이러면 테스트 컨텍스트에서도 폴러가 돈다. Task 6 의 테스트는 폴러가 도는 것과 무관하지만, 새로 쓰는 `OutboxPublisherTest` 는 폴러를 **직접 호출**해 검증하므로 자동 실행과 겹치지 않도록 `@SpringBootTest(properties = "my.outbox-poll-interval-ms=3600000")` 로 주기를 길게 잡는다.

- [ ] **Step 6: Task 6 의 테스트를 뒤집는다**

`DirectPublishFailureModeTest.java` 를 `OutboxFailureModeTest.java` 로 이름을 바꾸고 내용을 교체한다.

```java
package dev.starryeye.session.event;

import dev.starryeye.session.SessionService;
import dev.starryeye.session.jpa.OidcSessionEntityRepository;
import dev.starryeye.session.jpa.OutboxEntity;
import dev.starryeye.session.jpa.OutboxEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "my.outbox-poll-interval-ms=3600000")
class OutboxFailureModeTest {

	/**
	 * Task 6 이 고정했던 두 실패가 outbox 로 사라졌음을 같은 상황에서 확인한다.
	 *
	 * 주의. 이 클래스는 DirectPublishFailureModeTest 를 대체한 것이다. 커밋 diff 에서 두 파일을 나란히
	 *      보면 무엇이 달라졌는지가 드러난다 — 같은 상황, 반대 결과다.
	 */

	@Autowired
	private SessionService sessionService;

	@Autowired
	private OidcSessionEntityRepository sessionRepository;

	@Autowired
	private OutboxEntityRepository outboxRepository;

	@Test
	@DisplayName("Kafka 가 죽어 있어도 로그아웃은 커밋된다 — 편지는 outbox 에 남는다")
	void logoutCommitsEvenWhenKafkaIsDown() {
		// Kafka 를 목으로 죽일 필요가 없다. 이 컨텍스트에는 브로커가 없고, 발행은 폴러가 별도로 하므로
		// consumeForLogout 은 Kafka 를 전혀 부르지 않는다 — 그것이 outbox 의 요점이다.
		sessionService.register("SID-A", "user-sub-0001", "my-client");

		sessionService.consumeForLogout("SID-A");

		assertThat(sessionRepository.findBySid("SID-A")).isEmpty();

		List<OutboxEntity> pending = outboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc();
		assertThat(pending).hasSize(1);
		assertThat(pending.get(0).getPartitionKey()).isEqualTo("SID-A");
		assertThat(pending.get(0).getPublishedAt()).isNull();
	}

	@Test
	@DisplayName("로그아웃이 롤백되면 편지도 함께 사라진다 — 유령 이벤트가 불가능하다")
	void rollbackTakesTheEventWithIt() {
		sessionService.register("SID-B", "user-sub-0001", "my-client");
		long before = outboxRepository.count();

		// consumeForLogout 과 같은 트랜잭션 안에서 예외를 던져 롤백시킨다.
		try {
			sessionService.consumeForLogoutThenFail("SID-B");
		} catch (IllegalStateException expected) {
			// 의도된 롤백이다
		}

		assertThat(sessionRepository.findBySid("SID-B")).hasSize(1); // 삭제가 롤백됐다
		assertThat(outboxRepository.count()).isEqualTo(before);      // 편지도 롤백됐다
	}
}
```

`SessionService` 에 롤백 재현용 메서드를 추가한다.

```java
	/**
	 * 롤백 시 outbox 행도 함께 사라지는지 확인하기 위한 경로다. consumeForLogout 과 같은 트랜잭션 안에서
	 *      마지막에 예외를 던진다.
	 *
	 * 주의. 운영 코드가 아니다. 그러나 테스트 전용 하위 클래스나 목으로는 "같은 트랜잭션 안에서 커밋이
	 *      실패한다"를 재현할 수 없어(프록시를 우회하면 트랜잭션 자체가 안 걸린다) 서비스에 둔다.
	 *      호출자는 테스트뿐이며, 어떤 컨트롤러도 이 메서드를 노출하지 않는다.
	 */
	@Transactional
	public void consumeForLogoutThenFail(String sid) {
		consumeForLogoutInternal(sid);
		throw new IllegalStateException("simulated commit failure");
	}
```

`consumeForLogout` 의 본문을 `consumeForLogoutInternal(String sid)` 로 뽑아 두 메서드가 공유하게 한다.

> **주의.** 운영 코드에 테스트 전용 메서드를 두는 것은 원칙적으로 피해야 한다. 구현자가 **트랜잭션 프록시를 유지하면서 커밋 실패를 주입하는 더 나은 방법**(예: `TransactionSynchronization` 을 쓰는 테스트 전용 `@TestConfiguration` 빈, 또는 `@Transactional` 테스트에서 `TestTransaction.flagForRollback()`)을 찾으면 그쪽을 쓰고, 이 메서드는 넣지 않는다. 어느 쪽을 택했는지 보고서에 적는다.

- [ ] **Step 7: 폴러 테스트 작성**

`session/src/test/java/dev/starryeye/session/outbox/OutboxPublisherTest.java`:

```java
package dev.starryeye.session.outbox;

import dev.starryeye.session.SessionService;
import dev.starryeye.session.event.KafkaTopicConfig;
import dev.starryeye.session.jpa.OutboxEntityRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
		"my.outbox-poll-interval-ms=3600000" // 자동 실행을 사실상 끄고 직접 호출해 검증한다
})
@EmbeddedKafka(partitions = 3, topics = KafkaTopicConfig.LOGGED_OUT_TOPIC)
class OutboxPublisherTest {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private OutboxPublisher publisher;

	@Autowired
	private OutboxEntityRepository outboxRepository;

	@Autowired
	private EmbeddedKafkaBroker broker;

	private Consumer<String, String> consumer;

	@BeforeEach
	void subscribe() {
		Map<String, Object> props = KafkaTestUtils.consumerProps("outbox-test", "true", broker);
		consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(
				props, new StringDeserializer(), new StringDeserializer());
		broker.consumeFromAnEmbeddedTopic(consumer, KafkaTopicConfig.LOGGED_OUT_TOPIC);
	}

	@AfterEach
	void close() {
		consumer.close();
	}

	@Test
	@DisplayName("미발행 행을 Kafka 로 옮기고 발행 표시를 남긴다")
	void publishesPendingAndMarks() {
		sessionService.register("SID-A", "user-sub-0001", "my-client");
		sessionService.consumeForLogout("SID-A");
		assertThat(outboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc()).hasSize(1);

		publisher.publishPending();

		ConsumerRecord<String, String> record =
				KafkaTestUtils.getSingleRecord(consumer, KafkaTopicConfig.LOGGED_OUT_TOPIC);
		assertThat(record.key()).isEqualTo("SID-A");
		assertThat(outboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc()).isEmpty();
	}

	@Test
	@DisplayName("두 번 돌려도 같은 편지를 다시 보내지 않는다")
	void doesNotResendPublishedRows() {
		sessionService.register("SID-B", "user-sub-0001", "my-client");
		sessionService.consumeForLogout("SID-B");

		publisher.publishPending();
		KafkaTestUtils.getSingleRecord(consumer, KafkaTopicConfig.LOGGED_OUT_TOPIC);

		publisher.publishPending();

		assertThat(KafkaTestUtils.getRecords(consumer, java.time.Duration.ofSeconds(2)).count()).isZero();
	}
}
```

- [ ] **Step 8: 테스트 실행**

```bash
cd oauth-2/authorization-server/practice/microservice/session
JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn \
  java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon
```

Expected: BUILD SUCCESSFUL. `LogoutEventPublishTest` 는 발행 시점이 바뀌었으므로 **폴러를 직접 호출한 뒤 확인하도록 함께 고친다.**

- [ ] **Step 9: 테스트가 실제로 무는지 확인**

`LogoutEventPublisher.record` 의 `outboxRepository.save(...)` 를 주석 처리한다.

Expected: `OutboxFailureModeTest.logoutCommitsEvenWhenKafkaIsDown` 과 `OutboxPublisherTest` 두 테스트가 실패한다.

`git diff` 증거를 붙이고 원복한다.

- [ ] **Step 10: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/session/src
git commit -m "$(cat <<'EOF'
session: move logout events through an outbox

Task 6 이 고정한 두 실패를 닫는다. 같은 상황, 반대 결과다.

- Kafka 가 죽어 있어도 로그아웃은 커밋된다. 편지는 outbox 에 남아 다음
  주기에 나간다
- 로그아웃이 롤백되면 편지도 함께 사라진다. 유령 이벤트가 불가능하다

oidc_sessions 삭제와 outbox INSERT 가 같은 트랜잭션에 들어가는 것이
전부다. DB 와 Kafka 를 직접 묶을 방법이 없으니 편지를 DB 에 먼저 적는다.

주의. 폴링 주기(500ms)가 곧 보안 창이다. 로그아웃하고 최대 그만큼은
refresh 가 살아 있다. 원자성과 전달 보장을 얻는 대신 즉시성을 잃는 것이
outbox 의 대가다.

주의. 폴러에 잠금을 걸지 않았다. session 인스턴스가 하나뿐이라 폴러도
하나다. 인스턴스를 늘리면 중복 발행이 늘어나는데 소비자가 멱등이라
피해는 없다. 한계로 기록한다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 8: 소비 실패를 DLT 로 보낸다

**Files:**
- Create: `token-state/src/main/java/dev/starryeye/token_state/event/KafkaConsumerConfig.java`
- Test: `token-state/src/test/java/dev/starryeye/token_state/event/DeadLetterTopicTest.java` (신규)

**Interfaces:**
- Consumes: Task 5 의 `SessionLoggedOutConsumer.LOGGED_OUT_TOPIC`
- Produces: DLT 토픽 이름 `oidc.session.logged-out.v1.dlt`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package dev.starryeye.token_state.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.token_state.IssueResult;
import dev.starryeye.token_state.RefreshTokenService;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.apache.kafka.clients.consumer.Consumer;
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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {
		SessionLoggedOutConsumer.LOGGED_OUT_TOPIC,
		KafkaConsumerConfig.LOGGED_OUT_DLT
})
class DeadLetterTopicTest {

	/**
	 * 처리할 수 없는 편지 하나가 파티션 전체를 막으면 안 된다. 한 사용자의 폐기가 안 되는 동안
	 *      다른 사용자들 폐기까지 멈추기 때문이다(head-of-line blocking).
	 *
	 * 주의. 파티션을 1로 잡는다. 두 편지가 반드시 같은 줄에 서야 "뒤가 막히지 않는다"를 확인할 수 있다.
	 */

	@Autowired
	private RefreshTokenService service;

	@Autowired
	private RefreshTokenEntityRepository repository;

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private EmbeddedKafkaBroker broker;

	private Consumer<String, String> dltConsumer;

	@BeforeEach
	void subscribeDlt() {
		Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-test", "true", broker);
		dltConsumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(
				props, new StringDeserializer(), new StringDeserializer());
		broker.consumeFromAnEmbeddedTopic(dltConsumer, KafkaConsumerConfig.LOGGED_OUT_DLT);
	}

	@AfterEach
	void close() {
		dltConsumer.close();
	}

	@Test
	@DisplayName("처리할 수 없는 편지는 DLT 로 가고 뒤 편지는 정상 처리된다")
	void poisonMessageGoesToDltAndDoesNotBlockTheQueue() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-GOOD");

		// 1) 역직렬화가 불가능한 편지 — 몇 번을 시도해도 낫지 않는다
		kafkaTemplate.send(SessionLoggedOutConsumer.LOGGED_OUT_TOPIC, "SID-POISON", "{not json").get(5, TimeUnit.SECONDS);

		// 2) 뒤이어 정상 편지
		String good = objectMapper.writeValueAsString(new SessionLoggedOutEvent(
				UUID.randomUUID().toString(), "SID-GOOD", "user-sub-0001", Instant.now()));
		kafkaTemplate.send(SessionLoggedOutConsumer.LOGGED_OUT_TOPIC, "SID-GOOD", good).get(5, TimeUnit.SECONDS);

		// 앞 편지가 DLT 로 빠진다
		assertThat(KafkaTestUtils.getSingleRecord(dltConsumer, KafkaConsumerConfig.LOGGED_OUT_DLT,
				java.time.Duration.ofSeconds(20)).value()).isEqualTo("{not json");

		// 뒤 편지는 막히지 않고 처리된다
		await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
				assertThat(repository.findByFamilyId(issued.familyId()).get(0).getStatus())
						.isEqualTo(RefreshTokenStatus.REVOKED));
	}
}
```

- [ ] **Step 2: 테스트 실패 확인**

Expected: `KafkaConsumerConfig` 가 없어 컴파일 실패.

- [ ] **Step 3: 소비자 설정 작성**

```java
package dev.starryeye.token_state.event;

import org.apache.kafka.clients.admin.NewTopic;
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
	 * 소비 실패를 제한 재시도 후 DLT 로 보낸다.
	 *
	 * 주의. 무한 재시도를 걸면 그 파티션의 뒷 이벤트가 전부 막힌다(head-of-line blocking). 한 사용자의
	 *      폐기가 안 되는 동안 다른 사용자들 폐기까지 멈춘다. 그래서 몇 번 시도하고 안 되면 그 편지를
	 *      따로 빼놓고 다음으로 넘어간다 — 실패가 조용히 사라지지도 않고, 줄도 막히지 않는다.
	 *
	 * 주의. DLT 로 보낸 편지는 아무도 자동으로 다시 처리하지 않는다. 그 세션의 refresh token 은 폐기되지
	 *      않은 채 남는다. 로그와 DLT 가 그 사실을 남기는 유일한 수단이므로, 운영이라면 DLT 적재를
	 *      경보 대상으로 삼아야 한다.
	 */
	public static final String LOGGED_OUT_DLT = SessionLoggedOutConsumer.LOGGED_OUT_TOPIC + ".dlt";

	@Bean
	NewTopic sessionLoggedOutDlt() {
		return TopicBuilder.name(LOGGED_OUT_DLT).partitions(3).replicas(1).build();
	}

	@Bean
	DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
			@Value("${my.consumer-retry-attempts}") long retryAttempts,
			@Value("${my.consumer-retry-interval-ms}") long retryIntervalMs) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
				(record, exception) -> new org.apache.kafka.common.TopicPartition(LOGGED_OUT_DLT, -1));
		return new DefaultErrorHandler(recoverer, new FixedBackOff(retryIntervalMs, retryAttempts));
	}
}
```

`token-state/src/main/resources/application.yml` 의 `my` 아래에 추가한다.

```yaml
  consumer-retry-attempts: 2
  consumer-retry-interval-ms: 200
```

`token-state/src/test/resources/application.yml` 의 `my` 아래에도 같은 두 키를 추가한다.

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd oauth-2/authorization-server/practice/microservice/token-state
JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn \
  java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 테스트가 실제로 무는지 확인**

`kafkaErrorHandler` 빈을 통째로 주석 처리한다(기본 에러 핸들러가 쓰이게 된다).

Expected: `poisonMessageGoesToDltAndDoesNotBlockTheQueue` 가 실패한다 — DLT 에 아무것도 오지 않는다.

`git diff` 증거를 붙이고 원복한다.

- [ ] **Step 6: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/token-state/src
git commit -m "$(cat <<'EOF'
token-state: send unprocessable logout events to a dead letter topic

처리할 수 없는 편지 하나가 파티션 전체를 막으면 안 된다. 한 사용자의
폐기가 안 되는 동안 다른 사용자들 폐기까지 멈춘다.

제한 재시도(2회) 후 oidc.session.logged-out.v1.dlt 로 보내고 다음으로
넘어간다. 실패가 조용히 사라지지도 않고 줄도 막히지 않는다.

주의. DLT 로 간 편지는 아무도 자동으로 재처리하지 않는다. 그 세션의
refresh token 은 폐기되지 않은 채 남는다. 운영이라면 DLT 적재 자체가
경보 대상이다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 9: e2e 검증과 문서

**Files:**
- Create: `http/slice7-logout-revokes-refresh.http`
- Modify: `README.md`

**Interfaces:**
- Consumes: Task 1~8 전부
- Produces: 없음

- [ ] **Step 1: 전 서비스 기동**

```bash
cd oauth-2/authorization-server/practice/microservice
docker compose -p microservice-as -f docker-compose/docker-compose.yml down -v
docker compose -p microservice-as -f docker-compose/docker-compose.yml up -d
sleep 20
export JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn
for m in signing user-directory client-registry consent token-state session auth token demo-rp; do
  (cd $m && $JAVA_HOME/bin/java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain bootJar --no-daemon -q)
done
for m in signing user-directory client-registry consent token-state session auth token demo-rp; do
  (cd $m && nohup $JAVA_HOME/bin/java -jar build/libs/$m-0.0.1-SNAPSHOT.jar > /tmp/$m.log 2>&1 &)
done
sleep 30
```

- [ ] **Step 2: 성공 기준 1·2 확인 (분리)**

Task 1 Step 7 의 권한 오류 명령과, 스키마별 테이블 목록 명령을 다시 실행해 출력을 기록한다.

- [ ] **Step 3: 성공 기준 3 확인 — 로그아웃 전후로 refresh 가 갈린다**

`http/slice7-logout-revokes-refresh.http` 를 만들어 절차를 남긴다. 슬라이스 5의 e2e 절차(로그인 → authorize → 동의 → code 교환)를 재사용하되, `scope` 에 `offline_access` 를 포함한다.

```
### 이 파일은 절차 기록이다. 실제 실행은 아래 curl 흐름으로 한다.
###
### 1. 로그인해 쿠키를 얻는다 (auth:8081, gateway 경유 9000)
### 2. GET /oauth2/authorize?...&scope=openid%20offline_access 로 동의 화면까지 간다
### 3. POST /oauth2/consent 로 승인해 code 를 받는다
### 4. POST /oauth2/token 으로 code 를 교환해 access_token + refresh_token + id_token 을 받는다
### 5. [기준 3-전] POST /oauth2/token grant_type=refresh_token → 200, 새 access_token 이 나온다
### 6. GET /oauth2/logout 으로 로그아웃한다
### 7. 폴링 주기(500ms)를 넘겨 1초 기다린다
### 8. [기준 3-후] 같은 refresh_token 으로 다시 → 400 invalid_grant
```

절차를 실제로 실행하고, 5번과 8번의 응답을 그대로 기록한다.

- [ ] **Step 4: 성공 기준 4 확인 — 다른 세션은 산다**

쿠키 항아리를 두 개(`/tmp/jar-a`, `/tmp/jar-b`) 써서 같은 사용자로 두 번 로그인하고 각각 refresh token 을 받는다. A 만 로그아웃한 뒤 두 토큰을 각각 써서 A 는 `invalid_grant`, B 는 `200` 인 것을 확인한다.

DB 로도 확인한다.

```bash
docker exec -i microservice-as-mysql-1 mysql -usvc_token_state -ppw_token_state -e \
  "SELECT sid, status, revoked_reason FROM ms_token_state.refresh_tokens ORDER BY id"
```

Expected: A 의 `sid` 행만 `REVOKED` / `SESSION_LOGGED_OUT`, B 의 행은 `ACTIVE`.

- [ ] **Step 5: 성공 기준 5 확인 — refresh 재발급 id token 에 sid**

4번에서 받은 B 의 refresh 로 재발급하고, 응답의 `id_token` payload 를 디코드해 `sid` claim 이 있는지 확인한다.

```bash
echo '<id_token 의 payload 부분>' | base64 -d 2>/dev/null | python3 -m json.tool
```

- [ ] **Step 6: 성공 기준 8 확인 — Kafka 를 내려도 로그아웃이 커밋된다**

```bash
docker compose -p microservice-as -f docker-compose/docker-compose.yml stop kafka
# 새 세션으로 로그인 → refresh 획득 → 로그아웃
docker exec -i microservice-as-mysql-1 mysql -usvc_session -ppw_session -e \
  "SELECT id, partition_key, published_at FROM ms_session.outbox ORDER BY id"
# → 그 sid 의 행이 published_at NULL 로 남아 있어야 한다
docker compose -p microservice-as -f docker-compose/docker-compose.yml start kafka
sleep 15
docker exec -i microservice-as-mysql-1 mysql -usvc_token_state -ppw_token_state -e \
  "SELECT sid, status FROM ms_token_state.refresh_tokens WHERE sid = '<그 sid>'"
# → REVOKED 로 바뀌어 있어야 한다
```

각 단계의 출력을 그대로 기록한다.

- [ ] **Step 7: 성공 기준 12 확인 — 보안 창 실측**

로그아웃 요청을 보낸 시각과 `refresh_tokens.revoked_at` 의 차이를 잰다.

```bash
date +%s.%N   # 로그아웃 직전
# ... 로그아웃 실행 ...
docker exec -i microservice-as-mysql-1 mysql -usvc_token_state -ppw_token_state -N -e \
  "SELECT UNIX_TIMESTAMP(revoked_at) FROM ms_token_state.refresh_tokens WHERE sid = '<그 sid>'"
```

3회 반복해 값을 기록한다. **측정하지 않은 값을 적지 않는다.**

- [ ] **Step 8: README 갱신**

아래를 추가·수정한다.

1. **기동 방법** — `docker compose ... down -v` 가 필요하다는 것, Kafka 가 추가됐다는 것
2. **서비스별 책임과 소유 데이터** 표 — 스키마·계정 열 추가
3. **검증된 성공 기준** 절 — Step 2~7 에서 **실제로 얻은 출력**만 옮긴다
4. **알려진 한계** — 설계 문서 10절의 6개 항목을 옮긴다(보안 창, 폴러 경쟁, `sid` null 행, outbox 정리 수단 없음, `session` 다운 시 fail-open, `root` 잔존)
5. **설계/계획 문서** 절에 슬라이스 7 링크 두 줄 추가

- [ ] **Step 9: 전체 테스트 회귀**

```bash
export JAVA_HOME=/Users/starryeye/.sdkman/candidates/java/21.0.6-amzn
for m in auth client-registry consent demo-rp session signing token-state token user-directory; do
  (cd oauth-2/authorization-server/practice/microservice/$m && \
   $JAVA_HOME/bin/java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test --no-daemon > /tmp/test-$m.log 2>&1) \
   && echo "PASS $m" || echo "FAIL $m"
done
```

Expected: 9개 모듈 전부 PASS.

- [ ] **Step 10: 정리와 커밋**

```bash
pkill -f 'build/libs/.*-0.0.1-SNAPSHOT.jar'
docker compose -p microservice-as -f docker-compose/docker-compose.yml down

git add oauth-2/authorization-server/practice/microservice/http/slice7-logout-revokes-refresh.http \
        oauth-2/authorization-server/practice/microservice/README.md
git commit -m "$(cat <<'EOF'
microservice: verify slice 7 end to end and document it

성공 기준을 실제로 실행해 확인했다. README 에는 실행해서 얻은 출력만
옮겼다.

- 남의 스키마 조회가 권한 오류로 막힌다
- 로그아웃 전에는 refresh 로 재발급되고, 로그아웃 후에는 invalid_grant
- 다른 브라우저 세션의 refresh 는 살아 있다
- refresh 재발급 id token 에 sid 가 실린다
- Kafka 를 내린 채 로그아웃해도 트랜잭션은 커밋되고 outbox 에 남는다.
  Kafka 를 올리면 그때 나가서 폐기된다
- 보안 창 실측값을 기록했다

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## 부록: 계획 자체 검토 결과

**스펙 커버리지** — 설계 문서 8절의 성공 기준 12개가 태스크에 매핑된다.

| 기준 | 태스크 |
|---|---|
| 1, 2 (분리) | Task 1 Step 5~8, Task 9 Step 2 |
| 3 (로그아웃 후 invalid_grant) | Task 9 Step 3 |
| 4 (다른 세션은 산다) | Task 4 Step 1, Task 9 Step 4 |
| 5 (재발급 id token 의 sid) | Task 3 Step 1, Task 9 Step 5 |
| 6, 7 (직접 발행의 실패 재현) | Task 6 |
| 8, 9 (outbox 로 뒤집힘) | Task 7 Step 6, Task 9 Step 6 |
| 10 (멱등) | Task 4 Step 1 |
| 11 (DLT, 줄이 안 막힘) | Task 8 |
| 12 (보안 창 실측) | Task 9 Step 7 |

**설계와 달라진 점 (구현 시 설계 문서도 함께 갱신할 것)**

- 설계 5절은 페이로드의 `sub` 를 필수처럼 서술했으나, 실제로는 **nullable** 이다. 등록된 RP 가 없는 세션(openid 없이 offline_access 만 받은 경로)은 `oidc_sessions` 행이 없어 소유자를 알 수 없는데, 그 `sid` 의 refresh token 은 존재할 수 있어 이벤트는 발행해야 한다. 소비자가 쓰는 값은 `sid` 하나라 폐기는 정상 동작한다. **Task 5 에서 설계 문서 5절과 10절을 이 사실로 갱신한다** — 이 저장소에서 문서가 stale 해지는 것이 네 번 재발했다.
- 설계 6절의 "직접 발행" 단계는 **트랜잭션 안에서 블로킹 발행**으로 구체화됐다. 그래서 재현되는 실패가 "유실"이 아니라 "로그아웃 트랜잭션 전체 롤백"이다. 결과는 같다(세션도 안 지워지고 refresh 도 안 죽는다). **Task 6 에서 설계 문서 6절의 표를 이 사실로 갱신한다.**

**미해결로 남기는 판단 두 가지 (구현자가 결정하고 보고할 것)**

- Task 3 Step 1 의 테스트가 참조하는 `RefreshTokenGrantService` 생성자와 `ClientInfo` 생성자 시그니처는 계획 작성 시점의 추정이다. 실제와 다르면 **테스트를 실제 시그니처에 맞추고** 그 사실을 보고한다.
- Task 7 Step 6 의 커밋 실패 주입 방법. 운영 코드에 테스트 전용 메서드를 두지 않고 재현할 수 있으면 그쪽을 택한다.
