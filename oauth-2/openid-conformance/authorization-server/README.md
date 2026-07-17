# authorization server (OP) 테스트 가이드

- suite 가 RP(client) 역할이 되어 내 authorization server 를 공략한다..
  discovery 를 읽고, 정상 code flow 부터 비정상 케이스(변조된 state/nonce, 미등록 redirect_uri, request object,
  code 재사용, 토큰 검증 등)까지 스펙 요구사항을 모듈 단위로 검증한다.
- authorize 단계는 실제 브라우저 상호작용(로그인/동의)이 필요하다.. 사람이 클릭하거나 자동화 설정(browser 자동화)을 쓴다.

## 공통 준비물

1. **테스트 플랜 선택**.. OP 인증 플랜은 response_type 별로 나뉜다. spring authorization server 는 OAuth 2.1 기반이라
   implicit/hybrid 를 지원하지 않으므로 **Basic 플랜**이 대상이다.
   - `oidcc-basic-certification-test-plan[server_metadata=discovery][client_registration=static_client]`
2. **client 3개 사전 등록** (static_client 방식 기준.. dynamic client registration 을 지원하면 등록 생략 가능)
   - client_secret_basic 용 2개(client, client2) + client_secret_post 용 1개
   - redirect uri: `{suite 주소}/test/a/{alias}/callback` (alias 는 내가 정하는 고유 이름.. 플랜 설정의 alias 와 일치해야 한다)
3. **설정 JSON**.. 플랜 생성 시 입력한다. 뼈대:
   ```json
   {
     "alias": "my-op-test",
     "server": { "discoveryUrl": "{내 서버}/.well-known/openid-configuration" },
     "client": { "client_id": "...", "client_secret": "..." },
     "client_secret_post": { "client_id": "...", "client_secret": "..." },
     "client2": { "client_id": "...", "client_secret": "..." }
   }
   ```

---

## 방법 1. 로컬 서버 테스트 (suite self-host)

로컬/사내 서버는 호스팅 suite 가 접근할 수 없으므로 suite 를 서버 옆(docker)에 띄운다.

### suite 기동

```shell
curl -O https://gitlab.com/openid/conformance-suite/-/raw/master/docker-compose-prebuilt.yml
docker compose -f docker-compose-prebuilt.yml up -d
```

- prebuilt 이미지라 소스 빌드가 필요 없다. UI: https://localhost.emobix.co.uk:8443
  (이 도메인은 공개 DNS 가 127.0.0.1 로 해석해준다.. hosts 파일 수정 불필요. 자체 서명 인증서라 브라우저 경고는 계속 진행)
- 기본이 dev mode 라 UI 로그인 없이 사용할 수 있다.

### 대상 서버 주소의 함정 (중요)

suite 도, suite 안의 자동화 브라우저도 "docker 컨테이너 안"에서 내 서버에 접근한다.

- discoveryUrl 의 host 는 localhost 가 아니라 **host.docker.internal** 이어야 한다.
  (컨테이너 안에서 localhost 는 컨테이너 자신이다)
  - issuer 기반으로 절대 URL 을 만드는 서버라면 issuer 자체를 host.docker.internal 로 기동해야 한다.
- 자동화 브라우저가 콜백(localhost.emobix.co.uk:8443)에 접근하려면 nginx 서비스에 docker 네트워크 alias 가 필요하다..
  override 파일을 함께 사용한다:
  ```yaml
  # compose-alias-override.yml
  services:
    nginx:
      networks:
        default:
          aliases:
            - localhost.emobix.co.uk
  ```
  ```shell
  docker compose -f docker-compose-prebuilt.yml -f compose-alias-override.yml up -d
  ```

### 실행 (UI 수동)

1. UI 에서 "Create a new test plan" -> 플랜/variant 선택 -> 설정 JSON 입력 -> 플랜 생성
2. 모듈을 하나씩 실행.. authorize 단계마다 브라우저 창이 뜨면 로그인/동의를 직접 진행한다.
3. 에러 페이지 확인형 모듈은 화면 스크린샷을 업로드하라고 요구한다. (redirect 없이 에러를 표시하는 것이 정답인 케이스들)

### 실행 (자동화.. CI 용)

