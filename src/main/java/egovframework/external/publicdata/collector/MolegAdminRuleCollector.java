package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;

import java.util.List;

/**
 * 국가법령정보센터(법제처) 행정규칙 본문조회(2026-08-28 추가). {@link MolegCriminalLawCollector}와
 * 거의 동일한 구조지만, {@link LawSourcePort#fetchAdminRuleBody}를 호출해 {@code target=admrul}
 * (응답 봉투가 법령과 다름 - {@code AdmRulService} 키)로 조회한다는 점만 다르다.
 *
 * <p>{@link MolegLawCollectorFactory}가 {@link MolegLaw#docTypeOrDefault()}로 법령/행정규칙을
 * 갈라 이 컬렉터 또는 {@link MolegCriminalLawCollector}를 생성한다.</p>
 */
public class MolegAdminRuleCollector implements PublicDataCollector {

    private final LawSourcePort lawSourcePort;
    private final MolegLaw rule;

    public MolegAdminRuleCollector(LawSourcePort lawSourcePort, MolegLaw rule) {
        this.lawSourcePort = lawSourcePort;
        this.rule = rule;
    }

    @Override
    public String key() {
        return "moleg-admin-rule--" + rule.lawId();
    }

    @Override
    public String operationKey() {
        return "moleg-admin-rule";
    }

    @Override
    public String sourceName() {
        return "국가법령정보센터 (법제처)";
    }

    @Override
    public String apiName() {
        return "행정규칙 본문조회 (" + rule.lawName() + ")";
    }

    @Override
    public List<String> collect() throws CollectException {
        String body = lawSourcePort.fetchAdminRuleBody(sourceName(), apiName(), rule.lawName());
        return List.of(body);
    }
}
