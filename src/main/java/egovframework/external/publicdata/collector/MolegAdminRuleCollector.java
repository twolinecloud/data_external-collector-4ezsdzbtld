package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import egovframework.external.utility.PipelineLogUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 국가법령정보센터(법제처) 행정규칙 본문조회(2026-08-28 추가). {@link MolegCriminalLawCollector}와
 * 거의 동일한 구조지만, {@link LawSourcePort#fetchAdminRuleBody}를 호출해 {@code target=admrul}
 * (응답 봉투가 법령과 다름 - {@code AdmRulService} 키)로 조회한다는 점만 다르다.
 *
 * <p>{@link MolegLawCollectorFactory}가 {@link MolegLaw#docTypeOrDefault()}로 법령/행정규칙을
 * 갈라 이 컬렉터 또는 {@link MolegCriminalLawCollector}를 생성한다.</p>
 *
 * <p>시행일 미도래로 인한 "예상된 실패" 흡수는 {@link MolegCriminalLawCollector}와 동일한
 * 이유(그 클래스 주석 참고) - 행정규칙도 이름 자체가 이미 존재하는(현행 버전이 있는) 개정
 * 예정 건과, 아예 새로 생기는 건을 미리 구분할 수 없어 똑같이 실제 시도 후에만 판단한다.</p>
 */
public class MolegAdminRuleCollector implements PublicDataCollector {

    private static final Logger logger = LogManager.getLogger(MolegAdminRuleCollector.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

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
        try {
            String body = lawSourcePort.fetchAdminRuleBody(sourceName(), apiName(), rule.lawName());
            return List.of(body);
        } catch (CollectException e) {
            if (!rule.isEffectiveAsOf(LocalDate.now(KST))) {
                PipelineLogUtils.info(logger, "COLLECT", sourceName(), apiName(),
                    "시행 예정(시행일=" + rule.effectiveDate() + ")이라 아직 조회 안 됨 - 예상된 결과: " + e.getMessage());
                return List.of();
            }
            throw e;
        }
    }
}
