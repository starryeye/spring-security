# 마이크로서비스 인가 서버 슬라이스 6 — Istio mTLS Implementation Plan

> **이 계획은 완료됐고, 그 산출물(`k8s/`)은 별도 인프라 프로젝트로 이관됐다 (2026-08-08).**
> 7개 태스크 전부 실행·리뷰까지 마쳤다. 학습 기록으로 남긴다. 경위와 백로그:
> [docs/infra-project-backlog.md](../../infra-project-backlog.md)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 내부 서비스 간 호출의 인증·인가를 애플리케이션이 아니라 Istio 가 하게 하고, Spring 코드를 한 줄도 바꾸지 않고 그것을 증명한다.

**Architecture:** kind 로 만든 로컬 클러스터에 signing·client-registry·token 세 서비스를 올리고, Istio 의 `PeerAuthentication`(mTLS 강제)과 `AuthorizationPolicy`(호출자별·엔드포인트별 인가)를 건다. 신원은 ServiceAccount 에서 나온다. 검증은 서로 다른 ServiceAccount 를 단 curl 파드로 한다.

**Tech Stack:** kind, Istio, kubectl, Docker, 기존 Spring Boot 3.4.5 서비스(무변경)

## Global Constraints

- **Spring 소스를 수정하지 않는다.** 이 슬라이스의 주제가 "애플리케이션 코드 없이 닫는다"이므로, `*/src/main/java` 와 `*/src/main/resources/application.yml` 은 **읽기 전용**이다. 설정 차이는 전부 ConfigMap 으로 덮는다.
- 새 파일은 전부 `oauth-2/authorization-server/practice/microservice/k8s/` 아래에 만든다.
- 네임스페이스는 `microservice-as`.
- 이미지 태그는 `signing:local` · `client-registry:local` · `token:local`. `imagePullPolicy: IfNotPresent`.
- 포트는 기존 그대로: signing 8083, token 8082, client-registry 8085, mysql 3306.
- **버전을 추측해 박지 않는다.** kind 노드 이미지와 Istio 버전은 설치 후 지원 매트릭스를 확인해 정하고, 그 값을 보고서에 기록한다.
- **`git add` 는 경로를 명시한다. `-A`/`-a`/`.` 절대 금지** — 저장소에 상시 modified 로 두는 credential 파일이 있어 실제 비밀이 올라간다.
- 커밋 직후 `git push origin main`. 거부되면 강제 푸시하지 말고 보고한다.
- 커밋 메시지 말미: `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`
- 주석·문서: **경험담 서술 금지** — 함정은 `주의.` 항목으로 **현재형 지식 서술**("~해서 고쳤다" 금지, "~하면 ~가 된다" 로).
- 한국어 저장소다. 기존 문체를 따른다.
- **기존 `java -jar` 트랙을 건드리지 않는다.** `docker-compose/`·`gateway/`·`http/` 는 이 슬라이스의 대상이 아니다.

## 이 계획에는 단위 테스트가 없다

YAML 을 단위 테스트할 방법이 없다. 각 태스크의 "테스트"는 **kubectl 명령과 기대 출력**이다. 명령을 돌리고 실제 출력이 기대와 맞는지 확인하는 것이 이 계획의 RED/GREEN 이다.

**주의.** 상태 코드만 보고 통과로 적지 않는다. 403 이 Istio 에서 온 것인지 애플리케이션에서 온 것인지는 **본문**으로 구분한다 — Istio 는 정확히 `RBAC: access denied` 를 돌려준다.

## 검증 파드 구성 — 왜 curl 파드를 따로 두나

Istio 의 신원은 파드가 무엇을 실행하느냐가 아니라 **ServiceAccount 에서 나온다.** 그래서 SA 만 빌린 curl 파드가 그 서비스의 SPIFFE 신원을 그대로 갖는다.

| 파드 | ServiceAccount | 용도 |
|---|---|---|
| `caller-token` | `token` | token 신원으로 호출 |
| `caller-auth` | `auth` | auth 신원으로 호출 |
| `no-mesh` | (기본) | 사이드카 없음. mTLS 강제 확인용 |

**실제 서비스 간 호출은 별도로 탄다.** `token` 의 `/oauth2/jwks` 는 signing 의 `/oauth2/jwks` 로 프록시하는 실제 코드 경로이고 Redis·MySQL 을 타지 않는다. `curl token:8082/oauth2/jwks` 로 그 경로를 태워 **진짜 `SigningClient` 가 mesh 를 통과**하는 것을 확인한다.

**주의.** `eclipse-temurin:21-jre` 에는 curl 이 들어 있다(8.18.0, 확인함). 그래서 `kubectl exec deploy/token -- curl` 로 실제 서비스 파드에서 직접 부르는 것도 가능하다. 그렇게 하지 않는 이유는 증거 강도가 아니라 **결합**이다 — Istio 가 보는 principal 은 어느 쪽이든 `sa/token` 으로 같고, 전용 curl 파드는 베이스 이미지를 distroless 나 alpine 으로 바꿔도 검증이 깨지지 않으며 `caller-auth` 와 대칭이다. 이 사실을 다시 조사하지 않도록 적어 둔다.

## File Structure

