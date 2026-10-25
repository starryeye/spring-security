#!/usr/bin/env bash
# 로그인 → 인가(PKCE) → 동의 → code 교환 → (선택) 로그아웃 을 curl 로 재현한다. 모든 요청은 gateway(:9000).
#
# 사용:
#   ./e2e-login-logout.sh login  <jar>   # 로그인하고 토큰을 받는다. refresh_token 과 sid 를 출력한다
#   ./e2e-login-logout.sh logout <jar>   # 그 쿠키 항아리의 세션으로 로그아웃한다
#
# 주의. 로그인 redirect 의 Location 이 게이트웨이 포트(:9000)를 잃는 알려진 한계(README 참고)가 있어,
#      redirect 를 따라가지 않고(-L 없음) 매번 절대 URL 로 다음 요청을 보낸다.
# 주의. 로그인 폼의 _csrf, 동의 화면의 pending_id·_csrf 는 응답 본문에서 매번 새로 읽는다.
# 주의. code 교환은 token-state 가 refresh token 을 발급해야 끝난다(offline_access). token-state 가 내려가 있으면
#      교환이 server_error 로 실패하므로, token-state 를 내린 채 로그아웃하려면 로그인을 먼저 하고 내린다.
# 주의. curl 연결 실패(gateway 다운)나 _csrf·pending_id 를 못 읽은 grep 실패는 set -e 만으로는 메시지 없이 끝나므로, ERR trap 이
#      실패한 명령(BASH_COMMAND)과 행 번호를 stderr 에 남긴다. macOS 기본 bash 3.2 에서는 ERR trap 의 $LINENO 가 한두 행 어긋날 수
#      있어, 실패를 가리키는 것은 행 번호가 아니라 명령 텍스트다. BASH_COMMAND 는 변수를 펼치기 전의 원문이라 자격 증명 값이 찍히지 않는다.
set -euo pipefail
trap 'echo "실패: $LINENO 행 ($BASH_COMMAND)" >&2' ERR

BASE=http://localhost:9000
CMD=${1:?login|logout}
JAR=${2:?cookie jar path}
REDIRECT_URI=http://127.0.0.1:8080/callback
BASIC=bXktY2xpZW50OnNlY3JldA==   # my-client:secret (seed)

csrf() { grep -o 'name="_csrf"[^>]*value="[^"]*"' | sed -E 's/.*value="([^"]*)".*/\1/' | head -1; }

if [ "$CMD" = "logout" ]; then
  curl -s -o /dev/null -w '%{http_code}\n' -b "$JAR" -c "$JAR" "$BASE/oauth2/logout"
  exit 0
fi

rm -f "$JAR"

# 1. 로그인
TOKEN=$(curl -s -b "$JAR" -c "$JAR" "$BASE/login" | csrf)
curl -s -o /dev/null -b "$JAR" -c "$JAR" -X POST "$BASE/login" \
  --data-urlencode "username=user" --data-urlencode "password=1111" --data-urlencode "_csrf=$TOKEN"

# 2. 인가 요청 (PKCE S256)
VERIFIER=$(openssl rand -base64 48 | tr -d '=+/\n' | cut -c1-64)
CHALLENGE=$(printf '%s' "$VERIFIER" | openssl dgst -sha256 -binary | openssl base64 | tr '+/' '-_' | tr -d '=\n')
AUTHZ="$BASE/oauth2/authorize?response_type=code&client_id=my-client&redirect_uri=$REDIRECT_URI"
AUTHZ="$AUTHZ&scope=openid%20profile%20email%20offline_access&state=s1&nonce=n1"
AUTHZ="$AUTHZ&code_challenge=$CHALLENGE&code_challenge_method=S256"
RESP=$(curl -s -i -b "$JAR" -c "$JAR" "$AUTHZ")

# 3. 동의 화면이 나오면 제출한다(이미 같은 scope 를 승인한 세션이면 바로 302 로 code 가 온다)
LOCATION=$(printf '%s' "$RESP" | grep -i '^location:' | tr -d '\r' | sed 's/^[Ll]ocation: //' || true)
if [ -z "$LOCATION" ]; then
  PENDING=$(printf '%s' "$RESP" | grep -o 'name="pending_id"[^>]*value="[^"]*"' | sed -E 's/.*value="([^"]*)".*/\1/')
  CTOKEN=$(printf '%s' "$RESP" | csrf)
  LOCATION=$(curl -s -i -b "$JAR" -c "$JAR" -X POST "$BASE/oauth2/consent" \
      --data-urlencode "pending_id=$PENDING" --data-urlencode "_csrf=$CTOKEN" \
      --data-urlencode "scope=openid" --data-urlencode "scope=profile" \
      --data-urlencode "scope=email" --data-urlencode "scope=offline_access" \
    | grep -i '^location:' | tr -d '\r' | sed 's/^[Ll]ocation: //' || true)
fi
CODE=$(printf '%s' "$LOCATION" | sed -E 's/.*[?&]code=([^&]*).*/\1/')
case "$LOCATION" in *code=*) ;; *) echo "code 를 받지 못했다. Location='$LOCATION'" >&2; exit 1;; esac

# 4. code 교환
TOKENS=$(curl -s -X POST "$BASE/oauth2/token" -H "Authorization: Basic $BASIC" \
  --data-urlencode "grant_type=authorization_code" --data-urlencode "code=$CODE" \
  --data-urlencode "redirect_uri=$REDIRECT_URI" --data-urlencode "code_verifier=$VERIFIER")
case "$TOKENS" in *'"refresh_token"'*) ;; *) echo "토큰 교환 실패: $TOKENS" >&2; exit 1;; esac
REFRESH=$(printf '%s' "$TOKENS" | python3 -c 'import sys,json; print(json.load(sys.stdin)["refresh_token"])')
ID_TOKEN=$(printf '%s' "$TOKENS" | python3 -c 'import sys,json; print(json.load(sys.stdin)["id_token"])')
SID=$(printf '%s' "$ID_TOKEN" | cut -d. -f2 | tr '_-' '/+' | python3 -c \
  'import sys,base64,json; s=sys.stdin.read().strip(); s+="="*(-len(s)%4); print(json.loads(base64.b64decode(s))["sid"])')

echo "refresh_token=$REFRESH"
echo "sid=$SID"
