package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import egovframework.external.utility.PipelineLogUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 국가법령정보센터(법제처) 형사법령 본문조회. {@link MolegLaw} 하나(=법령 하나)당 인스턴스
 * 하나 - {@link MolegLawCollectorFactory}가 44개 생성한다.
 *
 * <p>기상청 컬렉터와 달리 "지역"이 아니라 "법령"이 반복 축(팬아웃)이다 - {@code facilityId()}는
 * 위치기반이 아니므로 기본값(null)을 그대로 쓴다. 실제 API 호출은 {@link LawSourcePort}에
 * 위임하고 이 클래스는 어떤 소스(직접연결/ESB)로 연결되는지 모른다.</p>
 *
 * <p><b>시행일 미도래로 인한 "예상된 실패" 흡수(2026-09-10 추가)</b>: 공포는 됐지만 아직
 * 시행 전인 신규 법령(예: "공소청법")은 그 이름 자체가 API에 없어 실패한다 - 코드/CSV 오류가
 * 아니라 예정된 상태({@link MolegLawCollectorFactory} 주석 참고). 실패 시 {@link
 * MolegLaw#isEffectiveAsOf}로 이 경우인지 확인해서, 맞으면 예외를 삼키고 빈 결과(0건 수집
 * 성공)로 취급한다 - WARN/FAILED로 잡혀 매일 잡음이 되는 걸 막는다. 반대로 "상법"처럼 이름은
 * 이미 존재하는(현행 버전이 있는) 개정 예정 법령은 이 catch 블록에 들어오지 않고 정상 성공한다
 * - 그래서 여기서 미리 걸러내지 않고 실제로 시도해본 뒤에만 판단한다.</p>
 */
public class MolegCriminalLawCollector implements PublicDataCollector {

    private static final Logger logger = LogManager.getLogger(MolegCriminalLawCollector.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

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
        try {
            String body = lawSourcePort.fetchLawBody(sourceName(), apiName(), law.lawName());
            return List.of(body);
        } catch (CollectException e) {
            if (!law.isEffectiveAsOf(LocalDate.now(KST))) {
                PipelineLogUtils.info(logger, "COLLECT", sourceName(), apiName(),
                    "시행 예정(시행일=" + law.effectiveDate() + ")이라 아직 조회 안 됨 - 예상된 결과: " + e.getMessage());
                return List.of();
            }
            throw e;
        }
    }
}
