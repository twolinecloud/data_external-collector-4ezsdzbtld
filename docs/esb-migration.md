# ESB(메타빌드 MESIM) 전환

## 방침
스케줄러, 수집기, 정제, 적재는 그대로 두고 **외부 호출의 통신 대상만** 직접 호출에서 ESB로 바꾼다. ESB는 HTTP gateway 방식이고 IF_ID는 메타빌드가 정해 주기로 했다(회신 전).

## 이미 준비된 것 (`publicdata/source/`)
- `ExternalApiPort`: 호출 1회(`getText`, `getBytes`)를 추상화한다. 수집기는 이 포트로 호출한다(`KmaApiClient`, `AirKoreaApiClient`, `KmaApiHubClient`, `DisasterMsgCollector`, `KmaWarningZoneDirectory`). 법령 본문은 `LawSourcePort`, Genon 전송 쪽은 `LawFileFetcher`, `LawMstLookup`, `LawIngestPort`가 이미 있다.
- `DirectExternalApiAdapter`: 기존 직접 호출 그대로.
- `RoutingExternalApiPort`(@Primary): operation별로 `direct` 또는 `esb`를 고른다. 호출마다 Timer `external_api_call`(태그 operation, mode, outcome=success|empty|failure)을 남기고 실패는 WARN `[외부호출] 실패 ...`로 남긴다.
- `EsbExternalApiPort`: 표지 인터페이스. **구현체는 아직 없다.**
- `ResponseComparator`: 직접 응답과 ESB 응답의 동등 비교(JSON은 키 순서, 공백 무시와 숫자 값 비교, 텍스트는 줄바꿈, 끝 공백 무시).

## 설정
`public-data.source.mode.default`(기본 `direct`)와 `public-data.source.mode.<operation>`. operation은 호출 URL의 마지막 경로 조각에서 영숫자만 남겨 소문자로 만든 값이다(예 `getultrasrtncst`, `dsspif00247`, `kmasfctm2php`, `wrnregaws2php`; 목록은 `application.yml` 상단 주석). `esb`로 설정했는데 구현체가 없거나 알 수 없는 값이면 **기동 시 실패**한다.

## 남은 일
1. 메타빌드가 IF_ID 매핑표를 회신한다(메타빌드 문서 §8, `private-doc/metabuild/`).
2. `EsbExternalApiPort` 구현: `ExternalApiCall`(endpoint, keyParam, params)에서 게이트웨이 요청(IF_ID, TRANSACTION_UNIQUE_ID, SND_CD 헤더와 RESULT/RECORD 봉투)을 만들고 응답을 풀어 원문을 돌려준다.
   - **예외 계약**: Spring `RestClientException` 계열로 변환한다(4xx는 `HttpClientErrorException`, 잘못된 URI는 `IllegalArgumentException`). 호출하는 쪽이 이것으로 재시도 여부를 정한다.
3. operation별 IF_ID를 설정으로 연결한다.
4. 섀도 비교(같은 요청을 직접과 ESB에 보내 `ResponseComparator`로 비교)를 연결한다. 호출량이 두 배가 되는 점에 주의한다.
5. operation을 하나씩 `esb`로 전환한다.
6. 실행 제어(기준 시각 유지, 중복 방지)는 별도 검토 항목이다.

## 참고 자료
- 원문 응답 샘플(24케이스, 키 없음, OC 마스킹): 저장소 밖 `export/esb-mock-samples/`(이관 때 함께 넘긴다).
- VWorld, OpenTopoData 호출은 아직 포트를 거치지 않는다(ESB 제외 가능성).
