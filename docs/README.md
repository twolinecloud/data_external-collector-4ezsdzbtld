# external-collector 이관 문서

공공 API(기상청, 에어코리아, 행정안전부 재난문자, 법제처 법령)를 수집해 admin-db(`kcais` 스키마)에 적재하고, 법령은 Genon으로 전송하는 배치 서비스의 인수인계 자료다. 작성일 2026-11-18.

| 문서 | 내용 |
|---|---|
| [handover.md](handover.md) | 구조, 스케줄, 배포, 확정된 결정, 알려진 한계 |
| [operations.md](operations.md) | 운영 중 겪은 장애와 확인 방법(런북) |
| [credentials.md](credentials.md) | 외부 키와 계정 목록, 교체 방법 |
| [esb-migration.md](esb-migration.md) | 직접 호출에서 ESB(HTTP gateway)로 전환하는 구조와 남은 일 |

- 이 저장소의 `private-doc/`은 git에 올라가지 않는다(설계서, 스키마 명세, 메타빌드 문서, Genon 규약 PDF 등). 이관 때 별도로 넘겨야 한다.
- 이 문서는 운영 중 쌓은 지식을 코드와 함께 버전 관리하려고 만든 것이다. 코드와 어긋나면 코드가 맞다.
