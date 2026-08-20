#!/usr/bin/env bash
# 1회성으로 수집(Collect)+정제(Cleanse)를 강제 실행하고, 그 결과(정제 JSON)를
# private-doc/pipeline-viewer.html 에 임베드해서 브라우저로 바로 볼 수 있게 만든다.
#
# 스케줄(cron)을 기다리지 않고 지금 당장 대표 샘플(날씨 1개 기관 × 4개 오퍼레이션 + 법령 2건)을
# 돌려서 CleansedJsonDropWriter가 떨어뜨린 JSON을 모아 뷰어 HTML을 다시 만든다.
#
# 사전조건:
#   - 로컬 앱이 떠 있어야 함: mvn -s settings.xml spring-boot:run -Dspring-boot.run.profiles=local
#     (local 프로파일은 public-data.cleanse.json-drop.enabled=true 가 기본값으로 켜져 있음 -
#      application-local.yml 참고. 다른 프로파일에서 쓰려면
#      PUBLIC_DATA_CLEANSE_JSON_DROP_ENABLED=true 로 직접 켜야 함)
#
# 사용법:
#   scripts/force-collect-cleanse.sh
#   BASE_URL=http://localhost:8080 FACILITY_ID=1270280 scripts/force-collect-cleanse.sh
#
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
# 1270280 = 대전지방교정청 (공식 교정기관코드, private-doc/CORR_INSTT_CD.csv 기준 - 2026-08-14부터 facilityId가 f301식 자체번호 대신 이 코드를 씀, task-spec 35번 항목). 실 서비스키 라이브 검증에 쓰인 기관(task-spec 27번 항목)
FACILITY_ID="${FACILITY_ID:-1270280}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DROP_DIR="${DROP_DIR:-$REPO_ROOT/private-doc/cleansed-json-drop}"
TEMPLATE="$SCRIPT_DIR/pipeline-viewer.template.html"
OUT_HTML="$REPO_ROOT/private-doc/pipeline-viewer.html"

log() { echo "[force-collect-cleanse] $*"; }

log "1) 서버 확인: ${BASE_URL}"
if ! curl -sf -o /dev/null "${BASE_URL}/public-data/collect"; then
  echo "앱이 응답하지 않습니다. 먼저 로컬로 기동하세요:" >&2
  echo "  mvn -s settings.xml spring-boot:run -Dspring-boot.run.profiles=local" >&2
  exit 1
fi

# 날씨: 오퍼레이션 4개 (위치기반 3개는 FACILITY_ID 접미사, 기상특보는 전국조회라 접미사 없음)
# 법령: 형법(001692)/형사소송법(001671) - private-doc 31번 항목에서 실측 확인된 lawId
# 재난문자: 전국조회라 접미사 없음(safetydata-disaster-msg-list, task-spec 33/DisasterMsgCollector 참고)
WEATHER_KEYS=(
  "kma-village-forecast-ultra-srt-ncst--${FACILITY_ID}"
  "kma-village-forecast-ultra-srt-fcst--${FACILITY_ID}"
  "kma-village-forecast-vilage-fcst--${FACILITY_ID}"
  "kma-weather-warning-list"
)
LAW_KEYS=(
  "moleg-criminal-law--001692"
  "moleg-criminal-law--001671"
)
DISASTER_KEYS=(
  "safetydata-disaster-msg-list"
)

log "2) 수집(Collect) 강제 실행 (${#WEATHER_KEYS[@]}개 날씨 + ${#LAW_KEYS[@]}개 법령 + ${#DISASTER_KEYS[@]}개 재난문자)"
for key in "${WEATHER_KEYS[@]}" "${LAW_KEYS[@]}" "${DISASTER_KEYS[@]}"; do
  echo "   - $key"
  if ! curl -sf -X POST "${BASE_URL}/public-data/collect/${key}/run" -o /dev/null; then
    echo "     실패(계속 진행): $key" >&2
  fi
done

log "3) 정제(Cleanse) 강제 실행"
curl -sf -X POST "${BASE_URL}/public-data/cleanse/run"
echo

log "3-1) Rule-base 재해 알림 평가 강제 실행 (private-doc/terrain-rule-base-spec.md)"
if curl -sf -X POST "${BASE_URL}/public-data/alert/run"; then
  echo
else
  echo "   실패(계속 진행) - rule-alert-result.json은 이전 결과가 남아있을 수 있음" >&2
fi

