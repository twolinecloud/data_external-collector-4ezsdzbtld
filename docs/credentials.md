# 외부 키와 계정

값은 이 문서에 적지 않는다. 모두 K8s Secret/ConfigMap으로 파드에 주입된다(`kubectl`로 직접 적용, ArgoCD 관리 밖). **현재 대부분이 개인 명의로 발급돼 있고, 법무부 명의로 옮기는 작업이 진행 중이다.**

**키 관리 주체**: 이 문서의 키는 개발팀과 함께 **법무부 주무관도 관리**한다(발급, 갱신, 만료 확인). 담당자 이름과 연락처는 이 문서에 적지 않고 이관 때 별도로 전달한다. 키를 바꿀 때는 주무관에게도 알리고, 교체 이력은 아래 "키 교체 이력"에 남긴다.

| 환경변수 | 대상 | 비고 |
|---|---|---|
| `KMA_VILLAGE_FORECAST_SERVICE_KEY` | 공공데이터포털 기상청 단기예보 | |
| `KMA_WEATHER_WARNING_SERVICE_KEY` | 공공데이터포털 기상청 기상특보 | |
| `LIVING_WTHR_IDX_SERVICE_KEY` | 공공데이터포털 기상청 생활기상지수 | |
| `AIRKOREA_SERVICE_KEY` | 공공데이터포털 한국환경공단 에어코리아 | |
| `KMA_APIHUB_AUTH_KEY` | 기상청 API허브(ASOS 시간자료, 특보구역 관측소) | 파라미터 이름이 `authKey` |
| `SAFETYDATA_SERVICE_KEY` | 재난안전데이터공유플랫폼 긴급재난문자 | |
| `MOLEG_OC` | 법제처 국가법령정보 | 값은 신청자 ID. **호출 서버 IP 등록 필요** |
| `GENON_API_KEY` (옛 `XENON_API_KEY`) | Genon 법령 전송 | 내부, Bearer 토큰 |
| `VWORLD_API_KEY` | VWorld 지오코딩 | 효과가 없어 향후 제거 예정(당분간 유지) |
| `ADMIN_DB_*` | admin-db 접속 | |

- OpenTopoData(해발 조회)는 키가 없다.
- 기관 계정으로 옮길 때: 새 키를 발급받아 Secret/ConfigMap을 교체하고 파드를 재시작한다. **법제처는 OC(ID) 교체와 함께 클러스터 노드 IP를 새 계정에 다시 등록해야 한다.** 순서를 놓치면 법령 수집이 조용히 끊긴다([operations.md](operations.md)).
- 호출량: 전체 하루 약 3,900건 수준이고 개발계정 한도는 일 10,000건으로 기록돼 있다. ESB 섀도 비교로 같은 호출을 한 번 더 보내면 서비스별 호출량이 두 배가 되니 그때 다시 계산한다.
- 출처 표시: 데이터별 이용허락범위(공공누리 유형 등)는 각 API 상세 페이지에서 확인해야 한다. 표시는 화면을 만드는 쪽의 일이며 수집기는 `sourceName`을 로그와 이력에만 남긴다.

## 키 교체 이력
### 2026-11-19 신규 발급 키 반영
`kubectl patch secret external-collector-secret-toqd7wzduo -n data-pipeline`으로 7개 키를 바꾸고 `kubectl rollout restart deploy/external-collector-4ezsdzbtld -n data-pipeline`로 재시작했다(16:12 KST). 값은 `.env`(로컬, git 제외)에서 가져왔다.

| 키 | 교체 | 사전 확인 |
|---|---|---|
| `KMA_VILLAGE_FORECAST_SERVICE_KEY`, `KMA_WEATHER_WARNING_SERVICE_KEY`, `LIVING_WTHR_IDX_SERVICE_KEY`, `AIRKOREA_SERVICE_KEY` | 공공데이터포털 새 키 하나(64자)로 통일 | 서비스별로 `resultCode 00 / NORMAL_SERVICE` |
| `KMA_APIHUB_AUTH_KEY` | 새 키 | ASOS 시간자료, 특보구역 관측소 데이터 수신 |
| `SAFETYDATA_SERVICE_KEY` | 새 키 | `resultCode 00`, 틀린 키는 `30`으로 구분됨 |
| `MOLEG_OC` | 새 OC | 클러스터 파드에서 호출 성공(노드 IP 등록 확인), 틀린 OC는 IP 검증 오류 |

