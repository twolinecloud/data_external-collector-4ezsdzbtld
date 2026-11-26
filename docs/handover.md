# 인수인계 개요

## 1. 하는 일
```
스케줄러 → 수집(collector) → 정제(cleanser) → 적재(loader) → admin-db
                                     └ 법령만: 전송(transfer) → Genon
```
- 코드 위치: `src/main/java/egovframework/external/publicdata/{collector,cleanser,loader,scheduler,transfer,source}`
- 수집기마다 `PublicDataCollector` 구현체가 하나다(전략 패턴). 수집 결과는 `raw_staging`(메모리)에 쌓였다가 정제, 적재로 넘어간다. 파드가 재시작되면 `raw_staging`은 비지만 DB 데이터는 영향이 없다.
- 외부 API 호출 1회는 `publicdata/source/ExternalApiPort`로 분리돼 있다(ESB 전환용, [esb-migration.md](esb-migration.md)). 법령 본문은 `LawSourcePort`다.

## 2. 수집 대상과 스케줄
cron은 `application.yml`의 `public-data.collector.*`에 있고 **KST 기준**이다(JVM 시간대가 `Main.java`에서 Asia/Seoul로 고정). 로그 타임스탬프만 UTC다.

| 오퍼레이션 | 스케줄 코드 | 주기(대략) | 제공처 |
|---|---|---|---|
| 초단기실황 / 초단기예보 / 단기예보 (59개소) | KMA_SRT_NCST / KMA_SRT_FCST / KMA_VILAGE_FCST | 매시 12분 / 매시 47분 / 02,05,...,23시 15분 | 공공데이터포털(기상청) |
| 기상특보 목록 | KMA_WRN_LIST | 10분 | 공공데이터포털(기상청) |
| ASOS 시간자료 | KMA_ASOS_HOURLY | 매시 25분 | 기상청 API허브 |
| 에어코리아 실시간 / 황사예보 | AIRKOREA_REALTIME / AIRKOREA_DUST_FCST | 매시 20분 / 5,11,17,23시 15분 | 공공데이터포털(환경공단) |
| 생활기상지수(자외선, 대기정체) | KMA_LIVING_UV / KMA_LIVING_AIR_DIFF | 3시간마다 10분(00,03,...,21시) | 공공데이터포털(기상청) |
| 긴급재난문자 | SAFETY_DISASTER_MSG | 10분 | 재난안전데이터공유플랫폼 |
| 법령 + 행정규칙 (약 495건) | MOLEG_LAW | 매일 05:00, 끝에 Genon 전송 | 법제처 |
| 특보구역 관측소 갱신 | - | 매일 04:40 | 기상청 API허브 |
| 교정시설 마스터 대조 | - | 매일 04:00 | admin-db `tb_dim_instt` |

- 정확한 값은 `application.yml`과 `PublicDataCollectorScheduler`를 본다. 새 오퍼레이션을 추가하면 `ScheduledOperations`와 `ScheduleCodes`에 함께 등록한다(어긋나면 `PublicDataCollectorSchedulerTest` 실패).
- 관리자 화면의 "바로 실행"과 스케줄 즉시 반영 API는 루트 `README.md`에 있다. 주기 실행 시각은 화면에서 못 바꾸고 cron 수정과 배포로만 바뀐다.

## 3. 빌드와 배포
- Java 17, Spring Boot 3, Maven. `mvn test`로 전체 테스트(현재 738건)를 돌린다.
- **`dev` 브랜치에 push하면 Jenkins 파이프라인이 빌드, 이미지 푸시, 배포까지 한다.** `master`는 쓰지 않는다(08-05 이후 갱신 안 됨).
- ArgoCD는 자동 sync가 꺼져 있다. 배포는 Jenkinsfile 마지막 `Deploy` 단계가 `argocd app sync`를 직접 호출해서 일어난다. 이 단계가 실패하면 HelmChart만 갱신되고 OutOfSync로 멈춘다 → [operations.md](operations.md).
- 배포 확인: 파드 이미지 태그 끝이 커밋 해시와 같은지, `Started Main`과 ERROR 0건을 본다.
- ConfigMap/Secret은 ArgoCD 앱의 관리 대상이 아니다. 값을 바꾸면 `kubectl`로 직접 적용하고 파드를 재시작한다.
- Helm 차트 저장소(`twolinecloud/data_HelmChart`)는 Turaco 패치가 master를 덮어쓴다. 설정 변경은 별도 브랜치(`feature/<서비스>`)에서 관리하고 패치 뒤에 머지한다. master에 직접 머지하지 않는다.