```
microservice/k8s/
  README.md                        이 트랙 사용법. 기존 java -jar 트랙과 별개임을 명시
  Dockerfile                       서비스 3개 공용. ARG 로 jar 경로만 받는다
  base/
    namespace.yaml                 네임스페이스 + istio-injection 라벨
    mysql.yaml                     Service + Deployment (emptyDir)
    signing.yaml                   SA + Service + Deployment (ConfigMap 불필요)
    client-registry.yaml           SA + Service + Deployment + ConfigMap
    token.yaml                     SA + Service + Deployment + ConfigMap
    callers.yaml                   SA auth + caller-token · caller-auth · no-mesh 파드
  istio/
    peer-authentication.yaml       네임스페이스 전체 STRICT
    authz-signing.yaml             /internal/sign 과 /oauth2/jwks 를 다르게
    authz-client-registry.yaml     GET /internal/clients/*
  verify.sh                        성공 기준 9개를 순서대로 돌리고 raw 출력을 남긴다
```

**주의.** signing 에는 ConfigMap 이 없다. `application.yml` 에 호스트 이름이 하나도 없고 keystore 가 classpath 에 있어 덮을 것이 없다.

---

## Task 1: kind 클러스터와 Istio

**Files:**
- Create: `microservice/k8s/README.md` (설치·기동 절차 부분만. 나머지는 Task 7 에서 채운다)

**Interfaces:**
- Produces: `microservice-as` 네임스페이스가 있는 클러스터, Istio 컨트롤 플레인, `istio-injection=enabled` 라벨

- [ ] **Step 1: 도구 확인**

```bash
command -v kind istioctl kubectl docker
```

`kind` 또는 `istioctl` 이 없으면 설치한다.

```bash
brew install kind istioctl
```

설치가 권한으로 막히면 **거기서 멈추고 사용자에게 보고한다.** 다른 방법으로 우회하지 않는다.

- [ ] **Step 2: Istio 가 지원하는 k8s 버전을 확인한다**

```bash
istioctl version --remote=false
```

출력된 Istio 버전에 대해 지원 k8s 범위를 확인한다(`https://istio.io/latest/docs/releases/supported-releases/`). **범위 안의 버전을 골라 `kindest/node` 이미지 태그를 정한다.**

**주의.** 현재 Docker Desktop 의 k8s 는 v1.34.3 인데, 그 버전이 설치한 Istio 의 지원 범위 밖일 수 있다. 최신이라는 이유로 고르지 않는다. **고른 버전과 근거를 보고서에 적는다.**

- [ ] **Step 3: 클러스터 생성**

`<NODE_IMAGE>` 는 Step 2 에서 정한 값이다.

```bash
kind create cluster --name microservice-as --image kindest/node:<NODE_IMAGE>
kubectl config use-context kind-microservice-as
kubectl get nodes
```

Expected: `microservice-as-control-plane` 이 `Ready`

**주의.** 기존 `docker-desktop` 컨텍스트를 건드리지 않는다. 이후 모든 `kubectl` 은 `kind-microservice-as` 컨텍스트에서 돈다.

- [ ] **Step 4: Istio 설치**

```bash
istioctl install --set profile=minimal -y
kubectl get pods -n istio-system
```

Expected: `istiod-*` 파드가 `Running 1/1`

`minimal` 프로파일은 컨트롤 플레인만 깐다. 이 트랙은 ingress gateway 가 필요 없다 — 외부 트래픽을 다루지 않는다.

- [ ] **Step 5: 네임스페이스 생성**

`microservice/k8s/base/namespace.yaml`:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: microservice-as
  labels:
    istio-injection: enabled
```

```bash
kubectl apply -f oauth-2/authorization-server/practice/microservice/k8s/base/namespace.yaml
kubectl get ns microservice-as --show-labels
```

Expected: 라벨에 `istio-injection=enabled` 가 보인다

**주의.** 이 라벨이 없으면 파드에 사이드카가 주입되지 않고, 그러면 mTLS 도 `AuthorizationPolicy` 도 전부 무력화된다. 파드가 `READY 1/1` 로 뜨면(2/2 가 아니라) 이 라벨을 먼저 의심한다.

- [ ] **Step 6: README 에 설치·기동 절차 기록**

`microservice/k8s/README.md` 를 만들고 아래를 담는다.

- 이 트랙이 **기존 `java -jar` 트랙과 별개**라는 것. 기존 e2e 를 대체하지 않는다
- Step 1~5 의 명령 순서
- Step 2 에서 고른 Istio 버전과 kind 노드 이미지, 그리고 그 근거
- 정리 명령: `kind delete cluster --name microservice-as`

- [ ] **Step 7: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/k8s
git commit -m "$(cat <<'EOF'
microservice(k8s): bootstrap a kind cluster with istio

Docker Desktop 의 k8s 는 노드 containerd 스토어가 docker 데몬 스토어와 분리돼
있어 로컬 빌드 이미지가 ErrImageNeverPull 로 막힌다. kind 는 load 한 줄로
해결되고 기존 클러스터를 건드리지 않는다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 2: Dockerfile 과 이미지 적재

**Files:**
- Create: `microservice/k8s/Dockerfile`

**Interfaces:**
- Consumes: Task 1 의 클러스터
- Produces: 노드에 적재된 `signing:local` · `client-registry:local` · `token:local` · `curlimages/curl:latest`

- [ ] **Step 1: Dockerfile 작성**

`microservice/k8s/Dockerfile`:

```dockerfile
# 세 서비스가 전부 Spring Boot fat jar 이라 모양이 같다. jar 경로만 ARG 로 받는다.
FROM eclipse-temurin:21-jre
ARG JAR
COPY ${JAR} /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

- [ ] **Step 2: jar 빌드**

각 서비스 디렉터리에서 실행한다. `./gradlew` 가 exit 137(SIGKILL)로 죽으면 우회한다.

