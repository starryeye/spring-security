# 인프라 프로젝트 백로그 — 이 저장소에서 다루지 않기로 한 것들

이 프로젝트(`microservice/`)는 **인가 서버를 코드로 어떻게 만드는가**에 집중한다. 아래 항목들은 그
경계 바깥이라 별도 인프라 프로젝트로 이관했다. 이 코드베이스를 **그대로 얹어서** 인프라만 파는 것이
목표다 — 애플리케이션을 다시 만들지 않는다.

## 경계 기준

| | 무엇 | 왜 |
|---|---|---|
| **바깥 (이 백로그)** | mTLS, 서비스 메시, ingress, 네트워크 인가 정책, 배포·오케스트레이션, 인증서 수명주기 | 플랫폼(k8s/EKS/Istio)이 더 깔끔하게 푼다. 앱 코드에서 손으로 재현하면 배움이 얇고, 실무에서 그렇게 하지도 않는다 |
| **안쪽 (이 프로젝트)** | MySQL, Redis, Kafka 같이 **앱이 직접 코드로 통합하는 미들웨어** | 붙이는 방식에 따라 정합성·순서·중복·트랜잭션 경계가 실제로 갈린다. 플랫폼이 대신 해주지 않는다 |

판별 질문: **"이걸 EKS 로 옮기면 매니페스트 몇 줄로 사라지는가?"** 사라지면 바깥이다.

## 이관 경위

슬라이스 6(Istio mTLS, 2026-08-08 완결)에서 `k8s/` 트랙을 만들어 kind + Istio 위에 세 서비스
(`signing`·`token`·`client-registry`)를 올리고 `PeerAuthentication` STRICT + `AuthorizationPolicy`
로 서비스 간 인증·인가를 걸었다. 동작은 검증됐다 — `caller-auth` 신원이 `POST /internal/sign` 에서
`403 RBAC: access denied` 를 받고 같은 파드가 `/oauth2/jwks` 는 200 을 받는 것까지.

그러나 슬라이스를 마치고 보니 **7개 태스크 중 Spring 코드를 건드린 것이 0개**였고, 리뷰가 잡은 결함도
전부 매니페스트·스크립트·문서였다. 배운 것도 대부분 "Istio/kind 를 어떻게 다루나"지 "인가 서버를
어떻게 만드나"가 아니었다. 그래서 이 프로젝트에서는 `k8s/` 를 걷어내고 여기로 이관했다.

**남긴 것**: 슬라이스 6의 설계·구현 계획 문서는 `docs/superpowers/` 에 그대로 있다. 거기 적힌 함정들이
인프라 프로젝트의 출발점이다.

**삭제한 것**: `microservice/k8s/`(12파일 922줄 — Dockerfile, kind/Istio 매니페스트,
`AuthorizationPolicy`, `verify.sh`, README). git 이력에는 커밋 `6135722` 시점까지 남아 있다.

## 백로그

### A. 슬라이스 6에서 이미 만들었던 것 (복원 대상)

1. kind 클러스터 + Istio 설치, 로컬 이미지 적재(`kind load docker-image`)
2. `PeerAuthentication` STRICT — 사이드카 없는 파드가 닿지 못함을 실증
3. `AuthorizationPolicy` — ALLOW 규칙이 deny-by-default 로 동작, SPIFFE 신원(ServiceAccount)별로
   같은 서비스의 엔드포인트가 갈리는 것을 실증
4. `verify.sh` 검증 스크립트

### B. 슬라이스 6이 못 한 것 (진짜 백로그)

1. **10개 서비스 전체 배포** — 슬라이스 6은 3개만 올렸다. 나머지 7개(gateway·auth·user-directory·
   consent·token-state·session·demo-rp) + Redis.
2. **`session:8088` 무인증 구멍 닫기** — 슬라이스 6이 *동기로 내세운* 구멍인데 `session` 을 배포하지
   않아 닫히지 않았다. `sid` 를 아는 누구든 네트워크로 도달하면 남의 세션을 강제 로그아웃시킬 수 있다
   (README "알려진 한계" 참고). **이 백로그의 1순위.**
