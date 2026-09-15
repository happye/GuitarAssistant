#!/usr/bin/env bash
# test-api.sh — LLM 网关冒烟测试（只发请求、打印 HTTP 状态与响应前 500 字符）
# 用法：bash scripts/test-api.sh
# 说明：key 一律从 local.properties 现场读取（grep/sed 提取），不写入本脚本、不打印。
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPS="$ROOT/local.properties"

if [ ! -f "$PROPS" ]; then
  echo "[test-api] 未找到 $PROPS，无法读取 API key" >&2
  exit 1
fi

# 从 local.properties 读单个属性：去行尾 CR（Windows 文件）与两侧引号
read_prop() {
  sed -n "s/^$1=//p" "$PROPS" | head -n1 | tr -d '\r' | sed -e 's/^"//' -e 's/"$//'
}

ARK_KEY="$(read_prop ARK_API_KEY)"
DS_KEY="$(read_prop DEEPSEEK_API_KEY)"

TMP_BODY="$(mktemp)"
trap 'rm -f "$TMP_BODY"' EXIT

# $1=名称 $2=URL $3=model $4=key
call_api() {
  local name="$1" url="$2" model="$3" key="$4"
  echo "=================================================="
  echo "[test-api] $name"
  echo "[test-api] model=$model"
  if [ -z "$key" ]; then
    echo "[test-api] 错误：local.properties 中未找到对应的 key，跳过"
    return 2
  fi
  local body status
  body="$TMP_BODY.$name"
  status="$(curl -sS --max-time 60 \
    -X POST "$url" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $key" \
    -d "{\"model\":\"$model\",\"messages\":[{\"role\":\"user\",\"content\":\"回复OK两个字母即可\"}],\"max_tokens\":16}" \
    -o "$body" -w '%{http_code}' 2>"$body.err")"
  local rc=$?
  if [ $rc -ne 0 ]; then
    echo "[test-api] curl 传输失败（exit=$rc）：$(head -c 500 "$body.err")"
    rm -f "$body" "$body.err"
    return 3
  fi
  rm -f "$body.err"
  echo "[test-api] HTTP 状态：$status"
  echo "[test-api] 响应前 500 字符："
  head -c 500 "$body"
  echo ""
  rm -f "$body"
  return 0
}

fail=0
call_api "火山方舟 Ark" "https://ark.cn-beijing.volces.com/api/v3/chat/completions" "glm-5.3-flash" "$ARK_KEY" || fail=1
call_api "DeepSeek" "https://api.deepseek.com/chat/completions" "deepseek-flash" "$DS_KEY" || fail=1

if [ $fail -ne 0 ]; then
  echo "[test-api] 存在未完成的测试（key 缺失或网络传输失败）"
  exit 1
fi
echo "[test-api] 两个网关均已发完请求（HTTP 状态见上方，2xx 即通）"
exit 0
