package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;

import java.util.List;

/**
 * 국가법령정보센터(법제처) 형사법령 본문조회. {@link MolegLaw} 하나(=법령 하나)당 인스턴스
 * 하나 - {@link MolegLawCollectorFactory}가 44개 생성한다.
 *
 * <p>기상청 컬렉터와 달리 "지역"이 아니라 "법령"이 반복 축(팬아웃)이다 - {@code facilityId()}는
 * 위치기반이 아니므로 기본값(null)을 그대로 쓴다. 실제 API 호출은 {@link LawSourcePort}에
 * 위임하고 이 클래스는 어떤 소스(직접연결/ESB)로 연결되는지 모른다.</p>
 */
public class MolegCriminalLawCollector implements PublicDataCollector {

    private final LawSourcePort lawSourcePort;
    private final MolegLaw law;

    public MolegCriminalLawCollector(LawSourcePort lawSourcePort, MolegLaw law) {
        this.lawSourcePort = lawSourcePort;
        this.law = law;
    }

    @Override
    public String key() {
        return "moleg-criminal-law--" + law.lawId();
    }

    @Override
    public String operationKey() {
        return "moleg-criminal-law";
    }

    @Override
    public String sourceName() {
        return "국가법령정보센터 (법제처)";
    }

    @Override
    public String apiName() {
        return "법령 본문조회 (" + law.lawName() + ")";
    }

    @Override
    public List<String> collect() throws CollectException {
        // MST(법령일련번호) 대신 법령명으로 조회(2026-08-28, LawSourcePort 참고) - 법령이
        // 개정돼도 대상 목록의 법령명만 맞으면 코드/설정 변경 없이 최신 버전이 자동 반영된다.
        String body = lawSourcePort.fetchLawBody(sourceName(), apiName(), law.lawName());
        return List.of(body);
    }
}
