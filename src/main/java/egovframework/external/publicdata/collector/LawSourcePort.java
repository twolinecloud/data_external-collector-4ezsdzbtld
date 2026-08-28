package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;

/**
 * 법령 데이터 소스에 접근하는 포트 - "커넥트 영역과 수신부"를 인터페이스로 분리해둔 것.
 *
 * <p>지금은 국가법령정보센터(law.go.kr)에 직접 연결하는 {@link DirectLawSourceAdapter}
 * 하나만 활성 구현이지만, 이 연결 자체가 앞으로 ESB(Enterprise Service Bus)로 옮겨갈
 * 예정이다. ESB 스펙이 나오면 이 인터페이스를 구현하는 새 어댑터(예: {@code EsbLawSourceAdapter})
 * 하나만 추가하고 Spring 설정으로 갈아끼우면 되고, {@link MolegCriminalLawCollector}를
 * 비롯한 컬렉터 쪽 코드는 전혀 손댈 필요가 없다.</p>
 */
public interface LawSourcePort {

    /**
     * 법령명 기준 "현행법령"(target=eflaw) 본문 원문(JSON)을 그대로 반환.
     *
     * <p>MST(법령일련번호) 대신 법령명으로 조회한다(2026-08-28 전환) - MST는 법령이 개정될
     * 때마다 새로 발급되는 값이라, 수집 대상 목록({@code tb_ext_law_target}/CSV)에 MST를
     * 고정해두면 개정 이후 계속 옛 버전을 가져오거나 실패하게 된다({@link MolegLaw} 클래스
     * 주석 참고 - 이 리스크를 이미 인지하고 있었음). {@code target=eflaw}는 법령명으로 "지금
     * 시행 중인 버전"을 매번 다시 찾기 때문에, 대상 목록의 법령명만 관리하면 코드/설정 변경
     * 없이 개정이 자동 반영된다.</p>
     */
    String fetchLawBody(String sourceName, String apiName, String lawName) throws CollectException;

    /**
     * 행정규칙명 기준 "현행" (target=admrul) 본문 원문(JSON)을 그대로 반환(2026-08-28 추가).
     *
     * <p>{@link #fetchLawBody}와 같은 이유(개정 시 mst 재발급)로 명칭 기준 조회를 쓰지만,
     * 응답 봉투가 다르다 - 성공 시 최상위 키가 {@code "법령"}이 아니라 {@code "AdmRulService"}
     * (실 API로 확인, 필드 구성도 다름: 개정문/별표/행정규칙기본정보/조문내용/첨부파일/부칙/
     * 제개정이유). 그래서 법령과 별도 메서드로 분리했다 - 호출부({@code MolegAdminRuleCollector})가
     * 어느 조회인지 명확히 구분되고, 어댑터 구현도 응답 검증 로직을 서로 다르게 가져갈 수 있다.</p>
     */
    String fetchAdminRuleBody(String sourceName, String apiName, String ruleName) throws CollectException;
}
