# OpenID Conformance Suite 실습 기록

- OpenID Foundation 의 공식 적합성 테스트 도구(conformance suite)를 self-host 로 띄워서
  이 authorization server(LB 뒤 2 인스턴스 구도 그대로)에 **OIDCC Basic OP 인증 플랜**을 돌린 기록이다.
- suite 가 RP(client) 역할이 되어 discovery 부터 code flow 의 정상/비정상 케이스까지 스펙 요구사항을 검증한다.
- keycloak 등의 "OpenID Certified" 마크가 이 suite 통과 + OIDF 등록(유료)의 결과물이다. (suite 사용 자체는 무료)

## 실행 방법

1. suite 기동 (prebuilt 이미지.. 소스 빌드 불필요)
   ```shell
   curl -O https://gitlab.com/openid/conformance-suite/-/raw/master/docker-compose-prebuilt.yml
   # compose-alias-override.yml 은 이 디렉토리의 파일 사용 (아래 함정 1 참고)
   docker compose -f docker-compose-prebuilt.yml -f compose-alias-override.yml up -d
   ```
   - UI: https://localhost.emobix.co.uk:8443 (이 도메인은 공개 DNS 가 127.0.0.1 로 해석해준다.. hosts 수정 불필요)
2. 이 프로젝트 기동.. issuer 를 suite 컨테이너가 접근 가능한 주소로 오버라이드
   ```shell
   java -jar build/libs/*.jar --my.issuer=http://host.docker.internal:9000
   java -jar build/libs/*.jar --my.issuer=http://host.docker.internal:9000 --server.port=8092
   ```
3. conformance 용 client 3개 등록 (admin API).. redirect uri 는 suite 콜백, 동의 화면은 자동화를 위해 생략
   - `POST /registered-clients` x3 (config-template.json 의 client, client2, client_secret_post 자리)
   - redirectUri: `https://localhost.emobix.co.uk:8443/test/a/{alias}/callback`, requireAuthorizationConsent: false
4. 플랜 실행 (suite 레포의 run-test-plan.py + config-template.json 을 채운 설정 파일)
   ```shell
   CONFORMANCE_SERVER=https://localhost.emobix.co.uk:8443/ CONFORMANCE_DEV_MODE=1 DISABLE_SSL_VERIFY=1 \
     ./run-test-plan.py "oidcc-basic-certification-test-plan[server_metadata=discovery][client_registration=static_client]" config.json
   ```
   - 로그인 상호작용은 config 의 browser 자동화(suite 내장 selenium)가 처리한다.

## 최종 결과 (35 모듈, 부적합 3건 수정 완료 기준)

| 결과 | 수 | 모듈 |
|---|---|---|
| PASSED | 19 | userinfo(get/post-header), nonce 생략, display 계열, prompt-none 계열, max-age-10000, id-token-hint, login-hint, ui/claims-locales, code 재사용 거부 계열, client_secret_post, refresh token, PKCE, **POST 인가 요청** 등 |
| WARNING | 4 | 아래 "경고 해석" |
| SKIPPED | 7 | scope-profile/email/address/phone/all 등.. discovery 의 scopes_supported 에 openid 만 선언되어 조건 미충족 (프로필 claim 소스가 없는 우리 구성에선 스펙상 정직한 상태) + request object 미지원 선언에 따른 스킵 1건 (아래 수정 2) |
| FAILED (자동화 한계) | 5 | 에러 페이지 스크린샷 업로드형 3건 + "재로그인 됐는지" 수동 확인형 2건.. 서버 동작 자체는 curl 로 적합함을 확인했다 |
| FAILED (실제 부적합) | 0 | 처음 실행에서 3종(아래)이 발견되었고 전부 수정했다 |

## suite 가 잡아준 부적합 3종과 수정 (이 실습의 최대 수확)

### 1. id token 의 auth_time 이 인스턴스마다 다르게 발급되는 다중 인스턴스 결함 (3개 모듈 실패 -> PASSED)