- 반영 뒤 첫 주기(재난문자 10건, 기상특보 26건 수집)에서 새 키 정상, ERROR 없음. API허브와 법제처 OC는 이후 스케줄(ASOS 매시, 법령 05:00 KST)에서 처음 쓰이니 그 로그에서 한 번 더 확인한다.
- **공공데이터포털 키 형태가 바뀌었다.** 예전 키는 `%`, `+`, `/`가 든 URL 인코딩 형태(98자)였고 지금은 소문자 16진수(64자)다. 앱은 키를 인코딩하지 않고 그대로 붙이므로 영향이 없다. **HTTP 200만으로 성공을 판단하면 안 된다**(키가 틀려도 200이고 본문에 오류 코드가 담긴다). 본문의 `resultCode`/`NORMAL_SERVICE`로 판단한다.
- 바꾸지 않은 것: `XENON_API_KEY`(값 변경 없음), `ADMIN_DB_*`. `GENON_API_KEY`와 `XENON_API_KEY`는 회사명 영문 표기를 잘못 알았던 흔적으로 호환용 두 이름이 공존하는 것이며 값은 같다.
- Secret에 없는 키: `KMA_FORECAST_BULLETIN_SERVICE_KEY`(예보통보문, 운영 미사용), `VWORLD_API_KEY`(비어 있음). `.env`에는 값이 있다.
- 롤백: 교체 전 Secret은 작업 PC에 임시 백업했다. 필요하면 이전 키로 같은 `patch`를 하고 파드를 재시작한다.

### 2026-11-19~24 Secret이 이전 키로 되돌려진 사건
- **원인**: Jenkins Deploy 단계(`Jenkinsfile` 86~90줄)가 배포 때마다 HelmChart **`master`**의 `pipeline/Common`을 통째로 `helm template | kubectl apply` 한다. 이 `Common`에 이 Secret(`external-collector-secret-toqd7wzduo`)과 `regsecret` 등이 함께 들어 있어서, **`Common`을 쓰는 어느 서비스든 배포하면** 이 Secret이 `master`의 값으로 덮인다. 새 키를 `kubectl patch`로만 넣고 `master`를 고치지 않아서 되돌려졌다.
- 확인된 시각(UTC): 11-19 08:02:08, 11-20 08:43:17, 11-24 01:32:07. 11-24 건은 우리 `dev` push에 이어 Jenkins가 `master`에 `Pushed Helm Chart: ...-d187f2b`를 push한 시각과 같고, 같은 초에 `regsecret`도 적용됐다. 앞의 두 건은 다른 서비스 배포였을 가능성이 크고 빌드 이력으로 확정할 수 있다(미확인). 10-26 04:28:27에도 같은 적용이 있었다.
- 값이 같으면 적용해도 `managedFields`가 갱신되지 않는다. 그래서 클러스터 값과 `master` 값이 같을 때는 아무 흔적이 없다.
- **값의 원본은 HelmChart `master`의 `pipeline/Common/templates/external-collector-secret-toqd7wzduo.yaml`이다.** 11-24에 새 키로 고쳤다(`e01264e`, `feature/external-collector`의 `817e106`과 같은 내용). 키를 바꿀 때는 클러스터 Secret과 이 파일을 함께 바꾼다. `kubectl patch`만 하면 다음 배포 때 되돌려진다.
- 11-24 10:40 KST에 Secret을 다시 반영하고 파드를 재시작했다. 이후 배포가 새 키를 유지하는지는 아래 명령으로 `kubectl-client-side-apply` 시각이 바뀌는지, 키 해시가 새 값인지 확인한다.
  `kubectl get secret external-collector-secret-toqd7wzduo -n data-pipeline --show-managed-fields -o json | python3 -c "import sys,json;[print(m['manager'],m['time']) for m in json.load(sys.stdin)['metadata']['managedFields']]"`
- Turaco가 `master`를 재생성하면(09-23, 09-29 이력) 이 파일이 초기화되어 되돌려질 수 있다. 재생성 뒤에는 이 파일을 다시 확인한다.
- 우리 배포도 같은 `Common`의 다른 서비스 Secret과 ConfigMap을 `master` 값으로 다시 적용한다. 다른 팀이 직접 바꾼 값이 있다면 우리 배포 때 되돌려질 수 있다.
- 이 저장소의 Secret 템플릿에는 운영 키가 base64로 들어 있다(암호화 아님). 이전 키도 git 이력에 남아 있으니 법무부 명의 이관 때 이전 키를 폐기하는지 정한다. 기관 이관 전에 저장소 접근 범위도 확인한다.
