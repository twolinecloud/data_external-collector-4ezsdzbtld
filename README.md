# external-collector-4ezsdzbtld RESTful API Service

##1. Environment
- Maven 3.0 이상
- JAVA 1.8
- SpringBoot 3.2

##2. Project Run
- Run Server

    `{SRC_ROOT}$ mvn spring-boot:run`

##3. Project Build
- Clean: 

    `{SRC_ROOT}$ mvn clean`

- Build Package

    `{SRC_ROOT}$ mvn package`
    
    `{SRC_ROOT}$ mvn package -Prelease`
    

## 관리자 서비스(admin-api) 연동 - 바로 실행 / 스케줄 즉시 반영

관리자 화면의 [바로 실행]과 스케줄 변경 즉시 반영을 받는 내부 API다. 정형(data-collector)·비정형 수집기와 같은 형식을 쓴다.
호출 주소는 관리자 DB `TB_BATCH_SCHEDULE_SETTING`의 **행별 URL**이고, 코드는 `AdminLinkController`다.

### 엔드포인트

| 기능 | 호출 | 응답 |
|---|---|---|
| 바로 실행 | `POST /internal/collect/external-incremental/{operationKey}?triggerBy={관리자ID}&to={무시}` (본문 없음) | **202** `{operationKey, dataTypeCd, execId}` |
| 스케줄 즉시 반영 | `POST /internal/schedule/refresh` JSON `{dataTypeCd, schdCd?, activeYn, execSchedTypeCd, schedVal}` | 200 |

#### 바로 실행
- `{operationKey}`는 오퍼레이션 키(`kma-asos-hourly`)나 스케줄 코드(`KMA_ASOS_HOURLY`) 어느 쪽이든 된다.
- **스케줄 1틱과 똑같이 실행**한다 - 위치별 오퍼레이션은 59개소 전체, 생활기상지수는 16개 시도 전체, 법령은 법령+행정규칙 전체. 수집 뒤 정제·적재까지 같은 execId에 이어붙인다.
- 오래 걸려서 **배치(execId)만 먼저 열어 202로 응답하고 실행은 백그라운드**에서 한다(관리자 서비스 읽기 타임아웃 3초). 진행·결과는 배치 수행 이력에서 본다.
- `triggerBy`는 필수(없으면 400)이며 이력의 실행 주체로 남는다. 실행 유형은 `MANUAL`.
- `to`는 받기만 하고 쓰지 않는다 - 날씨·법령은 호출 시점의 최신 값을 가져온다.
- 스케줄 사용 여부와 **무관하게** 실행한다. 같은 오퍼레이션의 바로 실행이 진행 중이면 **409**(연타 방지). 모르는 오퍼레이션은 404.
- 스케줄러가 돌리는 도중에 같은 오퍼레이션을 바로 실행하면 겹쳐서 돈다(자동 스케줄은 막지 않는다).

#### 스케줄 즉시 반영
- **사용 여부(`activeYn`)만 반영**한다. 실행 시각(`execSchedTypeCd`/`schedVal`)은 받아서 로그만 남기고 `application.yml`의 cron을 그대로 따른다.
  관리자 DB의 시각 값이 오퍼레이션별 실제 발표주기와 달라서 시각까지 따르면 수집 시각이 어긋난다.
- 사용 중지한 오퍼레이션은 스케줄 틱을 건너뛴다(`[SCHEDULE] 사용 중지된 스케줄이라 이번 틱을 건너뜀`).
- 사용 여부 판단 순서: ① 그 오퍼레이션의 `SCHD_CD` 행 → ② 데이터 구분 대표 행(`SCHD_CD` 없음) → ③ 둘 다 없으면 **사용**.
- 호출을 놓쳐도 `ScheduleSettingSync`가 **기동 직후와 5분마다** admin-api의 `GET /api/batch-schedules`(토큰 불필요)로 전체 스케줄을 가져와 따라잡는다 - 정형·비정형 수집기와 같은 방식이다(`public-data.schedule-sync.interval-ms`). 조회에 실패하면 기존 값을 유지한다.
  주소는 `admin-api.base-url`(환경변수 `ADMIN_API_URL`, 기본값은 dev 클러스터 내부 주소)이고, 비어 있으면 가져오지 않는다(로컬은 비어 있음). 관리자 DB를 직접 읽지 않는다.
