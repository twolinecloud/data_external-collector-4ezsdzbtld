package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가운뎃점류 유사문자 정규화 검증 - 2026-09-08 실제 사례("국가공무원 복무·징계 관련 예규"에
 * 표준 가운뎃점 대신 한글 자모 아래아가 섞여 행정규칙 조회가 실패했던 것)를 고정.
 */
class LawNameConfusablesTest {

    @Test
    void 한글_자모_아래아를_표준_가운뎃점으로_바꾼다() {
        // 실제 발견 사례 - CSV에 잘못 들어있던 문자 그대로.
        assertThat(LawNameConfusables.normalize("국가공무원 복무ㆍ징계 관련 예규"))
            .isEqualTo("국가공무원 복무·징계 관련 예규");
    }

    @Test
    void 이미_표준_가운뎃점이면_그대로_둔다() {
        String name = "국가공무원 복무·징계 관련 예규";

        assertThat(LawNameConfusables.normalize(name)).isEqualTo(name);
    }

    @Test
    void 다른_유사문자들도_표준형으로_바꾼다() {
        assertThat(LawNameConfusables.normalize("가‧나")).isEqualTo("가·나"); // HYPHENATION POINT
        assertThat(LawNameConfusables.normalize("가⋅나")).isEqualTo("가·나"); // DOT OPERATOR
        assertThat(LawNameConfusables.normalize("가∙나")).isEqualTo("가·나"); // BULLET OPERATOR
        assertThat(LawNameConfusables.normalize("가•나")).isEqualTo("가·나"); // BULLET
    }

    @Test
    void 가운뎃점이_여러_번_섞여도_전부_바꾼다() {
        assertThat(LawNameConfusables.normalize("아동ㆍ청소년의 성보호ㆍ지원에 관한 법률"))
            .isEqualTo("아동·청소년의 성보호·지원에 관한 법률");
    }

    @Test
    void 가운뎃점이_아예_없으면_변경_없이_그대로_반환한다() {
        String name = "형법";

        assertThat(LawNameConfusables.normalize(name)).isSameAs(name); // 새 객체를 안 만드는지까지 확인
    }

    @Test
    void null은_null_그대로_반환한다() {
        assertThat(LawNameConfusables.normalize(null)).isNull();
    }
}
