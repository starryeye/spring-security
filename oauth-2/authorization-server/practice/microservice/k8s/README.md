# microservice — Istio mTLS 트랙 (k8s)

이 디렉터리는 `oauth-2/authorization-server/practice/microservice/` 의 **Istio mTLS 트랙**을 위한
Kubernetes/Istio 리소스를 담는다.

**이 트랙은 기존 `java -jar` 트랙과 별개다.** `docker-compose/`, `gateway/`, `http/`, 각 서비스의
`*/src` 는 이 트랙의 대상이 아니고 그대로 유지된다. 기존 e2e(스크립트로 각 서비스를 `java -jar` 로
띄우는 방식)를 대체하지 않으며, 두 트랙은 서로 독립적으로 존재한다. 이 트랙은 내부 서비스 간
인증·인가(mTLS, `AuthorizationPolicy`)를 **Spring 코드 변경 없이** Istio 서비스 메시가 대신 처리하는
것을 검증하기 위한 것이다.

## 사전 준비물

- `kubectl`
- `docker` (Docker Desktop)
- `kind` — 로컬 전용 k8s 클러스터를 만드는 도구
- `istioctl` — Istio 설치/조작 CLI

`kind` 와 `istioctl` 은 Homebrew 로 설치한다.

```bash
brew install kind istioctl
```

**주의.** Docker Desktop 자체의 Kubernetes(현재 `docker-desktop` 컨텍스트)는 이 트랙과 무관하며
건드리지 않는다. Docker Desktop 의 k8s 는 노드 containerd 스토어가 docker 데몬 이미지 스토어와
분리돼 있어, 로컬에서 빌드한 이미지를 그 클러스터에 올리면 `ErrImageNeverPull` 로 막힌다. `kind` 로
별도 클러스터를 만들면 `kind load docker-image` 한 줄로 로컬 이미지를 노드에 넣을 수 있어 이 문제가
없다.

## 설치·기동 절차

### Step 1: 도구 확인

```bash
command -v kind istioctl kubectl docker
```

넷 다 있어야 다음 단계로 진행한다. `kind` 또는 `istioctl` 이 없으면 위 `brew install` 로 설치한다.
설치가 권한 문제로 막히면 여기서 멈추고 사용자에게 보고한다 — 다른 설치 경로(수동 바이너리 다운로드
등)로 우회하지 않는다.

### Step 2: Istio 가 지원하는 k8s 버전 확인

```bash
istioctl version --remote=false
```

설치된 Istio 버전에 대해 [Istio Supported Releases](https://istio.io/latest/docs/releases/supported-releases/)
에서 공식 지원 k8s 범위를 확인하고, 그 범위 안에서 `kindest/node` 이미지 태그를 고른다. **Docker
Desktop 의 k8s 버전을 그대로 따라가지 않는다** — 최신이라는 이유만으로는 Istio 의 지원 범위 안에
있다는 보장이 없다.

### Step 3: kind 클러스터 생성

```bash
kind create cluster --name microservice-as --image kindest/node:<NODE_IMAGE>
kubectl config use-context kind-microservice-as
kubectl get nodes
```

Expected: `microservice-as-control-plane` 이 `Ready`.

**주의.** 기존 `docker-desktop` 컨텍스트는 건드리지 않는다. 이 트랙의 모든 `kubectl` 명령은
`kind-microservice-as` 컨텍스트에서 실행한다. `kind create cluster` 는 클러스터 생성 직후
kubeconfig 의 현재 컨텍스트를 자동으로 `kind-microservice-as` 로 바꾼다 — 이 변경은
`~/.kube/config` 에 남고, 이후 별도 셸/세션에서도 이어진다.

### Step 4: Istio 설치 (minimal 프로파일)

```bash
istioctl install --set profile=minimal -y
kubectl get pods -n istio-system
```

Expected: `istiod-*` 파드가 `Running 1/1`.

`minimal` 프로파일은 컨트롤 플레인(istiod)만 설치한다. 이 트랙은 클러스터 외부에서 들어오는
트래픽을 다루지 않으므로 ingress gateway 가 필요 없다.

### Step 5: 네임스페이스 생성 (사이드카 자동 주입 라벨 포함)

`k8s/base/namespace.yaml`:

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

Expected: 라벨 목록에 `istio-injection=enabled` 가 보인다.

**주의.** 이 라벨이 없으면 이 네임스페이스에 뜬 파드에 Istio 사이드카(`istio-proxy`)가 주입되지
않는다. 사이드카가 없으면 mTLS 도 `AuthorizationPolicy` 도 전부 무력화된다 — mesh 가 트래픽을 보지
못하기 때문이다. 파드가 (`2/2` 가 아니라) `READY 1/1` 로 뜨면 이 라벨이 빠졌는지부터 의심한다.

## 이번에 고른 버전과 근거

| 항목 | 값 |
|---|---|
| Homebrew `kind` | 0.32.0 |
| Homebrew `istioctl` / Istio | 1.30.3 |
| kind 노드 이미지 | `kindest/node:v1.34.8@sha256:02722c2dedddcfc00febf5d27fbeb9b7b2c14294c82109ff4a85d89ac9ba3256` |

**근거.**

- `istioctl version --remote=false` 로 확인한 Istio 버전은 `1.30.3` 이고, Istio 공식 지원 릴리즈
  표(1.30 행)에 따르면 이 버전이 공식 지원하는 k8s 버전은 **1.32, 1.33, 1.34, 1.35, 1.36** 이다
  (1.27~1.31 은 "tested, but not supported"). Docker Desktop 의 k8s(v1.34.3)를 그대로 쓰지 않고
  독립적으로 이 표를 확인했다.
- kind `v0.32.0` 릴리즈가 사전 빌드해 제공하는 노드 이미지는 `v1.36.1`, `v1.35.5`, `v1.34.8`,
  `v1.33.12` 네 가지이며, 넷 다 Istio 1.30.3 의 공식 지원 범위(1.32~1.36) 안에 든다.
- 이 중 `v1.34.8` 을 선택했다. `v1.36.1` (kind 0.32.0 의 기본값)은 kubeadm 설정 포맷이
  v1beta3 에서 v1beta4 로 바뀌는 첫 릴리즈(k8s 1.36.0+)라서, 처음 부트스트랩하는 클러스터에서
  불필요한 리스크를 지고 싶지 않았다. `v1.34.8` 은 기존 v1beta3 포맷을 그대로 쓰는 마지막 라인 중
  하나이면서 Istio 1.30.3 지원 범위의 중간에 위치해 안정적이다.
- 반드시 `@sha256` 다이제스트까지 함께 지정한다 — kind 릴리즈 노트가 재현성을 위해 태그만이 아니라
  다이제스트 고정을 명시적으로 권장한다.

## 정리(clean up)

```bash
kind delete cluster --name microservice-as
```

클러스터를 통째로 지운다. `istio-system` 네임스페이스나 `microservice-as` 네임스페이스를 따로
지울 필요 없이 이 한 줄로 전체가 사라진다. 기존 `docker-desktop` 컨텍스트/클러스터에는 영향이
없다.
