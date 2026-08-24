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

    /** MST(법령일련번호) 기준 법령 본문 원문(JSON)을 그대로 반환. */
    String fetchLawBody(String sourceName, String apiName, String mst) throws CollectException;
}
