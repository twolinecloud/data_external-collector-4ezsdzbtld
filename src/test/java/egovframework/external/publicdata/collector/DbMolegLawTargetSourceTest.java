package egovframework.external.publicdata.collector;

import egovframework.external.publicdata.collector.mapper.LawTargetMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * admin-db 조회 실패가 절대 예외로 전파되지 않는지 검증(2026-08-21) - 이 목록은 매일 새벽
 * 법령 수집 스케줄러가 참조하므로, DB 장애 시 그날 수집만 0건으로 건너뛰어야 한다.
 */
@ExtendWith(MockitoExtension.class)
class DbMolegLawTargetSourceTest {

    @Mock
    private LawTargetMapper mapper;

    @Test
    void 정상_조회시_MolegLaw_목록으로_변환한다() {
        when(mapper.selectActiveTargets()).thenReturn(List.of(Map.of(
            "lawId", "001692", "lawName", "형법", "mst", "284025", "lawType", "법률",
            "promulgationDate", "20260312", "effectiveDate", "20260312", "ministry", "법무부"
        )));

        List<MolegLaw> laws = new DbMolegLawTargetSource(mapper).current();

        assertThat(laws).hasSize(1);
        assertThat(laws.get(0).lawId()).isEqualTo("001692");
        assertThat(laws.get(0).mst()).isEqualTo("284025");
    }

    @Test
    void doc_type_cd로_행정규칙을_구분하고_값이_없으면_법령으로_취급한다() {
        when(mapper.selectActiveTargets()).thenReturn(List.of(
            Map.of("lawId", "35334", "lawName", "가석방 업무지침", "mst", "1", "lawType", "예규", "docType", "ADMIN_RULE"),
            Map.of("lawId", "001692", "lawName", "형법", "mst", "2", "lawType", "법률", "docType", "LAW"),
            Map.of("lawId", "000001", "lawName", "옛 행", "mst", "3", "lawType", "법률")));

        List<MolegLaw> laws = new DbMolegLawTargetSource(mapper).current();

        assertThat(laws).extracting(MolegLaw::docTypeOrDefault).containsExactly("ADMIN_RULE", "LAW", "LAW");
        assertThat(laws.get(0).lawType()).isEqualTo("예규");
    }

    @Test
    void 사용_중인_대상이_0건이어도_빈_리스트를_돌려주고_예외는_없다() {
        when(mapper.selectActiveTargets()).thenReturn(List.of());

        assertThat(new DbMolegLawTargetSource(mapper).current()).isEmpty();
    }

    @Test
    void DB_조회가_예외를_던지면_빈_리스트를_반환하고_예외를_전파하지_않는다() {
        when(mapper.selectActiveTargets()).thenThrow(new DataAccessResourceFailureException("connection refused"));

        List<MolegLaw> laws = new DbMolegLawTargetSource(mapper).current();

        assertThat(laws).isEmpty();
    }
}
