# 마이크로서비스 인가 서버 — 슬라이스 6 설계: Istio mTLS 와 서비스 간 인가

내부 호출의 인증·인가를 애플리케이션이 아니라 service mesh 가 하게 한다.

대상: `oauth-2/authorization-server/practice/microservice/`
선행: 슬라이스 1~5 — 10개 바이너리, 251개 테스트

## 목표

슬라이스 1~5는 **프로토콜**을 다뤘다. 이번은 **배포 환경**이다.

이 저장소에는 슬라이스 1부터 "내부 REST 호출 무인증 — 신뢰 네트워크 가정"이 알려진 한계로 남아 있고, 슬라이스 5의 최종 리뷰가 그 대가를 구체적으로 드러냈다 — `session:8088` 에 도달하면 `sid` 하나로 남의 세션을 끊을 수 있고, `POST /internal/sessions` 로 가짜 행을 심을 수 있다.

그 한계를 **Spring 코드를 한 줄도 바꾸지 않고** 닫는다.

주제는 하나다. **`x509()` 와 `SecurityConfig` 로 짰을 코드가 YAML 두 파일로 대체된다** — 그리고 그것이 k8s 환경에서 이 문제의 정답이다.

### 이것이 대안으로 검토했던 것들

| | 성격 | 판정 |
|---|---|---|
| Spring 에서 `x509()` + SSL bundle | 애플리케이션이 mTLS 를 직접 | mesh 가 대체하는 코드다. k8s 에서는 TLS 를 두 번 종단하게 되어 해롭다 |
| 게이트웨이를 신뢰 경계로 | perimeter 모델 | **현재 상태다.** 게이트웨이는 `/internal/**` 을 아예 라우팅하지 않으므로 동-서 호출을 보호할 수 없다 |
| RFC 8705 (인증서 결속 토큰) | 프로토콜 | 유효한 주제이나 북-남 문제다. 별도 슬라이스 후보 |

**주의.** 게이트웨이 강화와 내부 mTLS 는 대안이 아니라 다른 표면이다. 게이트웨이는 공개 OAuth 엔드포인트만 프록시하고 서비스 간 호출은 보지 못한다.

---

## 1. 무엇을 클러스터에 올리나

전체 10개가 아니라 **대표 경로만** 올린다. 나머지는 같은 패턴의 반복이라 배울 것이 없다.

| | 정체 | 외부 의존성 |
|---|---|---|
| `signing` | 피호출자. 개인키 보유 | 없음 (keystore 가 jar 안 classpath) |
| `client-registry` | 피호출자 | MySQL |
| `token` | 호출자. 실제 `SigningClient` 코드가 호출을 만든다 | 없음 (Redis 미배포 — 아래) |
| `caller-token` | ServiceAccount `token` 을 단 curl 파드 | 없음 |
| `caller-auth` | ServiceAccount `auth` 를 단 curl 파드 | 없음 |
| `no-mesh` | 사이드카를 넣지 않은 curl 파드. mTLS 강제 확인용 | 없음 |
| `mysql` | client-registry 전용 | — |

**검증 호출은 전용 curl 파드가 한다.** Istio 가 보는 principal 은 ServiceAccount 에서 나오므로 `caller-token` 과 실제 `token` 파드는 같은 신원(`sa/token`)을 갖는다. 전용 파드를 쓰면 베이스 이미지를 바꿔도 검증이 깨지지 않고 `caller-auth`(실제 auth 를 배포하지 않으므로 어차피 대역이다)와 대칭이 된다.

**실제 코드 경로는 따로 탄다.** `token` 의 `/oauth2/jwks` 는 `SigningClient.jwks()` 로 signing 을 부르는 프록시이고 Redis·MySQL 을 타지 않는다. `curl token:8082/oauth2/jwks` 로 그 경로를 태워 진짜 서비스 간 호출이 mesh 를 통과하는 것을 확인한다.

**`caller-auth` 는 진짜 auth 서비스가 아니다.** Istio 의 신원은 파드가 무엇을 실행하느냐가 아니라 **ServiceAccount 에서 나오므로**, curl 파드에 SA `auth` 를 달면 그 파드는 auth 의 SPIFFE 신원을 그대로 갖는다. auth 의 의존성(Redis·user-directory·consent·session)을 끌어오지 않고도 충실한 시연이 된다.

**Redis 를 배포하지 않는다.** token 의 Lettuce 는 지연 연결이라 Redis 없이 부팅되고, 이 트랙은 Redis 를 타는 코드 경로를 부르지 않는다.

### 범위 밖 — 이 트랙은 프로토콜을 증명하지 않는다

