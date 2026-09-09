package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가운뎃점류 문자 정규화 검증. 두 방향을 모두 고정한다:
 * - admrul(행정규칙): 2026-09-08 실제 사례("국가공무원 복무·징계 관련 예규"에 표준 가운뎃점
 *   대신 한글 자모 아래아가 섞여 행정규칙 조회가 실패했던 것) - 표준형(·)으로 통일.
 * - eflaw(법령): 2026-09-09 실측 확인 - admrul과 반대로 유사문자(ㆍ)를 줘야 매칭되고, 표준
 *   가운뎃점(·)을 주면 HTML 에러 페이지가 옴 - 유사문자(ㆍ)로 통일.
 */
class LawNameConfusablesTest {

    @Test
    void 행정규칙용_한글_자모_아래아를_표준_가운뎃점으로_바꾼다() {
        // 실제 발견 사례 - CSV에 잘못 들어있던 문자 그대로.
        assertThat(LawNameConfusables.normalizeForAdminRule("국가공무원 복무ㆍ징계 관련 예규"))
            .isEqualTo("국가공무원 복무·징계 관련 예규");
    }

    @Test
    void 행정규칙용_이미_표준_가운뎃점이면_그대로_둔다() {
        String name = "국가공무원 복무·징계 관련 예규";

        assertThat(LawNameConfusables.normalizeForAdminRule(name)).isEqualTo(name);
    }

    @Test
    void 행정규칙용_다른_유사문자들도_표준형으로_바꾼다() {
        assertThat(LawNameConfusables.normalizeForAdminRule("가‧나")).isEqualTo("가·나"); // HYPHENATION POINT
        assertThat(LawNameConfusables.normalizeForAdminRule("가⋅나")).isEqualTo("가·나"); // DOT OPERATOR
        assertThat(LawNameConfusables.normalizeForAdminRule("가∙나")).isEqualTo("가·나"); // BULLET OPERATOR
        assertThat(LawNameConfusables.normalizeForAdminRule("가•나")).isEqualTo("가·나"); // BULLET
    }

    @Test
    void 법령용_표준_가운뎃점을_한글_자모_아래아로_바꾼다() {
        // 2026-09-09 실측 - eflaw는 표준 가운뎃점(·)을 주면 HTML 에러 페이지가 옴.
        assertThat(LawNameConfusables.normalizeForLaw("인지 첩부·첨부 및 공탁 제공에 관한 특례법"))
            .isEqualTo("인지 첩부ㆍ첨부 및 공탁 제공에 관한 특례법");
    }

    @Test
    void 법령용_이미_유사문자면_그대로_둔다() {
        String name = "아동ㆍ청소년의 성보호에 관한 법률";

        assertThat(LawNameConfusables.normalizeForLaw(name)).isEqualTo(name);
    }

    @Test
    void 법령용_다른_유사문자들도_아래아로_바꾼다() {
        assertThat(LawNameConfusables.normalizeForLaw("가‧나")).isEqualTo("가ㆍ나");
        assertThat(LawNameConfusables.normalizeForLaw("가⋅나")).isEqualTo("가ㆍ나");
        assertThat(LawNameConfusables.normalizeForLaw("가∙나")).isEqualTo("가ㆍ나");
        assertThat(LawNameConfusables.normalizeForLaw("가•나")).isEqualTo("가ㆍ나");
    }

    @Test
    void 가운뎃점이_여러_번_섞여도_전부_바꾼다() {
        assertThat(LawNameConfusables.normalizeForAdminRule("아동ㆍ청소년의 성보호ㆍ지원에 관한 법률"))
            .isEqualTo("아동·청소년의 성보호·지원에 관한 법률");
        assertThat(LawNameConfusables.normalizeForLaw("아동·청소년의 성보호·지원에 관한 법률"))
            .isEqualTo("아동ㆍ청소년의 성보호ㆍ지원에 관한 법률");
    }

    @Test
    void 가운뎃점이_아예_없으면_변경_없이_그대로_반환한다() {
        String name = "형법";

        assertThat(LawNameConfusables.normalizeForAdminRule(name)).isSameAs(name); // 새 객체를 안 만드는지까지 확인
        assertThat(LawNameConfusables.normalizeForLaw(name)).isSameAs(name);
    }

    @Test
    void null은_null_그대로_반환한다() {
        assertThat(LawNameConfusables.normalizeForAdminRule(null)).isNull();
        assertThat(LawNameConfusables.normalizeForLaw(null)).isNull();
    }
}