- 재시작하면 처음 동기화 전까지 모두 사용 상태다(메모리에만 보관).

### 긴급 재처리는 제공하지 않는다

날씨는 호출 시점 기준으로 수집하고 폴링형은 다음 주기가 대체하며, 법령은 매 실행이 최신 전체라서 원래 execId를 다시 처리할 의미가 없다(PL 확인, 2026-10-06).
관리자 DB의 `EXTERNAL_PUBLIC`/`EXTERNAL_LAW` 행의 **`REPROCESS_URL`은 NULL로 둔다** - 그러면 이력 화면의 재처리 버튼이 숨겨지고(`reprocessable=false`) 직접 호출해도 관리자 API가 400으로 거부한다.
실패 복구는 스케줄 화면의 [바로 실행]으로 한다.

### 오퍼레이션 ↔ 스케줄 코드 (`ScheduleCodes`)

`SCHD_CD`는 관리자 DB 행의 키라 **한 번 정하면 바꾸지 않는다**(형식 `^[A-Z][A-Z0-9_]*$`, 최대 30자 - 오퍼레이션 키가 최대 35자라 변환이 아니라 명시적으로 정한 코드다).

| operationKey | SCHD_CD | 설명 |
|---|---|---|
| `kma-village-forecast-ultra-srt-ncst` | `KMA_SRT_NCST` | 초단기실황 (지역 59개소 순회) |
| `kma-village-forecast-ultra-srt-fcst` | `KMA_SRT_FCST` | 초단기예보 (59개소) |
| `kma-village-forecast-vilage-fcst` | `KMA_VILAGE_FCST` | 단기예보 (59개소) |
| `kma-weather-warning-list` | `KMA_WRN_LIST` | 기상특보 목록 |
| `kma-asos-hourly` | `KMA_ASOS_HOURLY` | 지상관측(ASOS) 시간자료 |
| `airkorea-realtime-measure` | `AIRKOREA_REALTIME` | 에어코리아 실시간 대기오염 |
| `airkorea-dust-forecast` | `AIRKOREA_DUST_FCST` | 대기질예보통보(황사) |
| `moleg-criminal-law` | `MOLEG_LAW` | 법제처 법령 + 행정규칙 (EXTERNAL_LAW) |
| `safetydata-disaster-msg-list` | `SAFETY_DISASTER_MSG` | 긴급재난문자 |
| `kma-living-uv-idx` | `KMA_LIVING_UV` | 자외선지수 (16개 시도) |
| `kma-living-air-diffusion-idx` | `KMA_LIVING_AIR_DIFF` | 대기정체지수 (16개 시도) |

새 오퍼레이션을 추가하면 `ScheduledOperations`(실행 묶음)와 `ScheduleCodes`(코드)에 함께 등록한다. 둘이 어긋나면 `PublicDataCollectorSchedulerTest`가 실패한다.

### 관리자 DB 설정 (배포 후)

세부 스케줄 행의 `INSERT`는 **이 수집기가 배포된 뒤에** 한다. 행은 오퍼레이션별로 추가한다. **공공 연계(`EXTERNAL_PUBLIC`)는 대표 행(`SCHD_CD` NULL)을 두지 않는다**(재처리를 제공하지 않고 오퍼레이션이 각자 행을 가져 필요 없다 - dev 는 삭제함). 법령(`EXTERNAL_LAW`)은 세부 행 없이 대표 행 하나가 실제 스케줄이다.

- `REFRESH_URL` = `http://external-collector-4ezsdzbtld.data-pipeline:8080/internal/schedule/refresh` (모든 행 공통)
- `RUN_URL` = `http://external-collector-4ezsdzbtld.data-pipeline:8080/internal/collect/external-incremental/{operationKey}`
  (법령은 대표 행에 `.../moleg-criminal-law`를 넣는다)
- `REPROCESS_URL` = NULL

## 법령 Xenon(GenOS) 전송

