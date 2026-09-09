package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MolegLaw#isEffectiveAsOf} 검증 - 2026-09-09 확인 사례("공소청법"(시행 2026-10-02),
 * "친일반민족행위자 재산의 국가귀속 등에 관한 특별법"(시행 2026-12-03)이 아직 시행 전이라
 * eflaw 조회가 늘 실패했던 것)를 고정한다.
 */
class MolegLawTest {

    private static MolegLaw lawWithEffectiveDate(String effectiveDate) {
        return new MolegLaw("015092", "공소청법", "285045", "법률", "20260324", effectiveDate, "법무부", MolegLaw.DOC_TYPE_LAW);
    }

    @Test
    void 시행일이_미래면_아직_시행_전이다() {
        MolegLaw law = lawWithEffectiveDate("20261002");

        assertThat(law.isEffectiveAsOf(LocalDate.of(2026, 9, 9))).isFalse();
    }

    @Test
    void 시행일이_과거면_이미_시행_중이다() {
        MolegLaw law = lawWithEffectiveDate("20260324");

        assertThat(law.isEffectiveAsOf(LocalDate.of(2026, 9, 9))).isTrue();
    }

    @Test
    void 시행일_당일도_이미_시행_중으로_취급한다() {
        MolegLaw law = lawWithEffectiveDate("20261002");

        assertThat(law.isEffectiveAsOf(LocalDate.of(2026, 10, 2))).isTrue();
    }

    @Test
    void 시행일이_비어있으면_판단할_수_없어_안전하게_시행_중으로_취급한다() {
        assertThat(lawWithEffectiveDate(null).isEffectiveAsOf(LocalDate.of(2026, 9, 9))).isTrue();
        assertThat(lawWithEffectiveDate("").isEffectiveAsOf(LocalDate.of(2026, 9, 9))).isTrue();
    }

    @Test
    void 시행일_형식이_깨졌으면_판단할_수_없어_안전하게_시행_중으로_취급한다() {
        assertThat(lawWithEffectiveDate("모름").isEffectiveAsOf(LocalDate.of(2026, 9, 9))).isTrue();
    }
}
