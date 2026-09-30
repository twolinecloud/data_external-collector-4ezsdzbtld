#!/usr/bin/env bash
# 운영(개발계) external-collector의 Secret/ConfigMap이 비워지지 않았는지 점검한다.
#
# 배경: Turaco/Jenkins의 배포 작업으로 data-pipeline 네임스페이스의 external-collector Secret·
# ConfigMap이 키 0개로 덮어써지는 일이 반복됐다(2026-09-14, 09-29, 09-30). 파드는 Running/Ready라
# 겉으로 정상처럼 보이고, 기동 때 읽어 간 값으로 계속 돌다가 재시작되는 순간 수집이 전부 멈춘다.
#
# 점검 항목 (값은 절대 출력하지 않고 키 이름과 유무만 본다):
#   1. Secret/ConfigMap에 기대하는 키가 전부 있고 값이 비어 있지 않은지
#   2. 지금 떠 있는 파드가 그 키들을 환경변수로 갖고 있는지
#      (Secret은 멀쩡한데 파드만 빈 설정으로 뜬 경우 - 파드 재시작이 필요)
#
# 이 스크립트는 읽기 전용이다. 복구(kubectl apply)는 하지 않는다 - 운영 Secret 쓰기는 사람이 직접
# 하도록 마지막에 복구 절차만 안내한다.
#
# 종료 코드: 0 정상 / 1 Secret·ConfigMap에 키 누락 / 2 파드에 키 누락(재시작 필요) / 3 kubectl 조회 실패
#
# 사용법:
#   scripts/check-external-config.sh                 # 1회 점검
#   scripts/check-external-config.sh --watch         # 60초마다 반복, 이상이 생길 때만 알림(macOS 알림 포함)
#   scripts/check-external-config.sh --watch 30      # 30초마다
#   NAMESPACE=data-pipeline scripts/check-external-config.sh
#
set -uo pipefail

NAMESPACE="${NAMESPACE:-data-pipeline}"
SECRET_NAME="${SECRET_NAME:-external-collector-secret-toqd7wzduo}"
CONFIGMAP_NAME="${CONFIGMAP_NAME:-external-collector-configmap-kavzag87kf}"
POD_LABEL="${POD_LABEL:-app=external-collector-4ezsdzbtld}"

# 기대하는 키 이름 (pipeline/Common 차트의 external-collector 템플릿 기준, 2026-09-30).
# 키를 추가·삭제하면 여기도 같이 고쳐야 한다.
SECRET_KEYS="ADMIN_DB_PASSWORD ADMIN_DB_USER AIRKOREA_SERVICE_KEY KMA_APIHUB_AUTH_KEY KMA_VILLAGE_FORECAST_SERVICE_KEY KMA_WEATHER_WARNING_SERVICE_KEY LIVING_WTHR_IDX_SERVICE_KEY MOLEG_OC SAFETYDATA_SERVICE_KEY"
CONFIGMAP_KEYS="ADMIN_DB_HOST ADMIN_DB_NAME ADMIN_DB_PORT LOG_COLLECTOR_BASE_URL LOG_COLLECTOR_ENABLED PUBLIC_DATA_FACILITY_MASTER_SOURCE PUBLIC_DATA_FACILITY_SYNC_ENABLED PUBLIC_DATA_LOAD_ENABLED"

WATCH=0
INTERVAL=60
if [[ "${1:-}" == "--watch" ]]; then
  WATCH=1
  INTERVAL="${2:-60}"
fi

log() { echo "[$(date +%H:%M:%S)] $*"; }

# 리소스의 "값이 있는" 키 이름을 한 줄에 공백으로 이어서 출력한다. 조회 실패 시 return 1.
present_keys() {
  local kind="$1" name="$2" json
  json="$(kubectl get "$kind" "$name" -n "$NAMESPACE" -o json 2>/dev/null)" || return 1
  python3 -c '
import sys, json
data = json.load(sys.stdin).get("data") or {}
print(" ".join(sorted(k for k, v in data.items() if v)))
' <<<"$json"
}

# 기대 키 중 실제로 없는 것만 출력한다.
missing_of() {
  local expected="$1" present="$2" key out=""
  for key in $expected; do
    case " $present " in
      *" $key "*) ;;
      *) out="$out $key" ;;
    esac
  done
  echo "${out# }"
}