정제가 끝난 법령·행정규칙을 ZIP으로 묶어 Xenon에 보낸다. 법령 체인(수집 → 정제 → **SEND**)의 마지막 단계이고, 로그 컬렉터에는 `SEND` 스텝(stepSeq 3)으로 같은 execId에 이어붙는다. 규약은 `private-doc/xenon_protocol.pdf`. **기본은 꺼짐**이라 켜기 전까지 법령은 예전처럼 정제에서 배치를 닫는다.

| 설정 | 환경변수 | 기본값 | 설명 |
|---|---|---|---|
| `public-data.law-transfer.enabled` | `LAW_TRANSFER_ENABLED` | `false` | 전송 켜기 |
| `...xenon.api-key` | `XENON_API_KEY` | (없음) | Bearer 토큰. **K8s Secret 으로만 주입** |
| `...xenon.base-url` | `XENON_BASE_URL` | 운영 Code Serving 800 | |
| `...max-zip-bytes` | `LAW_TRANSFER_MAX_ZIP_BYTES` | 50MB | ZIP 하나의 상한(압축 후). 넘으면 문서 단위로 나눠 여러 ZIP |
| `...send-all` | `LAW_TRANSFER_SEND_ALL` | `false` | true면 변경 여부와 무관하게 전체 전송 |
| `...include-images` | `LAW_TRANSFER_INCLUDE_IMAGES` | `false` | 별표 GIF 포함 여부(같은 내용의 PDF가 있어 기본 제외) |
| `...ledger-file` | `LAW_TRANSFER_LEDGER_FILE` | (비움) | 보낸 기록 파일. 비우면 메모리만 → 재시작/배포 후 첫 수집에 전체 재전송 |

### ZIP 레이아웃 (`LawArchiveLayout` 한 곳에서만 정한다)

```
law/{법률,시행령,시행규칙}/<법령명>/<법령명>.json
                                   /별표/별표1.hwp, 별표1.pdf, 서식86의2.hwp ...
admrul/{훈령,예규,...}/<규칙명>/<규칙명>.json
                                   /별표/...   /첨부/첨부1.hwp ...
manifest.json   (문서별 법령ID·종류·시행일·sha256·첨부 목록, 건너뛴 첨부와 사유)
```

- 유형은 이름이 아니라 **법종구분**으로 나눈다: 법률(대통령긴급명령 포함)→`법률`, 대통령령→`시행령`, 부령·총리령·대법원규칙→`시행규칙`. 폴더 이름은 법적 의미가 아니라 유형 표시다(상위 법률이 없는 독립 대통령령도 `시행령`).
- 폴더명은 대상 목록(CSV)의 옛 이름이 아니라 **응답의 현행 이름**을 쓴다(정부조직 개편으로 이름이 바뀐 17건). 끝 공백·금지 문자는 정리한다.
- **이 구조는 규약의 "2단계 폴더 = 법령명 1건"과 다르다(유형 폴더가 한 단계 더 있다).** Xenon이 받아주지 않으면 `LawArchiveLayout.documentDir` 만 `law/<법령명>`으로 바꾼다.
- 규약 허용 확장자가 아닌 첨부(행정규칙 `.zip` 5건)는 ZIP에 넣지 않고 manifest 에 사유를 남긴다.

### 동작

- 지난번에 보낸 것과 본문(sha256)이 같은 문서는 보내지 않는다. 보냄 기록은 Xenon 응답(sha256·size_bytes·zip.json·zip.top_level_count)을 대조해 맞았을 때만 남긴다.
- 전송 전에 `GET /api/v1/ping` 으로 인증을 확인한다 - 키가 틀리면 수백 MB를 내려받기 전에 포기한다.
- 첨부 다운로드가 일시 실패(5xx/타임아웃)한 문서는 통째로 이번 전송에서 빠지고 다음 수집에 다시 시도한다. 404/410은 그 파일만 건너뛴다.
- 대조에서 어긋나면 `max-attempts`(3)까지 재전송한다(같은 파일 중복 전송은 규약상 무해).
- 첫 전송은 전체(약 320MB, 첨부 다운로드 약 6,900회)라 오래 걸린다. 이후는 변경분만이다.
- **첫 실전 전송에서 확인할 것**: `zip.json` 이 `manifest.json` 을 세는지, `top_level_count` 가 폴더만 세는지 규약에 없다. 지금은 두 경우를 모두 맞는 것으로 보고 어느 쪽인지 INFO 로 남긴다.