```bash
cd oauth-2/authorization-server/practice/microservice/signing
./gradlew bootJar --no-daemon -x test
# 우회: /Users/starryeye/.sdkman/candidates/java/21.0.6-amzn/bin/java \
#         -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain \
#         bootJar --no-daemon -x test
```

`client-registry`·`token` 도 같은 방식으로 빌드한다.

```bash
ls */build/libs/*-SNAPSHOT.jar
```

Expected: 세 jar 이 보인다

- [ ] **Step 3: 이미지 빌드**

microservice 루트에서 실행한다(빌드 컨텍스트가 루트여야 jar 경로가 맞는다).

```bash
cd oauth-2/authorization-server/practice/microservice
for s in signing client-registry token; do
  docker build -f k8s/Dockerfile \
    --build-arg JAR=$s/build/libs/$s-0.0.1-SNAPSHOT.jar \
    -t $s:local .
done
docker images | grep -E '^(signing|client-registry|token) '
```

Expected: 세 이미지가 보인다

**주의.** jar 파일명이 `<service>-0.0.1-SNAPSHOT.jar` 가 아닐 수 있다. `ls build/libs/` 로 실제 이름을 확인하고 맞춘다.

- [ ] **Step 4: 이미지를 노드에 적재**

```bash
docker pull curlimages/curl:latest
for img in signing:local client-registry:local token:local curlimages/curl:latest; do
  kind load docker-image "$img" --name microservice-as
done
```

- [ ] **Step 5: 적재 확인**

```bash
docker exec microservice-as-control-plane crictl images | grep -E 'signing|client-registry|token|curl'
```

Expected: 네 이미지가 전부 노드 containerd 스토어에 있다

**주의.** 이 확인을 건너뛰면 나중에 `ErrImageNeverPull` 이 났을 때 원인이 적재 누락인지 태그 오타인지 구분이 안 된다.

- [ ] **Step 6: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/k8s/Dockerfile
git commit -m "$(cat <<'EOF'
microservice(k8s): add a shared Dockerfile for the three services

세 서비스가 전부 Spring Boot fat jar 이라 Dockerfile 하나로 충분하다.
jar 경로만 ARG 로 받는다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 3: MySQL 과 client-registry

**Files:**
- Create: `microservice/k8s/base/mysql.yaml`
- Create: `microservice/k8s/base/client-registry.yaml`

**Interfaces:**
- Consumes: Task 2 의 이미지
- Produces: `client-registry` Service(8085), ServiceAccount `client-registry`, seed 된 `clients` 테이블

- [ ] **Step 1: mysql.yaml 작성**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: mysql
  namespace: microservice-as
spec:
  selector:
    app: mysql
  ports:
    # 주의. MySQL 은 server-first 프로토콜이라 서버가 먼저 greeting 을 보낸다.
    #      포트 이름을 tcp- 로 시작해 Istio 의 프로토콜 자동 감지를 건너뛰게 한다.
    #      이름이 없거나 http- 로 시작하면 사이드카가 HTTP 로 오인해 연결이 끊긴다.
    - name: tcp-mysql
      port: 3306
      targetPort: 3306
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mysql
  namespace: microservice-as
spec:
  replicas: 1
  selector:
    matchLabels:
      app: mysql
  template:
    metadata:
      labels:
        app: mysql
    spec:
      containers:
        - name: mysql
          image: mysql:8
          env:
            - name: MYSQL_ROOT_PASSWORD
              value: "1111"
            - name: MYSQL_DATABASE
              value: microservice_as
          ports:
            - name: tcp-mysql
              containerPort: 3306
          volumeMounts:
            - name: data
              mountPath: /var/lib/mysql
      volumes:
        # 데이터가 살아남을 이유가 없다. ddl-auto: update 와 seed 가 재생성한다.
        - name: data
          emptyDir: {}
```

- [ ] **Step 2: client-registry.yaml 작성**

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: client-registry
  namespace: microservice-as
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: client-registry-config
  namespace: microservice-as
data:
  # 기존 application.yml 을 덮어쓰는 것이 아니라 위에 얹는다.
  # 바뀌는 것은 DB 호스트뿐이고 포트·계정·ddl-auto 는 원본 그대로다.
  application.yml: |
    spring:
      datasource:
        url: jdbc:mysql://mysql:3306/microservice_as
---
apiVersion: v1
kind: Service
metadata:
  name: client-registry
  namespace: microservice-as
spec:
  selector:
    app: client-registry
  ports:
    - name: http
      port: 8085
      targetPort: 8085
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: client-registry
  namespace: microservice-as
spec:
  replicas: 1
  selector:
    matchLabels:
      app: client-registry
  template:
    metadata:
      labels:
        app: client-registry
    spec:
      serviceAccountName: client-registry
      containers:
        - name: client-registry
          image: client-registry:local
          imagePullPolicy: IfNotPresent
          env:
            # 주의. my.client-registry-base-url 같은 키를 환경변수로 넘기면 relaxed
            #      binding 이 하이픈을 지워 MY_CLIENTREGISTRYBASEURL 이 된다. 읽는 사람이
            #      원래 키를 복원할 수 없으므로 ConfigMap 을 파일로 얹는 방식만 쓴다.
            - name: SPRING_CONFIG_ADDITIONAL_LOCATION
              value: file:/config/
          ports:
            - name: http
              containerPort: 8085
          volumeMounts:
            - name: config
              mountPath: /config
      volumes:
        - name: config
          configMap:
            name: client-registry-config
```

- [ ] **Step 3: 배포**

```bash
cd oauth-2/authorization-server/practice/microservice
kubectl apply -f k8s/base/mysql.yaml
kubectl wait --for=condition=available --timeout=180s deploy/mysql -n microservice-as
kubectl apply -f k8s/base/client-registry.yaml
kubectl wait --for=condition=available --timeout=180s deploy/client-registry -n microservice-as
```