서비스 3개로는 OAuth 흐름이 돌지 않는다. `token` 이 `/oauth2/token` 을 처리하려면 user-directory·token-state·session 이 더 필요하다.

**이 트랙이 증명하는 것은 정책이다.** `caller-token` 에서 signing 을 부르고, `caller-auth` 에서도 같은 호출을 시도해 거부되는 것을 본다. 기존 `java -jar` e2e 가 프로토콜을 맡고 이 트랙은 "누가 누구를 부를 수 있나"만 맡는다. **기존 e2e 는 건드리지 않는다.**

---

## 2. 왜 kind 인가 — 실측 근거

Docker Desktop 에 Kubernetes(v1.34.3)가 이미 돌고 있지만 **로컬에서 빌드한 이미지를 클러스터가 쓸 수 없다.**

확인한 사실:
- 노드 런타임이 `containerd 2.2.0` 이고 `kindest/kindnetd` 가 돈다 — Docker Desktop 이 kind 기반 프로비저너로 바뀌었다
- 노드 컨테이너가 **어느 docker 컨텍스트에도 보이지 않는다**(`default`·`desktop-linux` 둘 다 빈 목록) — `docker exec` 로 이미지를 넣을 경로가 없다
- `docker build` 후 `imagePullPolicy: Never` 로 파드를 만들면 **`ErrImageNeverPull`** 이 난다

**주의.** `ErrImageNeverPull` 은 "받아오지 말라고 했는데 노드 로컬에 없다"는 뜻이다. 빌드가 실패한 것이 아니라 **노드의 containerd 스토어가 docker 데몬 스토어와 분리돼 있다**는 뜻이다. 예전 Docker Desktop 은 두 스토어를 공유했으므로 이전 경험과 다를 수 있다.

kind 를 쓰면 `kind load docker-image <img> --name <cluster>` 한 줄로 끝난다. 로컬 레지스트리를 띄우는 대안은 **노드 안의 `localhost` 가 호스트가 아니라서** 노드 containerd 의 레지스트리 설정을 손봐야 하고, 그 위치가 Docker Desktop 버전마다 다르다.

부수 이점: 기존 Docker Desktop 클러스터에 Istio CRD 를 설치하지 않는다. 실패해도 `kind delete cluster` 로 통째로 지운다.

**설치가 필요한 것: `kind`, `istioctl`.** 둘 다 없다.

**주의.** kind 노드 이미지 버전을 **Istio 지원 매트릭스에서 확인해 고정**한다. 최신 k8s 가 설치할 Istio 의 지원 범위 밖일 수 있다. 버전을 추측해 박아두지 않는다.

### 실제로 고른 버전

| 항목 | 값 |
|---|---|
| Homebrew `kind` | 0.32.0 |
| Homebrew `istioctl` / Istio | 1.30.3 |
| kind 노드 이미지 | `kindest/node:v1.34.8@sha256:02722c2dedddcfc00febf5d27fbeb9b7b2c14294c82109ff4a85d89ac9ba3256` |

**근거.**

- `istioctl version --remote=false` 로 확인한 Istio 버전은 `1.30.3` 이고, Istio 공식 지원 릴리즈 표(1.30 행)에 따르면 이 버전이 공식 지원하는 k8s 버전은 **1.32, 1.33, 1.34, 1.35, 1.36** 이다(1.27~1.31 은 "tested, but not supported"). Docker Desktop 의 k8s(v1.34.3)를 그대로 쓰지 않고 독립적으로 이 표를 확인한다.
- kind `v0.32.0` 릴리즈가 사전 빌드해 제공하는 노드 이미지는 `v1.36.1`, `v1.35.5`, `v1.34.8`, `v1.33.12` 네 가지이며, 넷 다 Istio 1.30.3 의 공식 지원 범위(1.32~1.36) 안에 든다.
- 이 중 `v1.34.8` 을 선택한다. `v1.36.1`(kind 0.32.0 의 기본값)은 kubeadm 설정 포맷이 v1beta3 에서 v1beta4 로 바뀌는 첫 릴리즈(k8s 1.36.0+)라서, 처음 부트스트랩하는 클러스터에서 불필요한 리스크를 지지 않는다. `v1.34.8` 은 기존 v1beta3 포맷을 그대로 쓰는 마지막 라인 중 하나이면서 Istio 1.30.3 지원 범위의 중간에 위치해 안정적이다.
- 반드시 `@sha256` 다이제스트까지 함께 지정한다 — kind 릴리즈 노트가 재현성을 위해 태그만이 아니라 다이제스트 고정을 명시적으로 권장한다. 다이제스트까지 고정하는 것은 노드 이미지뿐이다 — 워크로드 이미지(`curlimages/curl`, `mysql`)는 이 트랙이 부르는 API 표면이 좁아 버전 변화의 영향이 작으므로 태그만 쓴다(자세한 근거는 `k8s/README.md` 참고).