3. **ingress 로 front-channel 진입** — `PeerAuthentication` STRICT 는 평문 인바운드를 거부하므로
   브라우저도 "바깥 신원"이다. 메시 안의 진입점이 필요하다. 슬라이스 6은 `minimal` 프로파일이라
   ingress gateway 자체가 없다.
   - 딸린 문제: `my.issuer` 가 `http://localhost:9000` 으로 **설정에 고정**돼 있고 앱은
     `X-Forwarded-*` 를 **읽지 않는다**. kind `extraPortMappings` 로 호스트 9000 을 매핑하면 설정을
     안 고쳐도 되지만, 그 등가성을 실제로 확인해야 한다.
   - 라우팅을 nginx 로 유지할지 `Gateway`/`VirtualService`(또는 k8s Gateway API)로 옮길지 결정.
     현재 `gateway/nginx.conf` 는 prefix 11개를 두 upstream 으로 보내는 게 전부라 이관 비용은 낮다.
     상용 관행은 L7 을 겹치지 않는 쪽(Istio 로 일원화)이다.
4. **메시 안에서 OAuth flow e2e 완주** — 슬라이스 6은 정책이 신원별로 걸러내는지만 증명했다. 실제
   authorization code 발급·토큰 교환은 이 트랙에서 돌지 않았다. **"정책이 진짜 트래픽을 안 깨뜨린다"**
   를 증명해야 비로소 의미가 있다.
5. **워크로드 인증서 자동 회전 검증** — 회전이 실제로 일어나는지, 회전 중 연결이 끊기지 않는지.
   `verify.sh` 는 스냅샷만 봤다.

### C. 슬라이스 6 잔여 결함 (복원할 때 같이)

1. kind 노드 이미지가 `not-provided` 인 건에 대한 주석 (M1)
2. `verify.sh` `[5]` 가 본문과 상태 코드를 따로 받느라 curl 을 두 번 호출 (M4)
3. **`k8s/README.md` Step 6~10 과 `verify.sh` 의 `[1]`·`[9]` 는 통짜로 실행된 적이 없다** — 클러스터를
   지운 뒤에 정리·보강한 것이라 정적 검증(`bash -n`, `kubectl apply --dry-run=client`, `[9]` 는 변이
   증명)만 통과했다. 복원하면 반드시 한 번 실행해 확인할 것.

## 슬라이스 6에서 실제로 겪은 함정 (인프라 프로젝트 출발점)

설계 문서에 상세히 있고, 요약하면 이렇다.

- **native sidecar** — Istio 1.30.3 은 `istio-proxy` 를 `spec.containers` 가 아니라
  `spec.initContainers` 에 `restartPolicy: Always` 로 넣는다(k8s 1.28+). `containerStatuses` 만 읽는
  검사는 사이드카를 **아예 관측하지 못한다**. 파드 `Ready` 조건은 restartable init 까지 집계하므로
  그쪽을 봐야 하고, 주입 실패(`1/1`)는 `Ready` 가 `True` 라 `initContainers` 를 따로 확인해야 잡힌다.
- **xDS 전파는 즉시가 아니다** — apply 직후 거부돼야 할 호출이 잠깐 200 을 낸다(kind 에서 10초 초과
  관측). `sleep N` 이 아니라 "핵심 거부가 403 으로 수렴할 때까지 폴링, 제한시간 초과 시 경고 후 그대로
  검사"로 — 전파 지연과 정책 결함을 구분하되 결함을 대기로 덮지 않는다.
- **MySQL 은 server-first 프로토콜** — 포트 이름을 `tcp-mysql` 로 명시해야 한다. 자동 프로토콜 감지가
  HTTP 로 오판한다.
- **Docker Desktop 내장 k8s 는 kind 기반이지만 containerd 스토어가 별개** — 로컬 빌드 이미지가
  `imagePullPolicy: Never` 면 `ErrImageNeverPull`, `IfNotPresent` 면 `ErrImagePull`/`ImagePullBackOff`
  로 **다르게** 보인다.
- **zsh 파라미터 modifier** — `$s:latest` 가 `signingocal:latest` 같은 이미지 이름을 만든다.
  `${s}:latest` 로 써야 한다.
- **정책 주석이 사실과 어긋나기 쉽다** — 최종 리뷰가 Critical 로 잡은 것: 주석·문서가
  "`/internal/sign` 은 token 만 부른다"고 단언했는데 `session` 도 부른다(슬라이스 5의 back-channel
  logout 경로). 배포 범위에서 빠진 것과 호출자가 없는 것은 다르다.

## 관련 문서

- [슬라이스 6 설계](superpowers/specs/2026-08-07-microservice-istio-mtls-slice6-design.md)
- [슬라이스 6 구현 계획](superpowers/plans/2026-08-07-microservice-istio-mtls-slice6.md)