## 4. DB
- admin-db(PostgreSQL), 스키마 `kcais`. 수집 결과는 `tb_ext_*` 테이블, 대상 목록은 `tb_ext_law_target`, 시설 마스터는 `tb_dim_instt`.
- DB 시각은 KST다.
- 날씨 기호용 테이블 3개(`tb_ext_asos_hourly`, `tb_ext_air_quality`, `tb_ext_dust_forecast`)는 **DDL 파일 없이 admin-db에 직접 만들어졌다**. admin-db를 스키마 파일로 재구축하면 이 3개를 따로 만들어야 한다(컬럼은 현재 DB에서 `\d`로 뜬다).
- 폐기 배치(`PUBLIC_DATA_PURGE_ENABLED`)는 운영에서 꺼져 있어 설계상 30일 보존이 적용되지 않고 누적 중이다. 보존기간은 설계(30일)대로 두기로 했다. 켜려면 환경변수를 true로 하고, 날씨와 재난문자를 같은 보존기간으로 자르는 점(`PublicDataPurgeService`가 단일 `retention-days` 사용)을 먼저 본다.
- 접속: `kubectl port-forward svc/admin-db-fy9tjq4tsk -n service-core 15432:5432` 후 psql. 사용 후 port-forward를 종료한다.

## 5. 확정된 결정 (2026-11-18 기준)
- **교정시설 마스터는 `tb_dim_instt`**다. `tb_dim_instt_v2`는 폐기한다. admin-api 전환은 2026-11-18 dev에 배포됐고, v2의 위경도, 지형, 상세주소를 마스터로 복사했다(백업 `tb_dim_instt_bak_20261118`, 스크립트는 admin-api 저장소 `scripts/restore-dim-instt-geo-from-v2-20261118.sql`). 마스터의 `terrain_info`는 시설 59곳에 채워져 있고 지방교정청 4곳과 상위 기관은 비어 있는 것이 정상이다. 수집기 후속은 아래 6절.
- **주의**: 정형 "초기적재"(data-collector)가 다시 돌면 마스터의 위경도, 지형, `detail_addr`, `last_sync_addr`가 지워진다(11-08에 실제로 발생). 초기적재가 이 컬럼을 덮어쓰지 않도록 고치기 전에는 돌리지 않는다. 보라미 증분 동기화는 이 컬럼을 건드리지 않는다.
- **신규 시설의 초기 위경도**는 사용자가 admin-fe에 Google Maps URL을 입력하면 admin-api가 좌표를 추출해 `tb_dim_instt`에 넣는다. 해발, 지형은 OpenTopoData(SRTM)로 계산한다. VWorld 자동 지오코딩은 성공률이 19%라 쓰지 않는다.
- **안개, 박무, 연무 판정 기준**은 현재 코드(시정과 습도로 유도, `KmaAsosHourlyCleanser`)로 확정이다.
- **Genon 전송 형식은 변경하지 않는다**: manifest 필드, ZIP 폴더 구조, 파일 이름 규칙, 본문 JSON 원문 전송. 전송 관련 리팩터링은 출력이 바이트 단위로 같아야 한다. 구조를 바꿔야 할 것 같은 요구가 나오면 구현 전에 확인한다.
- **ESB는 HTTP gateway 방식**이고 IF_ID는 메타빌드가 정해 주기로 했다(회신 전).
- 기상특보 해상 오매칭 과거 행(08-24~09-22, 5,452건)은 삭제하지 않고 둔다.

## 6. 남은 후속 작업
1. ESB 전환([esb-migration.md](esb-migration.md)).
2. 시설 취약도 원천 전환은 **완료**(2026-11-18 `4c965c0` 배포·기동 확인: 이미지 태그 `...-4c965c0`, `Started Main`, ERROR 0, DB 조회 실패 경고 없음): 룰 평가(`RuleEvaluationService`)가 `tb_dim_instt.terrain_info.vulnerability`를 읽는다(`DbFacilityVulnerabilitySource`). DB에 값이 없는 시설은 `facility-vulnerability.csv` 값으로 대체되고, 조회가 실패하면 CSV 전체를 쓴다. 롤백은 `PUBLIC_DATA_FACILITY_VULNERABILITY_SOURCE=csv`. 전환 전 DB와 CSV는 59곳 모두 같았고, 풍랑은 마스터에 없어 전 시설 MEDIUM(CSV 값)이다. 룰 평가는 현재 `RuleEvaluationController` API로만 호출된다(스케줄 없음).
   - 전환 때 DB에서 시설을 더 이상 쓰지 않게 되면 CSV와 `FacilityVulnerabilityLoader`를 제거할 수 있다.
3. **VWorld 제거(향후)**: 당분간 유지한다. 자동 지오코딩은 성공률 19%라 쓸모가 없고 신규 좌표는 Google Maps URL 입력(admin-api)으로 대체됐으므로 `VWorldGeocoder`, `FacilitySyncService`의 자동 지오코딩 경로, `VWORLD_API_KEY`는 나중에 제거한다.
4. 코드, 설정 주석의 낡은 문구 정리(안개 기준 "확정 대기", 루트 README의 환경 표기)는 **완료**(`a088dbc`).

