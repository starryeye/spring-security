# microservice authorization server — 첫 관통 슬라이스 설계

- 위치: `oauth-2/authorization-server/practice/microservice/`
- 작성: 2026-07-18
- 상태: 설계 승인됨 (brainstorming 완료, 구현 계획 대기)

## 배경과 목표

Spring Authorization Server(SAS) 로 학습해온 authorization server 를, **SAS starter 없이 직접 구현**하면서
**여러 개의 독립 마이크로서비스(별도 프로젝트·바이너리)로 분해**한다. 빅테크가 실제로 하는 방식
(발급/서명/디렉토리/클라이언트 레지스트리 분리, 키 격리)의 축소판을 만든다.

전체는 하나의 스펙으로 담기엔 크므로 sub-project 로 나누고, 이 문서는 **첫 sub-project = authorization code + PKCE 관통 슬라이스**만 다룬다.

### 전체 로드맵 (참고, 이 스펙 범위 아님)
1. **code+PKCE 관통 슬라이스 (이 문서)** — 6서비스 골격 + 동기 flow
2. OIDC 확장 (id token / userinfo / consent 분리)
3. 인증 이벤트 스트림 (Kafka) — 감사·리스크·캐시 무효화
4. refresh / introspection, HA/상태 외부화 심화

### 결정된 범위 (brainstorming 합의)
- OIDC 까지 전부 직접 구현이 최종 목표이나, 첫 슬라이스는 code+PKCE 만
- 6+ 서비스로 확장 분해 (gateway/auth/token/signing/user-directory/client-registry)
- 내부 서비스 간 통신: REST + **인증 없음** (신뢰 네트워크 가정, **추후 개선 항목**)
- 운영급 수준: 상태 외부화 + HA (기존 production-ready 수준)
- 직접 구현 경계: **SAS starter 만 제외**. form login·세션·Spring Security 필터체인·Nimbus JOSE 는 활용
- Message queue(Kafka): 정말 어울리는 곳(#3 인증 이벤트 스트림)에만. 첫 슬라이스는 순수 동기

## 서비스 분해와 책임 경계

| 서비스 | 포트 | 책임 | 소유 데이터 |
|---|---|---|---|
| gateway | 9000 | 외부 진입점(nginx). 경로 라우팅만 | 없음 (설정만, 코드 없음) |
| auth | 8081 | front-channel: `/oauth2/authorize`, 로그인 폼, code 발급 | 진행 중 인가(code, PKCE challenge) — Redis |
| token | 8082 | back-channel: `/oauth2/token`, `/oauth2/jwks`(프록시), `/.well-known` | 없음 (첫 슬라이스는 토큰 상태 미저장) |
| signing | 8083 | JWT 서명 전담 + jwks 공개. **개인키를 이 서비스만 보유** | 서명 키(keystore 파일) |
| user-directory | 8084 | 사용자 조회 + credential 검증 API | 사용자(username, bcrypt password) — MySQL |
| client-registry | 8085 | client 조회 API + Caffeine 캐시 | client 메타데이터 — MySQL |

### 설계 원칙
- **데이터 소유권 분리**: auth 는 사용자/client DB 를 직접 보지 않고 user-directory·client-registry 를 REST 호출한다. (MSA 핵심)
- **키 격리**: signing 만 개인키를 가진다. token 은 "이 payload 서명해줘" 요청만 한다. token 이 털려도 개인키는 노출되지 않는다.
- **code 의 flow 횡단**: auth 가 만든 code 를 token 이 검증해야 하는데 별개 프로세스이므로 **공유 저장소(Redis)** 로 주고받는다.
  (production-ready 에서 본 seam 의 물리 분리 버전)

## 관통 데이터 흐름 (authorization code + PKCE, S256 필수)

```
[1] 브라우저 → gateway → auth: GET /oauth2/authorize?client_id&redirect_uri&scope&code_challenge&code_challenge_method=S256&state
        auth → client-registry: client_id 조회, redirect_uri·scope 검증
        (미인증) auth 가 로그인 폼 응답
[2] 브라우저 → gateway → auth: POST /login (username, password)
        auth → user-directory: POST /internal/users/authenticate (password 비교는 user-directory 가 수행)
        성공 → auth 세션 확립 (Spring Session + Redis)
[3] auth: code 생성, Redis 에 저장 {code → client_id, redirect_uri, scope, sub, code_challenge}, TTL 60초
        → 302 redirect_uri?code&state
[4] 브라우저 → client 콜백 (첫 슬라이스는 실제 client 앱 없이 curl/.http 로 이후 수동 수행)
[5] client 대역 → gateway → token: POST /oauth2/token (grant_type=authorization_code, code, code_verifier, client 인증)
        token → client-registry: client 인증(secret 검증)
        token → Redis: code 조회 + 1회용 소비, code_challenge vs code_verifier(S256) 대조
        token → (필요 시) user-directory: 프로필 claim 조회
        token: iss/sub/aud/exp/scope 등 표준 claim 구성
        token → signing: POST /internal/sign {claims, header} → {jwt}
        token → 브라우저: {access_token(JWT), token_type, expires_in}
[6] 검증: resource 대역 또는 직접 → signing/jwks(또는 token/jwks 프록시)로 서명 검증
```

### 설계 결정
- **PKCE 필수(S256만)**: public/confidential 구분 복잡도를 첫 슬라이스에서 제외. challenge 저장은 auth(Redis), verifier 대조는 token.
- **signing 인터페이스**: token 이 표준 claim(iss/exp 등)을 채워서 넘긴다. signing 은 "서명 기계" 로 정책 판단을 하지 않으며 kid 선택만 재량.
  jwks 의 진짜 소유자는 signing 이고, token 의 `/oauth2/jwks` 는 프록시(또는 discovery 가 signing jwks 를 가리킴).
- **client/resource 대역**: 첫 슬라이스는 실제 client 앱을 만들지 않고 curl/.http 로 수동 수행. resource server 대역은 jwks 검증만 확인.

## 상태 저장소

| 데이터 | 저장소 | 소유 서비스 | 이유 |
|---|---|---|---|
| authorization code + PKCE challenge | Redis (TTL 60초) | auth 가 쓰고 token 이 읽고 소비 | 두 서비스 횡단 + 짧은 수명·1회용 |
| 로그인 세션 | Redis (Spring Session) | auth | HA — auth 다중 인스턴스 세션 공유 |
| 사용자 | MySQL | user-directory | 내구성 |
| client | MySQL | client-registry | 내구성 |
| 서명 키 | keystore 파일(classpath) | signing | 고정 키, 전 인스턴스 동일 |

- token 의 "발급된 토큰 상태" 는 첫 슬라이스에서 저장하지 않는다. JWT 가 상태를 담고 검증은 jwks 로 한다.
  (refresh/introspection 슬라이스에서 Redis 도입)

## 서비스 간 REST 계약 (내부 인증 없음 — 추후 개선)

```
client-registry
  GET  /internal/clients/{clientId}     → {clientId, redirectUris, scopes, clientSecretHash, grantTypes}
user-directory
  POST /internal/users/authenticate     {username, password} → {sub, authorities} | 401
  GET  /internal/users/{sub}            → 프로필 claim (token 이 필요 시)
signing
  POST /internal/sign                   {claims, header} → {jwt}
  GET  /oauth2/jwks                     → 공개키 JWKS (외부 공개용, token/discovery 가 참조)
```

### 계약 설계 포인트
- **password 는 user-directory 밖으로 안 나간다**: auth 는 평문을 넘기고 bcrypt 비교는 user-directory 가. (credential 소유 서비스에 검증을 가둠)
- **`/internal/*` vs 공개 경로 구분**: internal 은 gateway 가 외부로 라우팅하지 않는다. jwks 만 공개. 내부 인증이 없는 지금은 이 경로 격리가 유일한 방어선.
- **표준 claim 은 token 이 채운다**: signing 은 정책 없는 서명 기계. iss/exp/aud 등은 token 책임, signing 은 서명 + kid 선택만.

## 에러 처리

### OAuth 표준 에러 (직접 구현)
| 상황 | 응답 | 처리 서비스 |
|---|---|---|
| 미등록 client_id / redirect_uri 불일치 | 에러 페이지 (redirect 안 함 — open redirect 방지) | auth |
| 잘못된 scope, code_challenge 누락 | error=invalid_request → redirect_uri | auth |
| 로그인 실패 | 로그인 폼 재표시 (401 아님) | auth |
| code 만료/재사용/없음 | error=invalid_grant (400 JSON) | token |
| PKCE verifier 불일치 | error=invalid_grant | token |
| client secret 불일치 | error=invalid_client (401) | token |

### 서비스 간 실패 모드 (분해의 대가 — 핵심 학습)
- **user-directory 다운** → auth 로그인 불가, "일시적 오류" 표시. code flow 진입 전이라 안전.
- **client-registry 다운** → authorize/token 막힘. 단, client 메타는 자주 안 변하므로 **Caffeine 짧은 TTL 캐시**로 완화. (분산 캐시 필요성이 드러나는 지점)
- **signing 다운** → 신규 발급 불가. 그러나 **이미 발급된 JWT 검증은 jwks 캐시로 지속** — signing 이 SPOF 가 아니게 되는 graceful degradation. (키 격리 + JWT 자가검증의 운영 가치)
- **Redis 다운** → code/세션 정지 = flow 전체 정지. 첫 슬라이스의 단일 실패점, 감수(운영에선 Redis 자체를 HA 로).

### 실패 처리 결정
- 내부 REST 호출에 짧은 connect/read 타임아웃(2초) + **재시도 없음**(첫 슬라이스). 관측성/서킷브레이커는 이후 슬라이스.
- client-registry 캐시는 첫 슬라이스에 **포함** (Caffeine 로컬 캐시).

## 검증 (테스트)

- 각 서비스: `@SpringBootTest` contextLoads 스모크 + 핵심 로직 단위 테스트(PKCE S256 대조, code 1회용 소비, JWT claims 구성)
- **관통 e2e**: docker-compose 로 6서비스 + Redis + MySQL 전부 기동, curl/.http 로 code+PKCE flow 완주

### 성공 기준
1. 브라우저 → gateway 로그인 → code → token → JWT 발급 완주
2. 발급 JWT 가 signing/jwks 로 서명 검증 통과
3. signing 을 죽여도 기존 JWT 검증 지속 (graceful degradation)
4. code 재사용 시 invalid_grant
5. PKCE verifier 조작 시 거부

## 프로젝트 구조

```
oauth-2/authorization-server/practice/microservice/
├── README.md                 # 전체 구도·기동 순서·flow 다이어그램
├── docker-compose/           # nginx(gateway) + mysql + redis + compose
├── gateway/                  # nginx.conf (경로 라우팅) — 코드 없음, 설정만
├── auth/                     # 8081  authorize, 로그인, code 발급
├── token/                    # 8082  token, jwks 프록시, discovery
├── signing/                  # 8083  sign API, jwks 소유
├── user-directory/           # 8084  사용자·credential API
└── client-registry/          # 8085  client API + Caffeine 캐시
```

- 각 서비스는 독립 Gradle 프로젝트·바이너리. 패키지 `dev.starryeye.<service_name>`
- Java 21, Spring Boot 3.4.5, **SAS starter 제외**, Nimbus JOSE 는 signing 에만
- seed: user(user/1111), client 1개(code+PKCE 용). 첫 슬라이스는 admin 등록 API 없이 seed

## 첫 슬라이스에서 명시적으로 제외 (이후 sub-project)

consent 화면, id token/OIDC/userinfo, refresh, introspection, admin 등록 API,
Kafka 이벤트, 내부 서비스 인증, 관측성/서킷브레이커.

## 추후 개선 항목 (기록)

- **내부 서비스 간 인증** — 첫 슬라이스는 인증 없음(신뢰 네트워크 가정). API 키/mTLS 등은 별도로 개선.
  signing 인증의 닭-달걀(자기 토큰으로 인증 불가) 때문에 정적 자격 or mTLS 가 필요.