- [ ] **Step 4: 사이드카 주입과 기동 확인**

```bash
kubectl get pods -n microservice-as
```

Expected: `mysql-*` 과 `client-registry-*` 가 전부 `READY 2/2`

**1/1 이면 Task 1 Step 5 의 라벨을 확인한다.** 라벨을 나중에 붙였다면 파드를 재생성해야 주입된다(`kubectl rollout restart deploy/<name> -n microservice-as`).

- [ ] **Step 5: seed 확인**

```bash
kubectl exec -n microservice-as deploy/mysql -c mysql -- \
  mysql -uroot -p1111 microservice_as -e "select client_id, client_scopes from clients;"
```

Expected: `my-client` · `article-api` · `demo-rp` 세 행

**주의.** 행이 없으면 client-registry 가 MySQL 에 붙지 못한 것이다. `kubectl logs -n microservice-as deploy/client-registry -c client-registry` 를 보고, 연결 오류라면 Service 포트 이름이 `tcp-mysql` 인지 먼저 확인한다.

- [ ] **Step 6: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/k8s/base/mysql.yaml \
        oauth-2/authorization-server/practice/microservice/k8s/base/client-registry.yaml
git commit -m "$(cat <<'EOF'
microservice(k8s): deploy mysql and client-registry

MySQL Service 의 포트 이름을 tcp-mysql 로 둔다. server-first 프로토콜이라
Istio 의 프로토콜 자동 감지가 HTTP 로 오인하면 연결이 끊긴다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 4: signing 과 token

**Files:**
- Create: `microservice/k8s/base/signing.yaml`
- Create: `microservice/k8s/base/token.yaml`

**Interfaces:**
- Consumes: Task 2 의 이미지, Task 3 의 네임스페이스
- Produces: ServiceAccount `signing`·`token`, Service `signing`(8083)·`token`(8082)

- [ ] **Step 1: signing.yaml 작성**

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: signing
  namespace: microservice-as
---
apiVersion: v1
kind: Service
metadata:
  name: signing
  namespace: microservice-as
spec:
  selector:
    app: signing
  ports:
    - name: http
      port: 8083
      targetPort: 8083
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: signing
  namespace: microservice-as
spec:
  replicas: 1
  selector:
    matchLabels:
      app: signing
  template:
    metadata:
      labels:
        app: signing
    spec:
      serviceAccountName: signing
      containers:
        - name: signing
          image: signing:local
          imagePullPolicy: IfNotPresent
          ports:
            - name: http
              containerPort: 8083
```

**주의.** signing 에는 ConfigMap 이 없다. `application.yml` 에 호스트 이름이 하나도 없고 keystore 가 classpath(jar 안)에 있어 덮을 것이 없다.

- [ ] **Step 2: token.yaml 작성**

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: token
  namespace: microservice-as
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: token-config
  namespace: microservice-as
data:
  # 이 트랙이 실제로 부르는 두 곳만 덮는다.
  # user-directory·token-state·session 은 이 트랙에 배포하지 않으며 그 경로를 부르지 않는다.
  # Redis 도 배포하지 않는다 — Lettuce 는 지연 연결이라 없어도 부팅된다.
  application.yml: |
    my:
      signing-base-url: http://signing:8083
      client-registry-base-url: http://client-registry:8085
---
apiVersion: v1
kind: Service
metadata:
  name: token
  namespace: microservice-as
spec:
  selector:
    app: token
  ports:
    - name: http
      port: 8082
      targetPort: 8082
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: token
  namespace: microservice-as
spec:
  replicas: 1
  selector:
    matchLabels:
      app: token
  template:
    metadata:
      labels:
        app: token
    spec:
      serviceAccountName: token
      containers:
        - name: token
          image: token:local
          imagePullPolicy: IfNotPresent
          env:
            - name: SPRING_CONFIG_ADDITIONAL_LOCATION
              value: file:/config/
          ports:
            - name: http
              containerPort: 8082
          volumeMounts:
            - name: config
              mountPath: /config
      volumes:
        - name: config
          configMap:
            name: token-config
```

- [ ] **Step 3: 배포와 기동 확인**

```bash
cd oauth-2/authorization-server/practice/microservice
kubectl apply -f k8s/base/signing.yaml -f k8s/base/token.yaml
kubectl wait --for=condition=available --timeout=180s deploy/signing deploy/token -n microservice-as
kubectl get pods -n microservice-as
```

Expected: 네 파드(mysql·client-registry·signing·token) 전부 `READY 2/2`

- [ ] **Step 4: 실제 서비스 간 호출 확인 — 정책 걸기 전**

아직 `AuthorizationPolicy` 가 없으므로 전부 통해야 한다. 이 시점의 성공은 **배선이 맞다**는 뜻이다.

```bash
kubectl run probe --rm -it --restart=Never --image=curlimages/curl:latest \
  -n microservice-as --image-pull-policy=IfNotPresent -- \
  curl -s -o /dev/null -w 'token/oauth2/jwks=%{http_code}\n' http://token:8082/oauth2/jwks
```

Expected: `token/oauth2/jwks=200`

**이것이 이 트랙의 유일한 실제 코드 경로다.** token 의 `/oauth2/jwks` 는 `SigningClient.jwks()` 로 signing 을 부르는 프록시이고, Redis 도 MySQL 도 타지 않는다. 200 이 나오면 token→signing 호출이 mesh 를 통과했다는 뜻이다.