## 6-1. 2026-11-18~20 작업 이력(dev 반영 완료)
| 커밋 | 내용 |
|---|---|
| `0fd6a48` | 법령 구조 드리프트 감지에서 정상 변이 필드(`별표`, `조문제개정유형`, `조문제목` 등)를 경고하지 않도록 정리. `StructureProbe`에 `skipMissingWhenNoneObserved` 추가. 이제 STRUCTURE-DRIFT 경고는 실제 구조 변화일 때만 나온다. |
| `bae4a4a`, `12066e0` | 이관 문서(`docs/`) 추가, tb_dim_instt 전환 배포 상태 반영. |
| `4c965c0` | 시설 취약도 원천을 `facility-vulnerability.csv`에서 `tb_dim_instt.terrain_info`로 전환(6장 2번 항목). |
| `6db7702` | 스캐폴드 로그인 샘플 제거(`LoginController`, `LoginService`, `EmailAuth`), Swagger 소개문 교체. |
| `2318b7d` | 법령 구조 프로브가 조문이 1개뿐인 법령(`조문단위`가 배열이 아닌 객체)도 관찰하도록 수정(`MolegCriminalLawCleanser`), 배열·객체·혼합·없음 케이스 테스트 추가. 수정 전에는 객체를 만나면 같은 수집분의 나머지 법령 관찰이 중단되고 `프로브 관찰 실패` 경고가 나왔다(11-19 법령 수집에서 16건). 정제와 Genon 전송에는 영향이 없다. |
| `cb8161f` | 프로브 오탐 제거: 조문단위가 전문 원소 하나뿐인 규칙은 조문 프로브가 누락 경고를 내지 않고, `부칙`을 선택 필드로 옮겼다(마약류 관리에 관한 법률 시행규칙에 부칙이 없음). |
| (이전 push) | ESB 선행 작업: `ExternalApiPort` 라우터, 응답 비교기([esb-migration.md](esb-migration.md)). |

배포 후 확인: 파드 `Running 1/1`, 재시작 0회, 기동 4.9초, admin-db(Hikari) 연결 정상. 룰 평가가 DB 값으로 실제 호출되는 시점은 API 호출 때라 별도 관측하지 않았다.

**2026-11-19 운영 키 교체**: 공공데이터포털, 기상청 API허브, 재난문자, 법제처 OC를 새 키로 바꿨다. Secret 반영, 파드 재시작, 사전 검증 결과는 [credentials.md](credentials.md#키-교체-이력)에 있다.

**2026-11-24 Secret 되돌려짐**: 새 키를 `kubectl patch`로 반영했더니 배포 때마다 이전 키로 되돌아갔다. Jenkins Deploy가 HelmChart `master`의 `Common` 차트(이전 키 포함)를 통째로 적용하기 때문이며, `Common`을 쓰는 어느 서비스의 배포든 해당된다. `master`의 Secret 템플릿을 새 키로 고쳐(`e01264e`) 해결했다. 키를 바꿀 때는 클러스터와 `master` 파일을 함께 바꾼다. 자세한 내용은 [credentials.md](credentials.md#2026-11-1924-secret이-이전-키로-되돌려진-사건)에 있다.

## 7. 알려진 한계
- 재난문자 API는 "오늘" 날짜만 조회한다. 업스트림이 장애 후 소급 반영하면(11-14, 11-15 사례) 그 날짜분이 DB에 빠진다. 해당 두 날짜는 복구하지 않았다.
- ASOS 시간자료는 매시 25분에 직전 정시 자료를 받는다. 그 실행이 API허브 타임아웃으로 3회 모두 실패하면 해당 시간분이 비고 다음 실행이 채워 주지 않는다(2026-11-20 04:00 KST분 지점 59곳 누락, 백필하지 않기로 함). 필요하면 `POST /public-data/collect/backfill/{operationKey}`로 채운다.
- 같은 날짜 재난문자 호출이 일시적으로 HTML 에러 페이지(HTTP 200)를 주기도 한다. 다음 폴링에서 회복된다.
- `tb_ext_disaster_msg`에는 시설과 매칭된 행만 저장된다(하루 4~7행 수준).
- Genon `mst`는 11-16에 manifest에 추가했다. 이미 보낸 문서는 해시가 같아 소급 전송하지 않았고, 운영에서 mst가 실려 나간 전송은 아직 관측하지 못했다(법령이 실제로 바뀔 때 확인). 전체 재전송이 필요하면 `LAW_TRANSFER_SEND_ALL=true`로 한 번 보낸다(약 400MB).
- VWorld, OpenTopoData 호출은 ESB 대상에서 제외될 수 있다(OpenTopoData는 키 없는 공개 API).
