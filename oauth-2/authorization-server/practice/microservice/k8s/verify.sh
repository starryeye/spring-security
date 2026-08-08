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

# 세 서비스(signing·client-registry·token)가 사이드카를 포함해(2/2) 전부 ready 인지 판정한다.
# 두 가지를 따로 봐야 한다.
#   (a) 파드의 Ready 조건 — k8s 1.28+ 는 restartPolicy: Always 인 init 컨테이너(= native
#       sidecar)까지 이 조건에 집계한다. containerStatuses 만 보면 istio-proxy 가
#       initContainers 에 있어 아예 관측되지 않으므로 (a) 를 Ready 조건으로 본다.
#   (b) istio-proxy 주입 여부 — 사이드카가 주입되지 않은 1/1 파드도 Ready 는 True 라서
#       (a) 만으로는 "주입 실패"(진단표 첫 행)를 잡지 못한다.
ALL_READY=true
for app in signing client-registry token; do
  CONDS=$(kubectl get pods -n $NS -l "app=$app" \
    -o jsonpath='{range .items[*]}{.status.conditions[?(@.type=="Ready")].status}{"\n"}{end}')
  PROXY=$(kubectl get pods -n $NS -l "app=$app" \
    -o jsonpath='{range .items[*]}{.spec.initContainers[*].name}{"\n"}{end}')
  [ -z "$CONDS" ] && ALL_READY=false          # 파드가 아예 없다
  case "$CONDS" in *False*) ALL_READY=false ;; esac
  case "$PROXY" in *istio-proxy*) ;; *) ALL_READY=false ;; esac
  echo "  $app: Ready=$(echo "$CONDS" | tr '\n' ' ')/ initContainers=$(echo "$PROXY" | tr '\n' ' ')"
done
check "signing/client-registry/token 세 서비스가 사이드카 포함 전부 ready" true "$ALL_READY"

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

echo "[8b] 실제 코드 경로: token -> signing 프록시"
check "token 의 jwks 프록시가 동작한다" 200 "$(code caller-token http://token:8082/oauth2/jwks)"

# 설계 §5 성공 기준 9번 — 이 슬라이스의 주제. Spring 소스를 한 줄도 바꾸지 않고 정책을
# 걸었다는 주장은 이 검사 없이는 스크립트 안에서 증명되지 않는다.
echo "[9] git diff -- Spring 소스 변경 0줄 (이 슬라이스의 주제, 비교 기준 edb6f2d)"
REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null)
if [ -n "$REPO_ROOT" ]; then
  # pathspec 주의. git 의 기본 wildmatch 는 패턴이 경로 '전체' 와 맞아야 하므로
  # '.../*/src' 는 '.../token/src/main/java/...java' 와 매칭되지 않는다(0개 매칭 =
  # 무엇을 바꿔도 통과하는 위양성). 반드시 '*/src/*' 로 끝까지 열어둬야 한다.
  # 비교 대상도 커밋(edb6f2d..HEAD)이 아니라 워킹트리로 둔다 — 커밋하지 않은 src 수정까지 잡는다.
  SRC_DIFF=$(git -C "$REPO_ROOT" diff --stat edb6f2d -- \
    'oauth-2/authorization-server/practice/microservice/*/src/*')
  check "Spring 소스(*/src) 변경이 없다" "" "$SRC_DIFF"
  [ -n "$SRC_DIFF" ] && echo "$SRC_DIFF"
else
  echo "  FAIL  git 저장소 루트를 찾지 못했다"
  FAIL=$((FAIL+1))
fi

echo
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
