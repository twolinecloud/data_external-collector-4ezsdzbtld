package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MolegCriminalLawCollectorTest {

    @Mock
    private LawSourcePort lawSourcePort;

    private static final MolegLaw CRIMINAL_ACT =
        new MolegLaw("001692", "형법", "284025", "법률", "20260312", "20260312", "법무부", MolegLaw.DOC_TYPE_LAW);

    @Test
    void key와_apiName에_법령명과_lawId가_반영된다() {
        MolegCriminalLawCollector collector = new MolegCriminalLawCollector(lawSourcePort, CRIMINAL_ACT);

        assertThat(collector.key()).isEqualTo("moleg-criminal-law--001692");
        assertThat(collector.operationKey()).isEqualTo("moleg-criminal-law");
        assertThat(collector.apiName()).isEqualTo("법령 본문조회 (형법)");
        assertThat(collector.facilityId()).isNull();
    }

    @Test
    void collect는_포트가_반환한_법령본문을_원소_1개짜리_리스트로_반환한다() throws CollectException {
        when(lawSourcePort.fetchLawBody(any(), any(), eq("형법"))).thenReturn("{\"법령\":{}}");
        MolegCriminalLawCollector collector = new MolegCriminalLawCollector(lawSourcePort, CRIMINAL_ACT);

        List<String> result = collector.collect();

        assertThat(result).containsExactly("{\"법령\":{}}");
    }

    @Test
    void 포트가_실패하면_그대로_전파된다() throws CollectException {
        when(lawSourcePort.fetchLawBody(any(), any(), eq("형법")))
            .thenThrow(new CollectException("소스", "API", "법령 조회 실패"));
        MolegCriminalLawCollector collector = new MolegCriminalLawCollector(lawSourcePort, CRIMINAL_ACT);

        assertThatThrownBy(collector::collect).isInstanceOf(CollectException.class);
    }

    @Test
    void 시행일_미도래_법령이_실패하면_예외를_삼키고_빈_결과를_반환한다() throws CollectException {
        // 2026-09-10 실측 - "공소청법"처럼 아직 시행 전이라 이름 자체가 API에 없는 신규
        // 법령은 실패가 예상된 결과다. WARN/FAILED로 잡혀 매일 잡음이 되는 걸 막는다.
        MolegLaw notYetEffective = new MolegLaw("015092", "공소청법", "285045", "법률",
            "20260324", "29991231", "법무부", MolegLaw.DOC_TYPE_LAW);
        when(lawSourcePort.fetchLawBody(any(), any(), eq("공소청법")))
            .thenThrow(new CollectException("소스", "API", "법령 조회 실패(법령명=공소청법): {}"));
        MolegCriminalLawCollector collector = new MolegCriminalLawCollector(lawSourcePort, notYetEffective);

        List<String> result = collector.collect();

        assertThat(result).isEmpty();
    }

    @Test
    void 시행일이_지난_뒤엔_실패해도_그대로_전파된다() throws CollectException {
        // effectiveDate가 과거인데도 실패하면(예: "상법"처럼 이름은 이미 존재하는 법인데
        // 다른 이유로 실패) 시행일 미도래로 오인해서 삼키면 안 된다 - 진짜 실패로 취급.
        MolegLaw alreadyEffective = new MolegLaw("001702", "상법", "284143", "법률",
            "20260306", "20260306", "법무부", MolegLaw.DOC_TYPE_LAW);
        when(lawSourcePort.fetchLawBody(any(), any(), eq("상법")))
            .thenThrow(new CollectException("소스", "API", "API 호출 실패"));
        MolegCriminalLawCollector collector = new MolegCriminalLawCollector(lawSourcePort, alreadyEffective);

        assertThatThrownBy(collector::collect).isInstanceOf(CollectException.class);
    }
}
