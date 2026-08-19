#!/usr/bin/env bash
# 反馈回路（diagnosing-bugs Phase 1）：以 admin-console 凭证签名调用
# GET /api/account?page=0&size=10，断言响应不是 403 "管理后台 is not authorized"。
#
# 凭证从 aieducenter-admin/src/main/resources/application-local.yml 读取（不落脚本）。
# 用法：./scripts/repro-management-gate.sh [baseUrl]   # 默认 http://localhost:10001
set -euo pipefail

BASE_URL="${1:-http://localhost:10001}"
ADMIN_LOCAL_YML="$HOME/workspace/aieducenter-admin/src/main/resources/application-local.yml"
KEY="admin-console"
SECRET=$(grep 'api-secret:' "$ADMIN_LOCAL_YML" | head -1 | sed 's/.*api-secret:[[:space:]]*//')

PATH_TO_CALL="/api/account"
QUERY="page=0&size=10"
TIMESTAMP=$(date +%s)
NONCE=$(openssl rand -hex 16)
BODY_DIGEST=$(printf '' | openssl dgst -sha256 -hex | sed 's/^.*= //')

# stringToSign：apiKey/bodyDigest/nonce/timestamp + query 参数，按 key 排序，k=v 用 & 连接
STRING_TO_SIGN=$(printf 'apiKey=%s\nbodyDigest=%s\nnonce=%s\npage=0\nsize=10\ntimestamp=%s\n' \
  "$KEY" "$BODY_DIGEST" "$NONCE" "$TIMESTAMP" | sort | tr '\n' '&' | sed 's/&$//')
SIGN=$(printf '%s' "$STRING_TO_SIGN" | openssl dgst -sha256 -hmac "$SECRET" -hex | sed 's/^.*= //')

HTTP_CODE=$(curl -s -o /tmp/repro-body.json -w '%{http_code}' \
  -H "X-Api-Key: $KEY" \
  -H "X-Timestamp: $TIMESTAMP" \
  -H "X-Nonce: $NONCE" \
  -H "X-Body-Digest: $BODY_DIGEST" \
  -H "X-Sign: $SIGN" \
  "$BASE_URL$PATH_TO_CALL?$QUERY")

echo "HTTP $HTTP_CODE"
cat /tmp/repro-body.json; echo

# 红：403 + 管理后台 is not authorized（本 bug）。绿：200。
if [ "$HTTP_CODE" = "200" ]; then
  echo "GREEN: management gate passed"
  exit 0
fi
echo "RED: management gate rejected the admin-console caller"
exit 1
