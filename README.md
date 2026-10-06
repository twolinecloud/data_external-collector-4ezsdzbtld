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
- 호출을 놓쳐도 `ScheduleSettingSync`가 **기동 직후와 5분마다** admin-db에서 읽어 따라잡는다(`public-data.schedule-sync.interval-ms`). 조회 실패 시에는 기존 값을 유지한다.
  admin-db가 구성되지 않은 환경(`public-data.load.enabled` 등이 모두 꺼진 경우)에서는 이 동기화가 동작하지 않고 즉시 반영 호출로만 갱신된다.
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

세부 스케줄 행의 `INSERT`는 **이 수집기가 배포된 뒤에** 한다. 행은 데이터 구분 대표 행(`SCHD_CD` NULL)을 그대로 두고 오퍼레이션별로 추가한다.

- `REFRESH_URL` = `http://external-collector-4ezsdzbtld.data-pipeline:8080/internal/schedule/refresh` (모든 행 공통)
- `RUN_URL` = `http://external-collector-4ezsdzbtld.data-pipeline:8080/internal/collect/external-incremental/{operationKey}`
  (대표 행에는 넣지 않는다 - 대표 행의 바로 실행은 오퍼레이션을 정할 수 없다)
- `REPROCESS_URL` = NULL
