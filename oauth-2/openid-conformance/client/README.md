# client (RP) 테스트 가이드

- suite 가 **가짜 OP(authorization server)** 가 되어 내 client 의 검증 로직을 공략한다..
  정상 응답뿐 아니라 일부러 틀린 응답(잘못된 iss/aud, 틀린 nonce, 서명이 깨진 id token, 잘못된 c_hash 등)을 돌려주고
  **client 가 그것을 거부하는지** 를 본다. RP 테스트의 본질은 "우리 client 가 검증을 생략하고 있지 않은가" 검사다.
- spring-security oauth2-client 처럼 검증을 프레임워크가 수행하는 구조라면.. 이 테스트는 사실상
  "프레임워크를 스펙대로 설정했는가 + 프레임워크가 스펙을 지키는가" 를 함께 확인하는 일이 된다.

## OP 테스트와 다른 점 (중요)

RP 테스트는 내 client 가 suite 로 나가는(outbound) 요청만 하면 된다.. **client 가 로컬에 있어도 호스팅 suite 로 바로 테스트할 수 있다.**
(브라우저 redirect 는 브라우저가 다니고, token 요청은 client 의 아웃바운드 호출이라 client 를 인터넷에 공개할 필요가 없다)

| 상황 | 권장 방법 |
|---|---|
| 로컬 개발 client, 인터넷 가능 | **방법 1. 호스팅 suite (가장 간단)** |
| 폐쇄망 / CI 파이프라인 | 방법 2. suite self-host |

## 공통: 테스트 플랜

- RP 테스트 플랜: `oidcc-client-test-plan` + variant 로 내 client 의 방식을 지정한다.
  - 예. `[client_auth_type=client_secret_basic][request_type=plain_http_request][response_type=code][response_mode=default][client_registration=static_client]`
  - spring oauth2-client 기본 조합이 위 예시다. (code flow, basic 인증, request object 미사용)
- client_registration
  - static_client: 내 client 가 쓸 client_id/secret 을 설정 JSON 에 미리 적는다.
  - dynamic_client: suite 가 테스트마다 임시 client 를 만들어준다. (내 client 가 동적 등록을 지원할 때)
- alias 는 suite 가 만들어주는 가짜 OP 의 주소 경로에 들어간다.. 플랜 생성 후 화면의 exported values 에서
  **issuer / discovery URL 을 확인해 내 client 의 provider 설정에 넣는다.**

## 진행 방식 (두 방법 공통)

1. 플랜 생성 후 테스트 모듈을 하나 시작하면 상태가 **WAITING** 이 된다. (suite 의 가짜 OP 가 준비된 상태)
2. 이때 **내 client 에서 로그인 flow 를 시작**한다. (브라우저에서 client 접속 -> OP 로그인 버튼)
3. suite 가 flow 를 받아 정상/변조 응답을 주고, client 의 요청/반응을 판정한 뒤 모듈이 FINISHED 로 끝난다.
4. 다음 모듈 시작 -> 다시 client 로그인 트리거.. 를 반복한다.
   - 잘못된 응답을 주는 negative 모듈에서는 client 가 로그인 실패로 끝나는 것이 정답이다.
   - CI 자동화라면 client 로그인 트리거를 스크립트로 반복시킨다. (suite 레포 CI 는 sample client 로 이 방식을 쓴다)

## spring oauth2-client 설정 예시

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          conformance:
            client-id: my-conformance-client        # static_client 면 설정 JSON 의 client_id 와 일치
            client-secret: my-conformance-secret
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope: openid
        provider:
          conformance:
            issuer-uri: https://www.certification.openid.net/test/a/{alias}/   # 플랜 생성 후 exported values 의 issuer 로 교체
```

- redirect uri (`http://localhost:8080/login/oauth2/code/conformance` 등)는 suite 의 client 설정에 등록해준다.
- issuer-uri 방식은 첫 사용 시 discovery 를 읽으므로.. **모듈을 먼저 WAITING 으로 만들어두고** client 를 기동/로그인해야 한다.

---

## 방법 1. 호스팅 suite 로 테스트 (로컬 client 그대로)

1. https://www.certification.openid.net 에 Google/GitLab 계정으로 로그인
2. "Create a new test plan" -> RP 테스트 플랜(oidcc-client-test-plan) + variant 선택
3. alias, client 정보(static 이면 client_id/secret/redirect uri) 입력 -> 플랜 생성
4. exported values 의 issuer 를 위 yml 에 넣고 client 기동
5. 모듈 시작(WAITING) -> client 로그인 트리거 -> 판정 확인.. 반복
6. 전 모듈 통과 후 정식 인증을 원하면 인증 패키지를 내려받아 OIDF 에 제출한다(수수료)

- https 인증서가 정식이라 truststore 작업이 없다.. 이것도 호스팅 방식이 간단한 이유다.

## 방법 2. suite self-host 로 테스트 (폐쇄망/CI)

1. suite 기동은 [authorization-server 가이드](../authorization-server/README.md)의 self-host 절과 동일하다.
   (RP 테스트는 suite 가 서버 역할이므로 network alias override 는 필요 없다.. 접근 주체가 내 client 와 내 브라우저뿐)
2. issuer 는 `https://localhost.emobix.co.uk:8443/test/a/{alias}/` 형태가 된다.
3. **자체 서명 인증서 신뢰가 필요하다..** client(JVM)가 suite 의 token/jwks endpoint 를 https 로 호출하기 때문.
   ```shell
   # suite 인증서 추출
   openssl s_client -connect localhost.emobix.co.uk:8443 </dev/null 2>/dev/null | openssl x509 > conformance.crt
   # 별도 truststore 를 만들어 JVM 옵션으로 주입 (시스템 cacerts 를 건드리지 않는 방법)
   keytool -importcert -noprompt -alias conformance -file conformance.crt -keystore conformance-truststore.p12 -storetype PKCS12 -storepass changeit
   java -Djavax.net.ssl.trustStore=conformance-truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit -jar my-client.jar
   ```
   - 주의. trustStore 를 교체하면 기본 cacerts 가 대체되어 다른 https 호출이 깨질 수 있다..
     테스트 전용 기동에서만 쓰거나, 기본 cacerts 를 복사한 파일에 추가하는 방식을 쓴다.
4. 이후 진행은 방법 1 과 동일하다.

---

## 함정 모음

- negative 모듈에서 client 가 "성공" 해버리면 그게 바로 결함이다.. (예. 서명이 깨진 id token 으로 로그인 성공 = 검증 생략)
  실패 화면이 나왔다고 당황하지 말고 모듈의 기대 결과를 먼저 읽을 것.
- 모듈을 시작하기 전에 client 가 discovery 를 캐시해버렸다면 재기동이 필요할 수 있다.. "모듈 시작 -> 로그인" 순서를 지킨다.
- redirect uri 불일치는 suite 쪽 client 설정과 spring 의 registrationId 경로가 어긋난 경우가 대부분이다.
