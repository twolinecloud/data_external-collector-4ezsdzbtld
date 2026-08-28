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

/** {@code MolegCriminalLawCollectorTest} 대응 버전 - target=admrul 조회. */
@ExtendWith(MockitoExtension.class)
class MolegAdminRuleCollectorTest {

    @Mock
    private LawSourcePort lawSourcePort;

    private static final MolegLaw GASEOKBANG_JIMCHIM =
        new MolegLaw("35334", "가석방 업무지침", "2100000276392", "예규",
            "20260330", "20260330", "법무부", MolegLaw.DOC_TYPE_ADMIN_RULE);

    @Test
    void key와_apiName에_행정규칙명과_lawId가_반영된다() {
        MolegAdminRuleCollector collector = new MolegAdminRuleCollector(lawSourcePort, GASEOKBANG_JIMCHIM);

        assertThat(collector.key()).isEqualTo("moleg-admin-rule--35334");
        assertThat(collector.operationKey()).isEqualTo("moleg-admin-rule");
        assertThat(collector.apiName()).isEqualTo("행정규칙 본문조회 (가석방 업무지침)");
        assertThat(collector.facilityId()).isNull();
    }

    @Test
    void collect는_포트가_반환한_행정규칙본문을_원소_1개짜리_리스트로_반환한다() throws CollectException {
        when(lawSourcePort.fetchAdminRuleBody(any(), any(), eq("가석방 업무지침")))
            .thenReturn("{\"AdmRulService\":{}}");
        MolegAdminRuleCollector collector = new MolegAdminRuleCollector(lawSourcePort, GASEOKBANG_JIMCHIM);

        List<String> result = collector.collect();

        assertThat(result).containsExactly("{\"AdmRulService\":{}}");
    }

    @Test
    void 포트가_실패하면_그대로_전파된다() throws CollectException {
        when(lawSourcePort.fetchAdminRuleBody(any(), any(), eq("가석방 업무지침")))
            .thenThrow(new CollectException("소스", "API", "행정규칙 조회 실패"));
        MolegAdminRuleCollector collector = new MolegAdminRuleCollector(lawSourcePort, GASEOKBANG_JIMCHIM);

        assertThatThrownBy(collector::collect).isInstanceOf(CollectException.class);
    }
}
