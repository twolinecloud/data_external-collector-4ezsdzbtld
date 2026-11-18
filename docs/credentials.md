# 외부 키와 계정

값은 이 문서에 적지 않는다. 모두 K8s Secret/ConfigMap으로 파드에 주입된다(`kubectl`로 직접 적용, ArgoCD 관리 밖). **현재 대부분이 개인 명의로 발급돼 있고, 법무부 명의로 옮기는 작업이 진행 중이다.**

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
| `VWORLD_API_KEY` | VWorld 지오코딩 | 사용하지 않기로 함, 제거 예정 |
| `ADMIN_DB_*` | admin-db 접속 | |

- OpenTopoData(해발 조회)는 키가 없다.
- 기관 계정으로 옮길 때: 새 키를 발급받아 Secret/ConfigMap을 교체하고 파드를 재시작한다. **법제처는 OC(ID) 교체와 함께 클러스터 노드 IP를 새 계정에 다시 등록해야 한다.** 순서를 놓치면 법령 수집이 조용히 끊긴다([operations.md](operations.md)).
- 호출량: 전체 하루 약 3,900건 수준이고 개발계정 한도는 일 10,000건으로 기록돼 있다. ESB 섀도 비교로 같은 호출을 한 번 더 보내면 서비스별 호출량이 두 배가 되니 그때 다시 계산한다.
- 출처 표시: 데이터별 이용허락범위(공공누리 유형 등)는 각 API 상세 페이지에서 확인해야 한다. 표시는 화면을 만드는 쪽의 일이며 수집기는 `sourceName`을 로그와 이력에만 남긴다.
