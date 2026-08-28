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
}
