#!/usr/bin/env bash
#
# 기존 템플릿 수정 반영 스크립트
#
# 무엇을 하는가:
#   seed-data/templates의 수정된 JSON을 기존 DB 템플릿에
#   새 버전으로 반영합니다.
#
# 주의:
#   PUT /api/templates/{templateId}는 기존 버전을 수정하지 않고
#   새 버전을 생성합니다. 따라서 이 스크립트를 다시 실행하면
#   버전이 계속 증가합니다.
#
# 필요 도구:
#   curl
#
# 사용:
#   ./update-templates.sh
#   TEMPLATE_API_BASE_URL=http://localhost:8080 ./update-templates.sh
#

set -euo pipefail

BASE_URL="${TEMPLATE_API_BASE_URL:-http://localhost:8080}"
ENDPOINT="${BASE_URL}/api/templates"

FILES=(
  "A1-recruitment-1x1.json"
  "A2-recruitment-4x5.json"
  "B1-event-1x1.json"
  "B2-event-4x5.json"
  "C1-news-1x1.json"
  "C2-news-4x5.json"
  "D1-quote-1x1.json"
  "D2-quote-4x5.json"
)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v curl >/dev/null 2>&1; then
  echo "오류: curl이 설치되어 있어야 합니다."
  exit 1
fi

# jq 없이 GET /api/templates 응답에서 활성 템플릿의 ID를 찾습니다.
#
# 현재 TemplateResponse는 다음 순서로 직렬화됩니다.
#   {"id":...,"code":"A1",...,"isActive":true,"version":...}
#
# Jackson의 기본 JSON 출력은 한 줄이므로 top-level response 객체의
# {"id": 시작 지점을 줄바꿈한 뒤 code와 isActive가 같은 객체에 있는지
# grep으로 확인합니다.
find_active_template_id() {
  local response="$1"
  local code="$2"

  printf '%s' "$response" |
    sed 's/{"id":/\n{"id":/g' |
    grep "\"code\":\"${code}\"" |
    grep '"isActive":true' |
    sed -n 's/.*"id":\([0-9][0-9]*\).*/\1/p' |
    head -n 1
}

echo "대상 서버: ${ENDPOINT}"
echo "기존 활성 템플릿을 조회합니다."
echo ""

template_response="$(curl -fsS "${ENDPOINT}")"

for file in "${FILES[@]}"; do
  filepath="${SCRIPT_DIR}/${file}"

  if [[ ! -f "$filepath" ]]; then
    echo "[건너뜀] 파일을 찾을 수 없습니다: ${filepath}"
    continue
  fi

  code="${file%%-*}"

  template_id="$(find_active_template_id "$template_response" "$code")"

  if [[ -z "$template_id" ]]; then
    echo "[실패] 활성 템플릿을 찾을 수 없습니다: code=${code}"
    echo "       먼저 seed-templates.sh로 신규 템플릿을 등록했는지 확인해주세요."
    exit 1
  fi

  echo "-> 새 버전 생성 중: ${file} (code=${code}, currentId=${template_id})"

  response="$(
    curl -sS -w "\n%{http_code}" \
      -X PUT "${ENDPOINT}/${template_id}" \
      -H "Content-Type: application/json" \
      --data-binary @"${filepath}"
  )"

  http_code="$(echo "$response" | tail -n 1)"
  body="$(echo "$response" | sed '$d')"

  if [[ "$http_code" == "200" ]]; then
    echo "   성공 (HTTP 200)"
  else
    echo "   실패 (HTTP ${http_code})"
    echo "   응답: ${body}"
    exit 1
  fi
done

echo ""
echo "완료. 수정된 템플릿은 새 버전으로 생성되었습니다."
echo "활성 템플릿 확인:"
echo "  curl ${ENDPOINT}"