# 이번 점검 결과를 STATUS(0/1/2/3)와 MESSAGE로 남긴다.
check_once() {
  STATUS=0
  MESSAGE=""

  local secret_present cm_present
  if ! secret_present="$(present_keys secret "$SECRET_NAME")" \
     || ! cm_present="$(present_keys configmap "$CONFIGMAP_NAME")"; then
    STATUS=3
    MESSAGE="kubectl 조회 실패 - 클러스터 접속/컨텍스트를 확인하세요 ($(kubectl config current-context 2>/dev/null))"
    return
  fi

  local secret_missing cm_missing
  secret_missing="$(missing_of "$SECRET_KEYS" "$secret_present")"
  cm_missing="$(missing_of "$CONFIGMAP_KEYS" "$cm_present")"

  if [[ -n "$secret_missing" || -n "$cm_missing" ]]; then
    STATUS=1
    MESSAGE="Secret/ConfigMap 키 누락 - Secret 없음: [${secret_missing:-없음}] / ConfigMap 없음: [${cm_missing:-없음}]"
    return
  fi

  local pod pod_env pod_missing
  pod="$(kubectl get pods -n "$NAMESPACE" -l "$POD_LABEL" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)"
  if [[ -z "$pod" ]]; then
    STATUS=3
    MESSAGE="external-collector 파드를 찾지 못함 (label $POD_LABEL)"
    return
  fi
  pod_env="$(kubectl exec -n "$NAMESPACE" "$pod" -- sh -c 'env | cut -d= -f1' 2>/dev/null | tr '\n' ' ' || true)"
  if [[ -z "${pod_env// /}" ]]; then
    STATUS=3
    MESSAGE="파드($pod) 환경변수 조회 실패 - 기동 중이거나 exec 권한 문제일 수 있음"
    return
  fi
  pod_missing="$(missing_of "$SECRET_KEYS $CONFIGMAP_KEYS" "$pod_env")"
  if [[ -n "$pod_missing" ]]; then
    STATUS=2
    MESSAGE="Secret/ConfigMap은 정상인데 파드($pod)가 빈 설정으로 떠 있음 - 파드 재시작 필요. 파드에 없는 키: [$pod_missing]"
    return
  fi

  MESSAGE="정상 - Secret $(wc -w <<<"$SECRET_KEYS" | tr -d ' ')키, ConfigMap $(wc -w <<<"$CONFIGMAP_KEYS" | tr -d ' ')키, 파드 $pod 가 모두 보유"
}

print_restore_guide() {
  cat <<EOF

복구 절차 (운영 Secret 쓰기라 사람이 직접 실행):
  1. data_HelmChart 저장소의 feature/external-collector 브랜치에서
     pipeline/Common/templates/external-collector-{configmap,secret}-*.yaml 을 가져와
     {{ }} 부분(이름/네임스페이스)을 실제 값으로 치환한다.
       이름: ConfigMap=$CONFIGMAP_NAME, Secret=$SECRET_NAME, 네임스페이스=$NAMESPACE
  2. kubectl apply --dry-run=server -f cm.yaml -f secret.yaml 로 먼저 검증한 뒤
     kubectl apply -f cm.yaml -f secret.yaml
  3. 값이 든 임시 파일은 바로 삭제한다.
  4. 파드가 이미 빈 설정으로 떠 있었다면(종료 코드 2 포함) 재시작한다.
       kubectl delete pod -n $NAMESPACE -l $POD_LABEL --wait=false
EOF
}

notify_mac() {
  command -v osascript >/dev/null 2>&1 || return 0
  osascript -e "display notification \"$1\" with title \"external-collector 설정 점검\"" >/dev/null 2>&1 || true
}

if [[ "$WATCH" -eq 0 ]]; then
  check_once
  log "$MESSAGE"
  if [[ "$STATUS" -ne 0 && "$STATUS" -ne 3 ]]; then
    print_restore_guide
  fi
  exit "$STATUS"
fi

log "감시 시작 (${INTERVAL}초 간격, 이상이 생기거나 회복될 때만 출력, Ctrl+C로 종료)"
last=-1
while true; do
  check_once
  if [[ "$STATUS" -ne "$last" ]]; then
    log "$MESSAGE"
    if [[ "$STATUS" -ne 0 ]]; then
      notify_mac "$MESSAGE"
      if [[ "$STATUS" -ne 3 ]]; then
        print_restore_guide
      fi
    fi
    last="$STATUS"
  fi
  sleep "$INTERVAL"
done