---

## 3. 신원과 정책

신원은 **ServiceAccount** 에서 나온다. 서비스마다 SA 를 하나 두면 Istio 가 `cluster.local/ns/<namespace>/sa/<name>` 형태의 principal 을 부여하고, `AuthorizationPolicy` 의 `source.principals` 가 그 값을 본다. 인증서 발급·회전·검증은 전부 mesh 가 한다.

### 핵심 데모 — signing 하나로 다 나온다

signing 에는 엔드포인트가 둘 있다. `/internal/sign`(서명)과 `/oauth2/jwks`(공개키). **auth 는 jwks 는 읽지만 서명은 하지 않는다.**

**실제 소스 코드 기준 호출자 집합은 이 트랙에 배포한 것보다 넓다.** `POST /internal/sign` 은 `token` 뿐 아니라 `session` 도 부른다(`session/src/main/java/dev/starryeye/session/client/SigningClient.java`, `LogoutTokenDelivery.java` — 슬라이스 5 의 back-channel logout 에서 logout token 을 서명하는 실제 경로다, 죽은 코드가 아니다). `GET /internal/clients/*`(client-registry)도 `token`·`auth`·`session` 셋이 부른다. `session` 서비스 자체를 이 트랙에 배포하지 않으므로(위 "무엇을 클러스터에 올리나" 참고) `AuthorizationPolicy` 의 `principals` 에는 `sa/session` 을 넣지 않는다 — 이것은 "session 이 그 엔드포인트를 안 부른다"는 사실 진술이 아니라 이 트랙의 배포 범위를 반영한 것이다.

**주의.** 이 슬라이스를 확장해 `session` 을 클러스터에 올린다면 `authz-signing.yaml` 의 `/internal/sign` rule 과 `authz-client-registry.yaml` 의 rule 양쪽 `principals` 에 `cluster.local/ns/microservice-as/sa/session` 을 반드시 추가해야 한다. 추가하지 않으면 두 정책은 여전히 `token`/`auth` 만 허용하는 화이트리스트이므로, 실제 배포 코드(`session` 의 back-channel logout 경로)가 403 으로 조용히 막힌다.

| | `GET /oauth2/jwks` | `POST /internal/sign` |
|---|:--:|:--:|
| `token` 신원 | 허용 | 허용 |
| `auth` 신원 | **허용** | **거부** |
| 신원 없음 | 거부 | 거부 |

**같은 서비스, 같은 호출자, 엔드포인트에 따라 갈린다.** "내부 서비스끼리는 서로 믿는다"와 정확히 반대되는 그림이고, 슬라이스 1이 signing 을 따로 뗀 이유를 실물로 증명한다 — 브라우저를 마주보는 서비스가 공개키는 읽되 개인키로 서명은 못 한다.

```yaml
spec:
  selector: { matchLabels: { app: signing } }
  action: ALLOW
  rules:
    - from: [{ source: { principals: ["cluster.local/ns/microservice-as/sa/token"] } }]
      to:   [{ operation: { methods: ["POST"], paths: ["/internal/sign"] } }]
    - from: [{ source: { principals: ["cluster.local/ns/microservice-as/sa/token",
                                      "cluster.local/ns/microservice-as/sa/auth"] } }]
      to:   [{ operation: { methods: ["GET"], paths: ["/oauth2/jwks"] } }]
```

client-registry 는 `{token, auth}` 가 `GET /internal/clients/*` 만 허용이다.

`PeerAuthentication` 은 네임스페이스 전체에 `STRICT` 하나면 된다.

**주의.** Istio API 그룹 버전(`security.istio.io/v1` 대 `v1beta1`)은 설치한 Istio 버전에 맞춰 확인한다. 문서의 예시를 그대로 믿지 않는다.

---

## 4. 매니페스트 구성

```
microservice/k8s/
  README.md                      이 트랙 사용법. 기존 java -jar 트랙과 별개임을 명시
  Dockerfile                     서비스 3개 공용. ARG 로 jar 경로만 받는다
  base/    namespace.yaml · mysql.yaml · signing.yaml · client-registry.yaml
           token.yaml · callers.yaml
  istio/   peer-authentication.yaml · authz-signing.yaml · authz-client-registry.yaml
  verify.sh                      성공 기준을 순서대로 실행하고 raw 출력을 남긴다
```

