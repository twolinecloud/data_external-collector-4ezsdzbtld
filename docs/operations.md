# 운영 런북

운영하면서 실제로 겪은 장애와 확인 방법이다. 로그 시각은 **UTC**, DB 시각은 **KST**이므로 로그를 볼 때 +9시간부터 한다.

## 기본 확인
- 파드: `kubectl get pods -n data-pipeline | grep external-collector`
- 로그: `kubectl logs <pod> -n data-pipeline --since=26h`
- 수집이 도는지: 테이블별 `max(collect_dtm)`을 본다(`tb_ext_weather_ncst`, `tb_ext_asos_hourly`, `tb_ext_air_quality` 등). 기상특보는 발효 건이 없으면 수집 시각이 멈춰 있는 것이 정상일 수 있다.
- 파드가 Running이어도 수집이 0건일 수 있다(아래 Secret 초기화 사례). 헬스체크만 믿지 않는다.

## 배포 후 반영이 안 될 때 (ArgoCD OutOfSync)
- 증상: dev push 후 HelmChart에는 "Pushed Helm Chart: ...-<커밋>"이 생겼는데 Deployment 이미지가 안 바뀐다.
- 확인: `kubectl -n tlc-support get applications.argoproj.io 428-197-external-collector-4ezsdzbtld`
- 해결: 빈 커밋을 push해 재빌드하거나, Jenkins 재실행, 또는 ArgoCD 화면에서 Sync(이미지는 이미 레지스트리에 있다).
- 2026-10-27 워커 노드 장애로 ingress와 argocd 서버가 재시작되며 sync가 누락된 사례가 있다.

## 수집이 전부 실패 (Secret/ConfigMap이 비워짐)
- Turaco 패치가 `data_HelmChart` master를 템플릿으로 덮어쓰면 클러스터의 ConfigMap, Secret이 빈 값이 될 수 있다(2026-09-14, 09-29, 09-30 사례).
- 파드는 Running인데 수집 성공이 0건이면 먼저 Secret 키 개수와 차트 저장소의 Turaco init 커밋을 확인한다.
- 복구는 클러스터에 직접 `kubectl apply` 후 파드 재시작이었다. 필요한 키는 [credentials.md](credentials.md).

## 법령 수집이 조용히 끊김 (법제처 IP 화이트리스트)
- 법제처 OC는 호출 서버 IP를 법제처에 등록해야 한다. 노드가 추가돼 파드가 다른 노드로 옮겨가면 아웃바운드 IP가 달라져 법령, 행정규칙 수집이 100% 실패한다(2026-09-05~07, 3일간 아무도 몰랐다). 다른 소스는 정상이라 헬스체크도 UP이다.
- 확인: `kubectl exec <pod> -n data-pipeline -- wget -qO- https://api.ipify.org`로 현재 IP를 보고 법제처 등록 목록과 대조한다. 통과 여부는 파드 안에서 `lawService.do?OC=$MOLEG_OC&target=eflaw&LM=형법&type=JSON`을 호출해 본다. 로컬에서 호출하면 로컬 IP로 나가 의미가 없다.
- 이 오류의 응답은 "사용자 정보 검증에 실패"다. 본문 없는 404, 502, 타임아웃은 법제처 일시 장애이고 수집 끝에 3분 간격으로 최대 2차 재시도한다.
- 근본 해결은 NAT Gateway로 아웃바운드 IP를 하나로 고정하는 것(인프라 작업, 미실시).

## 법령 대상 목록 편집
- 대상은 `kcais.tb_ext_law_target`이 기준이다(PK `law_id`, `doc_type_cd`=LAW|ADMIN_RULE). 추가, 수정, 중지는 배포 없이 테이블에서 한다. CSV(`moleg-criminal-laws.csv`)는 로컬, 롤백용이고 테이블과 동기화되지 않는다.
- 사람이 할 일은 둘뿐이다.
  1. 법령명은 법제처 `lawSearch.do` 검색 결과를 복사해서 쓴다(뉴스, PDF에서 베끼지 않는다). `totalCnt`가 0이 아닌지 확인한다.
  2. 시행일자는 공포일이 아니라 효력 발생일로 채운다.
- 행정규칙은 본문 조회 ID가 `law_id`가 아니라 `mst`다.
- "조회가 비었다 = 폐지"가 아니다. 시행일이 미래인 개정본일 수 있으니 시행일부터 본다(2026-12-03 시행 예정 015135 사례).
- `scripts/migrate-law-target-to-db.sql`, `cleanup-law-target.sql`, `add-law-targets.sql`은 기록용이다. 다시 실행하지 않는다(`DELETE`로 이후 편집분이 사라진다).
- 가운뎃점 문자 혼동(`eflaw`는 `ㆍ`, `admrul`은 `·`)과 시행일 미도래 제외는 코드가 자동 처리한다.

## 법령이 Genon으로 안 갈 때
- 전송 설정: `LAW_TRANSFER_ENABLED=true`, `GENON_API_KEY`, `LAW_TRANSFER_LEDGER_FILE=/logs/law-transfer-ledger.json`. 환경변수는 `GENON_*`가 우선이고 옛 `XENON_*`도 받는다(운영 파드에는 아직 `XENON_API_KEY`).
- 로그 `[SEND] 법령 전송 끝 ... 대상=N 성공=N 실패=N 변경없음=N`으로 본다. 변경분이 없으면 대상 0이 정상이다.
- 보낸 기록(ledger)은 문서 본문의 sha256으로 비교한다. 이 파일이 사라지면 다음 수집에 전체(약 320MB)를 다시 보낸다.
- 전송 전에 `GET /api/v1/ping`으로 인증을 확인하고, 4xx는 같은 ZIP을 다시 올리지 않는다. 실패는 기록하지 않으므로 다음 수집에서 다시 시도한다.
- manifest와 ZIP 구조는 변경 금지다([handover.md](handover.md) 5절).

## 매일 새벽 2시대 data.go.kr 타임아웃
- 02:10~02:20 KST 경 `apis.data.go.kr`(기상청, 에어코리아 공통 게이트웨이)에서 Connect timed out, 503, 504가 난다. 우리 쪽 원인이 아니고 다음 주기에 자동 회복된다. 조치하지 않는다.
- 에어코리아 시도별 실시간이 매시 20분에 504 한 번 후 재시도로 회복되는 것도 같은 부류다.

## 재난문자 totalCount 불일치
- 1페이지가 0건인데 `totalCount>0`인 경우가 있고 다음 폴링에서 회복된다. `MAX_PAGES` WARN이 떠도 조치하지 않는다.

## 로그의 구조 드리프트 경고
- `[STRUCTURE-DRIFT]`는 정제기가 아는 필드 목록과 실제 응답을 비교한 경고다. 정제를 막지 않는다.
- 법령은 선택 필드가 많아 2026-11-18에 알려진 목록을 정리했다(`MolegCriminalLawCleanser`, `MolegAdminRuleCleanser`). 이후에도 계속 뜨는 경고는 정상 변이이거나 진짜 변화다. 필드 이름을 보고 선택 필드면 알려진 목록에 추가한다.

## 기상특보 정제 주의
- `collect_dtm`은 마지막 수집 시각(예 23:50)이고, `tm_seq` 재사용으로 과거 행이 덮일 수 있다. 특보 세분 구역은 시설 위경도, 가장 가까운 AWS 관측소, 3km 경계, 해발(OpenTopoData)로 자동 계산한다.
- 경계 근처 시설은 `[WARN-ZONE]` 로그가 매일 난다(부산구치소, 인천구치소, 소망교도소 등). 오류가 아니라 확인 권장 안내다.
