package egovframework.external.rule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrecipitationParserTest {

    @Test
    void 강수없음과_적설없음은_0이다() {
        assertThat(PrecipitationParser.parseMm("강수없음")).isZero();
        assertThat(PrecipitationParser.parseMm("적설없음")).isZero();
    }

    @Test
    void null과_빈문자열도_0이다() {
        assertThat(PrecipitationParser.parseMm(null)).isZero();
        assertThat(PrecipitationParser.parseMm("")).isZero();
        assertThat(PrecipitationParser.parseMm("  ")).isZero();
    }

    @Test
    void 구간값은_상한을_쓴다() {
        assertThat(PrecipitationParser.parseMm("30.0~50.0mm")).isEqualTo(50.0);
    }

    @Test
    void 미만값은_그_값_자체를_쓴다() {
        assertThat(PrecipitationParser.parseMm("1.0mm 미만")).isEqualTo(1.0);
    }

    @Test
    void 이상값은_그_값_자체를_쓴다() {
        assertThat(PrecipitationParser.parseMm("50.0mm 이상")).isEqualTo(50.0);
    }

    @Test
    void 단일_mm값은_그대로_파싱된다() {
        assertThat(PrecipitationParser.parseMm("1.0mm")).isEqualTo(1.0);
    }

    @Test
    void 단위없는_순수숫자도_파싱된다() {
        // 초단기실황 RN1 실측 형태 ("0", "23.5" 등 - t1h와 같은 스타일)
        assertThat(PrecipitationParser.parseMm("0")).isZero();
        assertThat(PrecipitationParser.parseMm("23.5")).isEqualTo(23.5);
    }

    @Test
    void 알수없는_형식은_예외를_던진다() {
        assertThatThrownBy(() -> PrecipitationParser.parseMm("보통비"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("보통비");
    }
}