서비스 매니페스트 하나에 **ServiceAccount + Deployment + Service + ConfigMap** 을 담는다. 파일을 열면 그 서비스의 전부가 보이게 한다.

**Dockerfile 은 하나다.** 세 서비스가 전부 Spring Boot fat jar 이라 모양이 같다.

```dockerfile
FROM eclipse-temurin:21-jre
ARG JAR
COPY ${JAR} /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

**설정 이관**은 ConfigMap 을 `/config/` 에 마운트하고 `SPRING_CONFIG_ADDITIONAL_LOCATION` 으로 가리킨다. 포트와 키 이름은 기존 `application.yml` 그대로 두고 호스트만 `localhost` → k8s 서비스명으로 바꾼다.

**주의.** `my.signing-base-url` 을 환경변수로 넘기면 relaxed binding 이 하이픈을 지워 `MY_SIGNINGBASEURL` 이 된다. 읽는 사람이 원래 키를 복원할 수 없으므로 쓰지 않는다.

**MySQL 은 Deployment + `emptyDir`** 다. 데이터가 살아남을 이유가 없다 — `ddl-auto: update` 와 seed 가 재생성한다. **NodePort 로 노출하지 않는다**(호스트의 3306 이 기존 docker-compose 와 겹친다).

---

## 5. 검증

**이 슬라이스에는 단위 테스트가 없다.** YAML 을 단위 테스트할 방법이 없고, 증명 대상이 "클러스터에서 실제로 거부되는가"라 클러스터 안에서의 실행이 유일한 수단이다. `verify.sh` 가 아래를 순서대로 돌리고 **명령과 raw 출력**을 남긴다.

| # | 확인 | 기대 |
|---|---|---|
| 1 | 파드 상태 | 세 서비스 모두 `READY 2/2` (앱 + 사이드카) |
| 2 | `caller-token` → signing `/oauth2/jwks` | 200 |
| 3 | `caller-token` → signing `/internal/sign` | 200 |
| 4 | `caller-auth` → signing `/oauth2/jwks` | 200 |
| **5** | **`caller-auth` → signing `/internal/sign`** | **403 + 본문 `RBAC: access denied`** |
| 6 | `caller-token` → client-registry `/internal/clients/my-client` | 200 |
| 7 | `caller-auth` → client-registry `/internal/clients/my-client` | 200 |
| 8 | `no-mesh`(사이드카 없음) → signing | 연결 거부 |
| 8b | `caller-token` → token `/oauth2/jwks` (실제 프록시 경로) | 200 |
| 9 | `git diff` | **Spring 소스 변경 0줄** |

**5번이 핵심 기준이고 9번이 주제다.**

**주의.** 403 이 Istio 에서 온 것인지 애플리케이션에서 온 것인지 구분하려면 **본문을 봐야 한다.** signing 의 `/internal/sign` 은 애플리케이션 차원에서 403 을 낼 일이 없고, Istio 는 정확히 `RBAC: access denied` 를 돌려준다. 상태 코드만 보고 통과로 적지 않는다.

### 뮤테이션 — 이 슬라이스판

코드가 없으니 뮤테이션도 YAML 에 건다. `authz-signing.yaml` 의 `/internal/sign` 규칙 `principals` 에 `auth` 를 추가해 **5번이 403 → 200 으로 바뀌는 것을 확인**한 뒤 되돌린다.

**주의.** 이 확인이 없으면 "정책이 막았다"와 "원래부터 안 되던 것"을 구분할 수 없다.

---

## 6. 미리 막아둘 함정

**Istio 의 ALLOW 정책은 deny-by-default 다.** 어떤 ALLOW 정책이 워크로드를 선택하는 순간 규칙에 맞지 않는 나머지는 전부 거부된다. signing 에 `/internal/sign` 규칙만 쓰면 `/oauth2/jwks` 가 조용히 막힌다.

**MySQL 은 server-first 프로토콜이다.** 서버가 먼저 greeting 을 보내므로 Istio 의 프로토콜 자동 감지가 오작동할 수 있다. Service 포트 이름을 `tcp-mysql` 로 명시해 TCP 로 못박는다.

**health probe 는 지금 문제가 되지 않는다.** 이 서비스들에 actuator 가 없어 probe 를 설정하지 않는다. 나중에 넣는다면 kubelet 은 mesh 밖에서 오므로 Istio 의 probe rewrite 가 필요하다.

**native sidecar — Istio 1.30.3 은 `istio-proxy` 를 `spec.containers` 가 아니라 `spec.initContainers` 에 `restartPolicy: Always` 로 넣는다**(k8s 1.28+ 의 native sidecar 기능). `kubectl get pod -o jsonpath='{.spec.containers[*].name}'` 로만 확인하면 애플리케이션 컨테이너 이름만 보이고 `istio-proxy` 는 안 보인다 — 사이드카가 없는 것으로 오인하기 쉽다. `kubectl get pods` 의 `READY` 열은 `restartPolicy: Always` 인 init 컨테이너도 집계에 포함하므로, 정상 주입된 애플리케이션 파드는 여전히 `2/2` 로 보인다 — `READY` 만 보면 "떠 있다"는 사실은 정확히 알 수 있지만, 사이드카가 어느 스펙 필드에 들었는지는 `initContainers` 를 따로 확인해야 한다.

**xDS 전파 지연 — 정책 apply 직후 단발 검사는 불안정하다.** `AuthorizationPolicy`/`PeerAuthentication` 을 `kubectl apply` 한 직후 istiod 가 그 변경을 각 사이드카(Envoy)에 xDS 로 전파하는 데 시간이 걸린다. 이 kind 클러스터에서는 그 창이 실측 10초를 넘긴 적이 있다 — `sleep 10` 으로 부족한 경우가 있었다는 뜻이다. 그 창 안에서 검사하면 정책 자체는 멀쩡한데도 거부돼야 할 호출이 잠깐 200 을 내 거짓 FAIL 이 난다. `verify.sh` 는 실제 검사를 시작하기 전에 핵심 거부 케이스(`caller-auth` → `signing /internal/sign`)가 403 으로 수렴할 때까지 최대 60초(2초 간격) 대기하는 단계를 따로 둔다 — 고정된 `sleep` 한 번으로는 전파 완료를 보장할 수 없고, 폴링으로 실제 수렴을 확인해야 전파 지연과 정책 결함을 구분할 수 있다.

### 진단표

| 증상 | 원인 | 확인 |
|---|---|---|
| `READY 1/1` | 네임스페이스에 `istio-injection=enabled` 라벨 누락 | `kubectl get ns --show-labels` |
| `ErrImagePull` / `ImagePullBackOff` | 노드에 이미지 미적재 — 모든 Deployment 가 `imagePullPolicy: IfNotPresent` 라 노드에 이미지가 없으면 이 증상이 난다(`ErrImageNeverPull` 은 `imagePullPolicy: Never` 일 때만 나는 별개 증상이다 — 위 §2 의 Docker Desktop 실험이 그 경우다) | `kind load docker-image` 로 노드에 적재됐는지 `docker exec <노드> crictl images` 로 확인 |
| client-registry `CrashLoopBackOff` | MySQL 미기동 또는 포트 이름 미지정 | 로그 + Service 포트 이름 |
| 전부 403 | ALLOW 정책이 deny-by-default 를 켰는데 규칙이 좁음 | `istioctl x authz check <pod>` |
| 정책 미적용 | `selector.matchLabels` 오타 | 같은 명령 |
| 원인 불명 403 | — | `kubectl logs <pod> -c istio-proxy` 에서 `rbac_access_denied` |

---

## 7. 알려진 한계로 기록할 것

- **정책이 클러스터에만 산다.** `java -jar` 로 띄우는 기존 트랙은 여전히 무인증이다. 두 트랙이 같은 코드를 쓰지만 보호 수준이 다르다.
- **10개 중 3개만 올렸다.** user-directory·consent·token-state·session·auth·demo-rp 는 매니페스트가 없다. 확장은 같은 패턴의 반복이다.
- **OAuth 흐름이 클러스터에서 돌지 않는다.** 정책만 증명하고 프로토콜은 기존 e2e 가 맡는다.
- **인증서 회전을 다루지 않는다.** mesh 가 자동으로 하지만 이 슬라이스는 그 동작을 검증하지 않는다.
- **`caller-auth` 는 진짜 auth 가 아니다.** SA 만 빌린 curl 파드이므로 auth 의 실제 호출 패턴(consent·user-directory 등)은 재현되지 않는다.

## 8. 이번 슬라이스에서 제외

- **Spring 쪽 `x509()`·SSL bundle** — mesh 가 대체하는 코드다. 굳이 짜면 TLS 이중 종단이 된다
- **RFC 8705 인증서 결속 토큰** — 북-남 문제이고 프로토콜 작업이다. 별도 슬라이스 후보
- **Istio Gateway 로 nginx 대체** — 기존 게이트웨이는 그대로 둔다. 이 트랙은 동-서만 다룬다
- **나머지 7개 서비스 이관** — 같은 패턴의 반복
- **NetworkPolicy** — L3/L4 는 mesh 의 L7 인가와 별개 주제다