**주의.** 500 이 나오면 token 이 signing 에 못 닿은 것이다. `kubectl logs -n microservice-as deploy/token -c token` 을 보고 ConfigMap 이 `/config/application.yml` 로 실제 마운트됐는지(`kubectl exec ... -- cat /config/application.yml`) 확인한다.

- [ ] **Step 5: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/k8s/base/signing.yaml \
        oauth-2/authorization-server/practice/microservice/k8s/base/token.yaml
git commit -m "$(cat <<'EOF'
microservice(k8s): deploy signing and token

signing 에는 ConfigMap 이 없다 — application.yml 에 호스트 이름이 없고 keystore 가
jar 안에 있어 덮을 것이 없다. token 은 실제로 부르는 두 base-url 만 덮는다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 5: 호출자 파드와 mTLS 강제

**Files:**
- Create: `microservice/k8s/base/callers.yaml`
- Create: `microservice/k8s/istio/peer-authentication.yaml`

**Interfaces:**
- Consumes: Task 4 의 서비스들
- Produces: ServiceAccount `auth`, 파드 `caller-token`·`caller-auth`·`no-mesh`, 네임스페이스 전체 STRICT mTLS

- [ ] **Step 1: callers.yaml 작성**

```yaml
# 주의. Istio 의 신원은 파드가 무엇을 실행하느냐가 아니라 ServiceAccount 에서 나온다.
#      그래서 SA 만 빌린 curl 파드가 그 서비스의 SPIFFE 신원을 그대로 갖는다.
#      auth 서비스 자체는 이 트랙에 배포하지 않는다(Redis·user-directory·consent·session 이 필요하다).
apiVersion: v1
kind: ServiceAccount
metadata:
  name: auth
  namespace: microservice-as
---
apiVersion: v1
kind: Pod
metadata:
  name: caller-token
  namespace: microservice-as
  labels:
    app: caller-token
spec:
  serviceAccountName: token
  containers:
    - name: curl
      image: curlimages/curl:latest
      imagePullPolicy: IfNotPresent
      command: ["sleep", "infinity"]
---
apiVersion: v1
kind: Pod
metadata:
  name: caller-auth
  namespace: microservice-as
  labels:
    app: caller-auth
spec:
  serviceAccountName: auth
  containers:
    - name: curl
      image: curlimages/curl:latest
      imagePullPolicy: IfNotPresent
      command: ["sleep", "infinity"]
---
apiVersion: v1
kind: Pod
metadata:
  name: no-mesh
  namespace: microservice-as
  labels:
    app: no-mesh
  annotations:
    # 사이드카를 일부러 넣지 않는다. PeerAuthentication STRICT 가 실제로 강제되는지 확인하는 파드다.
    sidecar.istio.io/inject: "false"
spec:
  containers:
    - name: curl
      image: curlimages/curl:latest
      imagePullPolicy: IfNotPresent
      command: ["sleep", "infinity"]
```

- [ ] **Step 2: 배포와 사이드카 확인**

```bash
cd oauth-2/authorization-server/practice/microservice
kubectl apply -f k8s/base/callers.yaml
kubectl wait --for=condition=ready --timeout=120s pod/caller-token pod/caller-auth pod/no-mesh -n microservice-as
kubectl get pods -n microservice-as -o custom-columns=NAME:.metadata.name,READY:.status.containerStatuses[*].ready
```

Expected: `caller-token`·`caller-auth` 는 컨테이너 2개, `no-mesh` 는 1개

- [ ] **Step 3: STRICT 걸기 전 상태 기록**

정책 전에는 사이드카 없는 파드도 signing 에 닿아야 한다. 이 값이 Step 5 의 비교 기준이다.

```bash
kubectl exec -n microservice-as no-mesh -- \
  curl -s -o /dev/null -w 'no-mesh->signing(before STRICT)=%{http_code}\n' \
  --max-time 5 http://signing:8083/oauth2/jwks
```

Expected: `no-mesh->signing(before STRICT)=200`

- [ ] **Step 4: peer-authentication.yaml 작성과 적용**

```yaml
apiVersion: security.istio.io/v1
kind: PeerAuthentication
metadata:
  name: default
  namespace: microservice-as
spec:
  mtls:
    mode: STRICT
```

```bash
kubectl apply -f k8s/istio/peer-authentication.yaml
sleep 10   # 사이드카에 설정이 전파되는 시간
```

**주의.** API 그룹 버전(`security.istio.io/v1` 대 `v1beta1`)은 설치한 Istio 버전에 따라 다르다. `kubectl api-resources | grep peerauthentication` 으로 확인하고 맞춘다. 문서의 예시를 그대로 믿지 않는다.

- [ ] **Step 5: mTLS 강제 확인**

```bash
kubectl exec -n microservice-as no-mesh -- \
  curl -s -o /dev/null -w 'no-mesh->signing(after STRICT)=%{http_code}\n' \
  --max-time 5 http://signing:8083/oauth2/jwks
```

Expected: `000` (연결 거부). Step 3 의 `200` 과 대비된다.

```bash
kubectl exec -n microservice-as caller-token -- \
  curl -s -o /dev/null -w 'caller-token->signing=%{http_code}\n' \
  http://signing:8083/oauth2/jwks
```

Expected: `200` (사이드카가 있으므로 mTLS 로 통과)

**주의.** `no-mesh` 가 여전히 200 이면 STRICT 가 전파되지 않았거나 `PERMISSIVE` 로 남아 있는 것이다. `istioctl x describe pod signing-<hash> -n microservice-as` 로 확인한다.

- [ ] **Step 6: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/k8s/base/callers.yaml \
        oauth-2/authorization-server/practice/microservice/k8s/istio/peer-authentication.yaml