설정 JSON 에 browser 자동화를 추가하면 suite 내장 selenium 이 로그인을 대신한다:

```json
"browser": [{
  "match": "http://host.docker.internal:9000/oauth2/authorize*",
  "tasks": [
    { "task": "Login", "optional": true, "match": "http://host.docker.internal:9000/login*",
      "commands": [["text", "name", "username", "user"], ["text", "name", "password", "1111"], ["click", "css", "button[type=submit]"]] },
    { "task": "Verify Complete", "match": "*/test/a/*/callback*",
      "commands": [["wait", "id", "submission_complete", 10]] }
  ]
}]
```

- consent 화면이 있으면 task 를 추가하거나, 테스트용 client 를 동의 생략(requireAuthorizationConsent=false)으로 등록하는 것이 간단하다.
- 플랜 전체 실행은 suite 레포의 스크립트를 쓴다 (python + httpx/pyparsing 필요):
  ```shell
  CONFORMANCE_SERVER=https://localhost.emobix.co.uk:8443/ CONFORMANCE_DEV_MODE=1 DISABLE_SSL_VERIFY=1 \
    ./run-test-plan.py "oidcc-basic-certification-test-plan[server_metadata=discovery][client_registration=static_client]" my-config.json
  ```
  (스크립트 옆에 conformance.py, test_plan_parser.py, 빈 certs-keys/ 디렉토리가 있어야 한다)

### 이 방식의 실전 기록

이 repo 에서 LB 뒤 2 인스턴스 서버에 실제로 돌린 기록:
[practice/production-ready-authorization-server/openid-conformance/](../../authorization-server/practice/production-ready-authorization-server/openid-conformance/README.md)
(부적합 3종 발견/수정, 브라우저 자동화 설정 전문, 결과표 포함)

---

## 방법 2. 인터넷에 공개된 서버 테스트 (호스팅 suite)

서버가 https 로 공개되어 있으면 설치 없이 https://www.certification.openid.net 를 쓴다. 정식 인증(certification)도 이 경로다.

1. Google/GitLab 계정으로 로그인
2. "Create a new test plan" -> `OpenID Connect Core: Basic Certification Profile` 계열 선택
   -> server_metadata=discovery, client_registration=static_client 선택
3. 설정 입력.. discoveryUrl 은 공개 주소, client 3개는 내 서버에
   redirect uri `https://www.certification.openid.net/test/a/{alias}/callback` 로 사전 등록해둔다.
4. 모듈 실행은 로컬과 동일 (브라우저 수동 or browser 자동화 JSON).
   스크립트 실행 시에는 로그인 계정의 API 토큰이 필요하다 (UI 의 token 관리 페이지에서 발급 -> CONFORMANCE_TOKEN 환경변수).
5. **정식 인증까지 가려면**.. 해당 프로파일의 모든 모듈이 통과(또는 허용된 스킵)된 플랜에서 인증 패키지를 내려받아
   OIDF 에 제출한다(수수료). 통과하면 "OpenID Certified" 목록에 등재되고 마크를 쓸 수 있다.

- 참고. 로컬 서버를 터널링(ngrok 등)으로 잠깐 공개해서 호스팅 suite 로 테스트할 수도 있지만..
  인증 서버를 인터넷에 노출하는 것이므로 테스트 계정/데이터만 있는 환경에서만 할 것.

---

## 함정 모음

- redirect uri 는 항상 **정확 일치(exact match)** 로 등록/검증된다.. alias 를 바꾸면 client 재등록이 필요하다.
- suite 안의 자동화 브라우저까지 고려한 네트워크 경로를 그려볼 것.. "suite 컨테이너 -> 내 서버", "자동화 브라우저 -> 내 서버 + suite 자신" 모두 뚫려 있어야 한다.
- 에러 페이지형 모듈(스크린샷 업로드)과 수동 확인형 모듈("재로그인 되었는가")은 자동화로는 FAILED/INTERRUPTED 로 남는다..
  자동화 실행에서는 이 모듈들을 결과 해석에서 분리해서 봐야 한다. (정식 인증 제출은 수동으로 마저 채운다)
- 서버 로그와 suite 의 모듈 로그(log-detail 화면)를 같이 봐야 원인이 보인다.. suite 로그에는 요청/응답 전문이 남는다.
