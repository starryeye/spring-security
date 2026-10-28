#!/usr/bin/env bash
# refresh token 의 회전 · 폐기와 운영자의 이력 조회(슬라이스 9)를 curl 로 재현한다. 로그인 · 로그아웃은 e2e-login-logout.sh 로 한다.
#
# 사용:
#   ./e2e-refresh-audit.sh refresh <refresh_token>        # 회전한다. 성공하면 refresh_token=<새 값>, 실패하면 응답 본문 그대로(종료 코드 1)
#   ./e2e-refresh-audit.sh revoke <refresh_token>         # RFC 7009 폐기. HTTP 상태 코드를 출력한다
#   ./e2e-refresh-audit.sh client-token <client_id> <scope>   # client_credentials 로 access token 을 받아 그 값만 출력한다
#   ./e2e-refresh-audit.sh families <sub> [<bearer>]       # 조회 API. 첫 줄에 상태 코드, 이어서 WWW-Authenticate(있으면), 본문
#
# refresh · revoke · client-token 은 gateway(:9000), families 는 audit(:8089)로 직접 보낸다 — 조회 API 는 gateway 에
# 노출하지 않는다. refresh · revoke 는 my-client 로 인증한다(e2e-login-logout.sh 가 받은 refresh token 의 client).
# client-token 의 secret 은 시드 관례대로 모든 client 가 "secret" 이다(my-client · article-api · audit-operator).
#
# 주의. curl 연결 실패(gateway · audit 다운)나 python3 의 JSON 파싱 실패는 set -e 만으로는 메시지 없이 끝나므로, ERR trap 이
#      실패한 명령(BASH_COMMAND)과 행 번호를 stderr 에 남긴다. macOS 기본 bash 3.2 에서는 ERR trap 의 $LINENO 가 한두 행 어긋날 수
#      있어, 실패를 가리키는 것은 행 번호가 아니라 명령 텍스트다. BASH_COMMAND 는 변수를 펼치기 전의 원문이라
#      $BASIC 같은 변수의 값은 찍히지 않지만, 명령에 리터럴로 적힌 값(client-token 의 "$ID:secret" 에 적힌 시드 secret 같은 값)은
#      그대로 찍힌다.
set -euo pipefail
trap 'echo "실패: $LINENO 행 ($BASH_COMMAND)" >&2' ERR

BASE=http://localhost:9000
AUDIT=http://localhost:8089
CMD=${1:?refresh|revoke|client-token|families}
BASIC=bXktY2xpZW50OnNlY3JldA==   # my-client:secret (seed)

json_field() { python3 -c 'import sys,json; print(json.load(sys.stdin)[sys.argv[1]])' "$1"; }

case "$CMD" in
  refresh)
    TOKEN=${2:?refresh_token}
    RESP=$(curl -s -X POST "$BASE/oauth2/token" -H "Authorization: Basic $BASIC" \
      --data-urlencode "grant_type=refresh_token" --data-urlencode "refresh_token=$TOKEN")
    case "$RESP" in *'"refresh_token"'*) ;; *) echo "$RESP"; exit 1;; esac
    echo "refresh_token=$(printf '%s' "$RESP" | json_field refresh_token)"
    ;;
  revoke)
    TOKEN=${2:?refresh_token}
    curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE/oauth2/revoke" -H "Authorization: Basic $BASIC" \
      --data-urlencode "token=$TOKEN"
    ;;
  client-token)
    ID=${2:?client_id}
    SCOPE=${3:?scope}
    CLIENT_BASIC=$(printf '%s' "$ID:secret" | base64 | tr -d '\n')
    RESP=$(curl -s -X POST "$BASE/oauth2/token" -H "Authorization: Basic $CLIENT_BASIC" \
      --data-urlencode "grant_type=client_credentials" --data-urlencode "scope=$SCOPE")
    case "$RESP" in *'"access_token"'*) ;; *) echo "$RESP"; exit 1;; esac
    printf '%s' "$RESP" | json_field access_token
    ;;
  families)
    SUB=${2:?sub}
    BEARER=${3:-}
    HEADERS=$(mktemp)
    BODY=$(mktemp)
    trap 'rm -f "$HEADERS" "$BODY"' EXIT
    if [ -n "$BEARER" ]; then
      STATUS=$(curl -s -D "$HEADERS" -o "$BODY" -w '%{http_code}' -H "Authorization: Bearer $BEARER" \
        "$AUDIT/audit/users/$SUB/refresh-token-families")
    else
      STATUS=$(curl -s -D "$HEADERS" -o "$BODY" -w '%{http_code}' "$AUDIT/audit/users/$SUB/refresh-token-families")
    fi
    echo "$STATUS"
    grep -i '^www-authenticate:' "$HEADERS" | tr -d '\r' || true
    # 본문이 JSON 이 아니면(빈 본문 포함) 그대로 찍는다
    python3 -m json.tool "$BODY" 2>/dev/null || cat "$BODY"
    ;;
  *)
    echo "모르는 명령: $CMD (refresh|revoke|client-token|families)" >&2
    exit 2
    ;;
esac