git commit -m "$(cat <<'EOF'
microservice(k8s): enforce mTLS and add identity-bearing caller pods

Istio 의 신원은 ServiceAccount 에서 나오므로 SA 만 빌린 curl 파드가 그 서비스의
SPIFFE 신원을 그대로 갖는다. no-mesh 파드는 STRICT 가 실제로 강제되는지 확인한다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 6: AuthorizationPolicy

**Files:**
- Create: `microservice/k8s/istio/authz-signing.yaml`
- Create: `microservice/k8s/istio/authz-client-registry.yaml`

**Interfaces:**
- Consumes: Task 5 의 호출자 파드와 STRICT mTLS
- Produces: 호출자별·엔드포인트별 인가

- [ ] **Step 1: authz-signing.yaml 작성**

```yaml
# signing 에는 엔드포인트가 둘 있고 허용 집합이 다르다.
#   POST /internal/sign  — 서명. token 만 부른다.
#   GET  /oauth2/jwks    — 공개키. token 과 auth 가 읽는다.
# auth 는 공개키는 읽지만 서명은 하지 않는다. 브라우저를 마주보는 서비스가
# 개인키 보유자에게 무제한 접근권을 갖지 않게 하는 것이 signing 을 따로 뗀 이유다.
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: signing
  namespace: microservice-as
spec:
  selector:
    matchLabels:
      app: signing
  action: ALLOW
  rules:
    - from:
        - source:
            principals: ["cluster.local/ns/microservice-as/sa/token"]
      to:
        - operation:
            methods: ["POST"]
            paths: ["/internal/sign"]
    - from:
        - source:
            principals:
              - "cluster.local/ns/microservice-as/sa/token"
              - "cluster.local/ns/microservice-as/sa/auth"
      to:
        - operation:
            methods: ["GET"]
            paths: ["/oauth2/jwks"]
```

**주의.** Istio 의 ALLOW 정책은 deny-by-default 다. 어떤 ALLOW 정책이 워크로드를 선택하는 순간 규칙에 맞지 않는 나머지는 전부 거부된다. `/internal/sign` 규칙만 쓰면 `/oauth2/jwks` 가 조용히 막힌다 — 두 규칙이 함께 있는 이유다.

- [ ] **Step 2: authz-client-registry.yaml 작성**

```yaml
apiVersion: security.istio.io/v1
kind: AuthorizationPolicy
metadata:
  name: client-registry
  namespace: microservice-as
spec:
  selector:
    matchLabels:
      app: client-registry
  action: ALLOW
  rules:
    - from:
        - source:
            principals:
              - "cluster.local/ns/microservice-as/sa/token"
              - "cluster.local/ns/microservice-as/sa/auth"
      to:
        - operation:
            methods: ["GET"]
            paths: ["/internal/clients/*"]
```

- [ ] **Step 3: 적용**

```bash
cd oauth-2/authorization-server/practice/microservice
kubectl apply -f k8s/istio/authz-signing.yaml -f k8s/istio/authz-client-registry.yaml
sleep 10
```

- [ ] **Step 4: 인가 표 확인 — 이 태스크의 핵심**

```bash
run() { kubectl exec -n microservice-as "$1" -- curl -s -w "\n  status=%{http_code}\n" "${@:2}"; }

echo "== caller-token -> signing /oauth2/jwks (기대 200)"
run caller-token -o /dev/null http://signing:8083/oauth2/jwks

echo "== caller-auth -> signing /oauth2/jwks (기대 200)"
run caller-auth -o /dev/null http://signing:8083/oauth2/jwks

echo "== caller-auth -> signing /internal/sign (기대 403 + RBAC: access denied)"
run caller-auth -X POST -H 'Content-Type: application/json' \
    -d '{"claims":{"sub":"probe"},"typ":"at+jwt"}' http://signing:8083/internal/sign

echo "== caller-token -> client-registry (기대 200)"
run caller-token -o /dev/null http://client-registry:8085/internal/clients/my-client

echo "== caller-auth -> client-registry (기대 200)"
run caller-auth -o /dev/null http://client-registry:8085/internal/clients/my-client
```

Expected: 위 주석의 기대값 그대로. **`caller-auth` 의 `/internal/sign` 은 상태 코드뿐 아니라 본문이 `RBAC: access denied` 여야 한다.**

**주의.** 상태 코드만 보고 통과로 적지 않는다. signing 의 `/internal/sign` 은 애플리케이션 차원에서 403 을 낼 일이 없고, Istio 는 정확히 `RBAC: access denied` 를 돌려준다. 본문이 다르면 다른 이유로 막힌 것이다.

- [ ] **Step 5: 실제 코드 경로가 여전히 통하는지 확인**

정책이 실제 서비스 간 호출을 막지 않았는지 본다.

```bash
kubectl exec -n microservice-as caller-token -- \
  curl -s -o /dev/null -w 'token/oauth2/jwks=%{http_code}\n' http://token:8082/oauth2/jwks
```

Expected: `200`

**주의.** 여기서 500 이 나면 signing 정책의 jwks 규칙이 token 을 빠뜨린 것이다. token 은 자기 SA 로 signing 을 부른다.

- [ ] **Step 6: 뮤테이션 — 정책이 실제로 그 거부를 만드는지 증명**

`authz-signing.yaml` 의 **첫 번째 규칙**(`/internal/sign`) `principals` 에 auth 를 임시로 추가한다.

```yaml
            principals:
              - "cluster.local/ns/microservice-as/sa/token"
              - "cluster.local/ns/microservice-as/sa/auth"   # 뮤테이션: 임시 추가
```

