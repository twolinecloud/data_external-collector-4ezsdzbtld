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
}