log "4) 드롭된 JSON 확인: ${DROP_DIR}"
if [ ! -d "$DROP_DIR" ]; then
  echo "드롭 디렉토리가 없습니다 - public-data.cleanse.json-drop.enabled=true 인지 확인하세요" >&2
  exit 1
fi
ls -1 "$DROP_DIR" | sed 's/^/   - /'

log "5) 뷰어 HTML 생성: ${OUT_HTML}"

MANIFEST_JSON=$(cat <<EOF
[
  {"group": "weather", "groupLabel": "🌦 날씨", "label": "초단기실황조회", "kind": "weather-ncst", "collectorKey": "kma-village-forecast-ultra-srt-ncst--${FACILITY_ID}"},
  {"group": "weather", "groupLabel": "🌦 날씨", "label": "초단기예보조회", "kind": "weather-fcst", "collectorKey": "kma-village-forecast-ultra-srt-fcst--${FACILITY_ID}"},
  {"group": "weather", "groupLabel": "🌦 날씨", "label": "단기예보조회", "kind": "weather-fcst", "collectorKey": "kma-village-forecast-vilage-fcst--${FACILITY_ID}"},
  {"group": "weather", "groupLabel": "🌦 날씨", "label": "기상특보목록조회", "kind": "weather-warning", "collectorKey": "kma-weather-warning-list"},
  {"group": "law", "groupLabel": "⚖️ 법령", "label": "형법", "kind": "law", "collectorKey": "moleg-criminal-law--001692"},
  {"group": "law", "groupLabel": "⚖️ 법령", "label": "형사소송법", "kind": "law", "collectorKey": "moleg-criminal-law--001671"},
  {"group": "disaster", "groupLabel": "🚨 재난문자", "label": "긴급재난문자(전국매칭)", "kind": "disaster-msg", "collectorKey": "safetydata-disaster-msg-list"},
  {"group": "alert", "groupLabel": "🔔 Rule 알림", "label": "지형기반 재해 알림(59개소×2재해)", "kind": "rule-alert", "collectorKey": "rule-alert-result"}
]
EOF
)

DROP_DIR="$DROP_DIR" TEMPLATE="$TEMPLATE" OUT_HTML="$OUT_HTML" BASE_URL="$BASE_URL" MANIFEST_JSON="$MANIFEST_JSON" python3 <<'PY'
import json
import os
import re
from datetime import datetime, timezone, timedelta

drop_dir = os.environ["DROP_DIR"]
template_path = os.environ["TEMPLATE"]
out_path = os.environ["OUT_HTML"]
base_url = os.environ["BASE_URL"]
manifest = json.loads(os.environ["MANIFEST_JSON"])

groups = {}
order = []
for entry in manifest:
    gid = entry["group"]
    if gid not in groups:
        groups[gid] = {"id": gid, "label": entry["groupLabel"], "items": []}
        order.append(gid)

    file_path = os.path.join(drop_dir, entry["collectorKey"] + ".json")
    item = {"label": entry["label"], "kind": entry["kind"], "collectorKey": entry["collectorKey"], "found": False, "json": None}
    if os.path.isfile(file_path):
        with open(file_path, encoding="utf-8") as f:
            raw = f.read()
        try:
            item["json"] = json.loads(raw)
            item["found"] = True
        except json.JSONDecodeError as e:
            item["json"] = {"parseError": str(e), "raw": raw[:2000]}
            item["found"] = True
    groups[gid]["items"].append(item)

kst = timezone(timedelta(hours=9))
data = {
    "generatedAt": datetime.now(kst).isoformat(timespec="seconds"),
    "baseUrl": base_url,
    "groups": [groups[gid] for gid in order],
}

# JS <script> 안에 안전하게 임베드 (</script> 조기 종료 방지)
data_js = json.dumps(data, ensure_ascii=False).replace("</", "<\\/")

with open(template_path, encoding="utf-8") as f:
    template = f.read()

marker = "/*__PIPELINE_VIEWER_DATA__*/null"
if marker not in template:
    raise SystemExit(f"템플릿에서 플레이스홀더를 못 찾음: {marker}")

output = template.replace(marker, "/*__PIPELINE_VIEWER_DATA__*/" + data_js)

os.makedirs(os.path.dirname(out_path), exist_ok=True)
with open(out_path, "w", encoding="utf-8") as f:
    f.write(output)

found = sum(1 for g in data["groups"] for it in g["items"] if it["found"])
total = sum(len(g["items"]) for g in data["groups"])
print(f"완료: {found}/{total}건 임베드")
PY

log "완료. 브라우저에서 여세요:"
log "  open '${OUT_HTML}'"