```bash
kubectl apply -f k8s/istio/authz-signing.yaml
sleep 10
kubectl exec -n microservice-as caller-auth -- \
  curl -s -o /dev/null -w 'MUTATED caller-auth->/internal/sign=%{http_code}\n' \
  -X POST -H 'Content-Type: application/json' \
  -d '{"claims":{"sub":"probe"},"typ":"at+jwt"}' http://signing:8083/internal/sign
```

Expected: `403` 이 **`200` 으로 바뀐다**

되돌린다.

**주의.** 이 시점에 정책 파일은 아직 커밋 전(untracked)이다. `git checkout --` 는 실패하고 `git diff --exit-code` 는 비교 대상이 없어 **조용히 통과**하므로 원복 증거가 되지 못한다. 편집한 줄을 직접 되돌리고 **원본 텍스트와 diff 로 대조**해 증명한다.

```bash
# principals 목록에서 임시로 넣은 auth 줄을 지운다
kubectl apply -f k8s/istio/authz-signing.yaml
sleep 10
grep -c 'sa/auth' k8s/istio/authz-signing.yaml    # 기대: 1 (jwks 규칙에만 남는다)
```

그리고 403 으로 돌아왔는지 다시 확인한다.

**주의.** 이 확인이 없으면 "정책이 막았다"와 "원래부터 안 되던 것"을 구분할 수 없다. 실패 메시지(여기서는 200 으로 바뀐 출력)를 보고서에 그대로 인용한다.

- [ ] **Step 7: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/k8s/istio/authz-signing.yaml \
        oauth-2/authorization-server/practice/microservice/k8s/istio/authz-client-registry.yaml
git commit -m "$(cat <<'EOF'
microservice(k8s): authorize per caller and per endpoint

signing 은 같은 호출자라도 엔드포인트에 따라 갈린다 — auth 신원은 /oauth2/jwks 는
읽고 /internal/sign 은 거부된다. Istio 의 ALLOW 정책은 deny-by-default 라
두 규칙이 함께 있어야 jwks 가 막히지 않는다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## Task 7: verify.sh 와 문서

**Files:**
- Create: `microservice/k8s/verify.sh`
- Modify: `microservice/k8s/README.md`
- Modify: `microservice/README.md`

**Interfaces:**
- Consumes: 앞의 모든 태스크

- [ ] **Step 1: verify.sh 작성**

성공 기준 9개를 순서대로 돌리고 **명령과 raw 출력**을 남긴다. 실패해도 멈추지 않고 전부 돌린 뒤 마지막에 합계를 낸다(어디까지 되는지 한 번에 보여야 한다).

```bash
#!/usr/bin/env bash
# 슬라이스 6 검증. 클러스터가 떠 있고 전 매니페스트가 적용된 상태에서 실행한다.
#   ./verify.sh
set -u
NS=microservice-as
PASS=0; FAIL=0

check() {  # check <설명> <기대> <실제>
  if [ "$2" = "$3" ]; then echo "  PASS  $1  ($3)"; PASS=$((PASS+1))
  else echo "  FAIL  $1  기대=$2 실제=$3"; FAIL=$((FAIL+1)); fi
}

code() { kubectl exec -n $NS "$1" -- curl -s -o /dev/null -w '%{http_code}' --max-time 10 "${@:2}"; }

# 주의. AuthorizationPolicy 를 apply 한 직후에는 사이드카로의 xDS 전파가 끝나지 않아
#      거부돼야 할 호출이 잠깐 200 을 낸다. 그 창에서 검사하면 정책이 멀쩡해도 FAIL 이 난다.
#      그래서 검사 전에 핵심 거부(caller-auth -> /internal/sign)가 403 이 될 때까지 기다린다.
#      제한 시간 안에 수렴하지 않으면 기다림을 포기하고 그대로 검사에 들어간다 —
#      전파 지연과 정책 결함을 구분하되, 결함을 대기로 덮지는 않는다.
DENY_PROBE=(-X POST -H 'Content-Type: application/json'
            -d '{"claims":{"sub":"probe"},"typ":"at+jwt"}' http://signing:8083/internal/sign)
echo "[0] 정책 전파 대기 (최대 60초)"
for i in $(seq 1 30); do
  if [ "$(code caller-auth "${DENY_PROBE[@]}")" = "403" ]; then
    echo "  전파 완료: ${i}회차 (약 $((i*2))초)"
    break
  fi
  [ "$i" = "30" ] && echo "  경고: 60초 안에 403 으로 수렴하지 않았다. 그대로 검사한다."
  sleep 2
done

echo "[1] 파드 상태"
kubectl get pods -n $NS
READY=$(kubectl get pods -n $NS -o jsonpath='{range .items[*]}{.metadata.name}{" "}{.status.containerStatuses[*].ready}{"\n"}{end}')
echo "$READY"

echo "[2] caller-token -> signing /oauth2/jwks"
check "token 신원은 jwks 를 읽는다" 200 "$(code caller-token http://signing:8083/oauth2/jwks)"

echo "[3] caller-token -> signing /internal/sign"
check "token 신원은 서명할 수 있다" 200 "$(code caller-token -X POST \
  -H 'Content-Type: application/json' -d '{"claims":{"sub":"probe"},"typ":"at+jwt"}' \
  http://signing:8083/internal/sign)"

echo "[4] caller-auth -> signing /oauth2/jwks"
check "auth 신원은 jwks 를 읽는다" 200 "$(code caller-auth http://signing:8083/oauth2/jwks)"

echo "[5] caller-auth -> signing /internal/sign  <= 핵심"
BODY=$(kubectl exec -n $NS caller-auth -- curl -s --max-time 10 -X POST \
  -H 'Content-Type: application/json' -d '{"claims":{"sub":"probe"},"typ":"at+jwt"}' \
  http://signing:8083/internal/sign)
check "auth 신원은 서명할 수 없다" 403 "$(code caller-auth -X POST \
  -H 'Content-Type: application/json' -d '{"claims":{"sub":"probe"},"typ":"at+jwt"}' \
  http://signing:8083/internal/sign)"
echo "  본문: $BODY"
check "거부가 Istio 에서 왔다" "RBAC: access denied" "$BODY"

echo "[6] caller-token -> client-registry"
check "token 신원은 client 를 읽는다" 200 "$(code caller-token http://client-registry:8085/internal/clients/my-client)"

echo "[7] caller-auth -> client-registry"
check "auth 신원도 client 를 읽는다" 200 "$(code caller-auth http://client-registry:8085/internal/clients/my-client)"

echo "[8] no-mesh -> signing (mTLS 강제)"
check "사이드카 없는 파드는 닿지 못한다" 000 "$(code no-mesh http://signing:8083/oauth2/jwks)"

echo "[9] 실제 코드 경로: token -> signing 프록시"
check "token 의 jwks 프록시가 동작한다" 200 "$(code caller-token http://token:8082/oauth2/jwks)"

echo
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
```

