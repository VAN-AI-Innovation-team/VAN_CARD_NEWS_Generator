#!/usr/bin/env bash
#
# 템플릿 시드 데이터 등록 스크립트
#
# 무엇을 하는가:
#   이 폴더에 있는 TemplateCreateRequest JSON 파일을
#   POST /api/templates 로 순서대로 전송해 DB에 템플릿을 등록합니다.

set -euo pipefail

BASE_URL="${TEMPLATE_API_BASE_URL:-http://localhost:8080}"
ENDPOINT="${BASE_URL}/api/templates"

# 등록 순서: recruitment(A) -> event(B) -> news(C) -> quote(D)
# 각 유형 내에서는 1:1 -> 4:5 순서
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

echo "대상 서버: ${ENDPOINT}"
echo ""

for file in "${FILES[@]}"; do
  filepath="${SCRIPT_DIR}/${file}"

  if [[ ! -f "$filepath" ]]; then
    echo "[건너뜀] 파일을 찾을 수 없습니다: ${filepath}"
    continue
  fi

  echo "-> 등록 중: ${file}"

  response=$(curl -sS -w "\n%{http_code}" -X POST "${ENDPOINT}" \
    -H "Content-Type: application/json" \
    --data-binary @"${filepath}")

  http_code=$(echo "$response" | tail -n1)
  body=$(echo "$response" | sed '$d')

  if [[ "$http_code" == "201" ]]; then
    code=$(echo "$body" | grep -o '"code":"[^"]*"' | head -1)
    echo "   성공 (HTTP 201) - ${code}"
  else
    echo "   실패 (HTTP ${http_code})"
    echo "   응답: ${body}"
    echo ""
    echo "중단합니다. 위 오류를 확인한 뒤 다시 실행해주세요."
    exit 1
  fi
done

echo ""
echo "완료. 아래 명령으로 전체 목록을 확인할 수 있습니다:"
echo "  curl ${ENDPOINT} | jq"