- 메커니즘 (spring authorization server 1.4.3 바이트코드로 확인)..
  - openid scope 의 token 발급 시 JwtGenerator 가 SessionInformation 에서 sid 와 auth_time(getLastRequest())을 id token 에 넣는다.
  - SessionInformation 은 SessionRegistry 에서 조회하는데, 빈이 없으면 **인스턴스별 InMemory(SessionRegistryImpl)** 가 생성되고
    로그인을 처리한 인스턴스에만 세션이 등록된다.
  - LB round robin 으로 두 인스턴스에 서로 다른 시각의 세션들이 누적되고, token 요청을 받은 인스턴스가
    "자기가 기억하는 principal 의 최신 세션" 시각을 auth_time 으로 넣는다.
  - -> 재인증이 없었는데도 두 id token 의 auth_time 이 달라짐 (스펙 위반, 단일 인스턴스에서는 재현되지 않는 결함)
- 수정.. `SpringSessionSessionRegistry` (세션의 진실인 spring session/redis 로 조회 위임) + 로그인 성공 시각을 세션에 기록해
  auth_time 으로 반환. `spring.session.redis.repository-type=indexed` 전제. (해당 클래스 주석 참고)

### 2. request 파라미터(JWT request object)를 조용히 무시 (1개 모듈 실패 -> 미지원 선언에 따른 SKIPPED)

- spring authorization server 는 request object(OIDC Core 6)를 지원하지 않으며 거부 로직도 없다.. 파라미터를 무시하고
  쿼리 파라미터만으로 인가를 진행하므로, client 는 request object 안의 값이 반영됐다고 믿게 된다. (조용한 무시가 무는 위험)
- 스펙은 미지원 OP 가 해당 파라미터를 받으면 request_not_supported(request_uri_not_supported) 에러를 요구한다.
- 수정.. `RequestObjectRejectingAuthenticationProvider` (기본 provider 래핑, 등록된 redirect uri 일 때만 error redirect)
  + discovery 에 request_parameter_supported/request_uri_parameter_supported=false 명시.
- suite 판정: "request_not_supported 에러는 허용되는 동작(permitted behaviour)" 으로 인정되어 해당 모듈이 SKIPPED 처리된다.

### 3. 미인증 POST 인가 요청의 파라미터 유실 (1개 모듈 실패 -> PASSED)

- OIDC 는 authorize endpoint 의 GET/POST 모두 지원을 요구하는데.. 인증된 세션의 POST 는 정상이지만
  미인증 POST 는 로그인 후 saved request 가 GET 으로 재현되며 form 파라미터가 유실되어 400 이 된다.
  (spring authorization server 의 converter 가 GET 에서 query string 파라미터만 읽기 때문)
- 수정.. `PostToGetAuthorizationRequestEntryPoint` (미인증 POST 인가 요청을 form 파라미터를 query 로 옮긴 GET redirect 로 강등)

### 부수 발견: 에러 응답이 401 로 뒤바뀌는 문제

- sendError(400 등)는 boot 의 "/error" 로 재디스패치되는데 이 경로가 authenticated 규칙에 걸리면
  미인증 사용자의 에러 응답이 원래 상태코드 대신 401 이 된다. -> "/error" permitAll (DefaultSecurityConfig)

## 경고(WARNING) 해석

- oidcc-server: id token 에 **요청하지 않은 nickname claim** — 우리 token customizer 데모 코드가 원인.
  customizer 로 claim 을 넣는 것은 자유지만 스펙 관점에서는 "요청된 scope/claims 에 대응하는 것만" 이 원칙임을 배움.
- userinfo-post-body: POST body 로 access token 전달을 미지원 (스펙상 선택 사항)
- acr-values: acr claim 미반환 (SHOULD)
- claims-essential: essential claim(name) 을 userinfo 가 반환하지 않음 (프로필 claim 소스 없음.. scope SKIPPED 와 같은 맥락)

## 주의사항

1. **suite 자동화 브라우저의 콜백 접근** — suite 컨테이너 안의 selenium 은 콜백(localhost.emobix.co.uk:8443)을
   자기 자신(127.0.0.1)으로 해석해 connection refused 가 난다.. nginx 서비스에 docker 네트워크 alias 를 부여해야 한다 (compose-alias-override.yml)
2. **issuer 주소** — suite(컨테이너)와 자동화 브라우저(역시 컨테이너 안)가 접근해야 하므로 localhost 가 아닌
   host.docker.internal 로 기동해야 한다. (my.issuer 설정을 둔 이유)
3. **PRINCIPAL_NAME_INDEX_NAME 은 세션 attribute 가 아니다** — 인덱스 계산용 이름일 뿐이라 조회하면 null..
   세션에 저장된 SecurityContext 에서 인증 이름을 꺼내야 한다. (SpringSessionSessionRegistry 참고)