```bash
chmod +x oauth-2/authorization-server/practice/microservice/k8s/verify.sh
```

- [ ] **Step 2: 실행하고 raw 출력 확보**

```bash
cd oauth-2/authorization-server/practice/microservice/k8s
./verify.sh 2>&1 | tee /tmp/slice6-verify.log
```

Expected: `FAIL=0`

**실패하면 통과하도록 기준을 낮추지 마라.** 무엇이 왜 실패했는지 보고한다.

- [ ] **Step 3: Spring 소스 무변경 확인 — 이 슬라이스의 주제**

`edb6f2d` 는 슬라이스 6 설계 문서 커밋이다. 그 이후가 이 슬라이스의 작업 범위다.

```bash
cd /Users/starryeye/study/spring-security
git diff --stat edb6f2d..HEAD -- \
  'oauth-2/authorization-server/practice/microservice/*/src'
```

Expected: **출력 없음**

**주의.** 출력이 있으면 이 슬라이스의 주장("애플리케이션 코드 없이 닫는다")이 거짓이 된다. 무엇이 왜 바뀌었는지 보고한다.

- [ ] **Step 4: k8s/README.md 완성**

Task 1 에서 만든 파일에 아래를 추가한다.

- 아키텍처 그림(어느 파드가 어느 SA 를 갖고 무엇을 부를 수 있는지)
- 인가 표 (jwks 는 되고 sign 은 안 되는 그 표)
- `verify.sh` 사용법과 **Step 2 의 raw 출력**
- 진단표: 사이드카 미주입(`READY 1/1`) · `ErrImageNeverPull` · MySQL 연결 실패 · 전부 403 · 정책 미적용
- 정리: `kind delete cluster --name microservice-as`
- **`AuthorizationPolicy` 를 안 건 서비스**(token·mysql)는 어떤 정책도 선택하지 않아 기본 허용이라는 사실

- [ ] **Step 5: microservice/README.md 갱신**

- "설계/계획 문서" 절에 슬라이스 6 설계·계획 링크 2줄
- 알려진 한계에 추가:
  - **정책이 클러스터에만 산다** — `java -jar` 트랙은 여전히 무인증이다. 같은 코드인데 보호 수준이 다르다
  - **10개 중 3개만 올렸다** — 나머지는 매니페스트가 없고, 확장은 같은 패턴의 반복이다
  - **OAuth 흐름이 클러스터에서 돌지 않는다** — 정책만 증명한다
  - **인증서 회전을 검증하지 않는다** — mesh 가 자동으로 하지만 이 슬라이스는 그 동작을 확인하지 않는다
- 기존 한계 **"내부 REST 호출 무인증"** 항목에 "k8s 트랙에서는 Istio 가 닫는다(단 그 트랙에서만)"를 덧붙인다. **항목을 지우지 않는다** — `java -jar` 트랙에서는 여전히 참이다

- [ ] **Step 6: 정리**

```bash
kind delete cluster --name microservice-as
kubectl config use-context docker-desktop
kubectl config current-context
```

Expected: `docker-desktop` — 사용자의 기존 컨텍스트로 돌려놓는다

- [ ] **Step 7: 커밋**

```bash
git add oauth-2/authorization-server/practice/microservice/k8s/verify.sh \
        oauth-2/authorization-server/practice/microservice/k8s/README.md \
        oauth-2/authorization-server/practice/microservice/README.md
git commit -m "$(cat <<'EOF'
microservice(k8s): add verify.sh and document the slice

성공 기준 9개를 스크립트로 고정한다. 핵심은 auth 신원이 /oauth2/jwks 는 읽고
/internal/sign 은 403 을 받는 것이며, 그 403 의 본문이 RBAC: access denied 인지까지
확인한다. Spring 소스 변경이 0줄임을 함께 증명한다.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

## 최종 상태

| | |
|---|---|
| 새 디렉터리 | `microservice/k8s/` — 매니페스트 9개 + Dockerfile + verify.sh + README |
| Spring 소스 변경 | **0줄** |
| 기존 테스트 | 251개 그대로(이 슬라이스는 건드리지 않는다) |
| 검증 | `verify.sh` 의 9개 기준, raw 출력으로 |
