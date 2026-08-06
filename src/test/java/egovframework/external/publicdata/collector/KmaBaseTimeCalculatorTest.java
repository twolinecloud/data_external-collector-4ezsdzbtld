package egovframework.external.publicdata.collector;

import egovframework.external.publicdata.collector.KmaBaseTimeCalculator.BaseTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * weather-api.docx "예보 발표시각" 규칙에 맞게 base_date/base_time을 계산하는지 검증.
 * 특히 발표 직후(제공지연 이전)에는 이전 슬롯을 써야 하는 경계값들을 확인.
 */
class KmaBaseTimeCalculatorTest {

    @Test
    void 초단기실황_10분_이후면_이번_정시를_쓴다() {
        BaseTime bt = KmaBaseTimeCalculator.ultraSrtNcst(LocalDateTime.of(2026, 8, 5, 14, 10));
        assertThat(bt.baseDate()).isEqualTo("20260805");
        assertThat(bt.baseTime()).isEqualTo("1400");
    }

    @Test
    void 초단기실황_10분_이전이면_직전_정시를_쓴다() {
        BaseTime bt = KmaBaseTimeCalculator.ultraSrtNcst(LocalDateTime.of(2026, 8, 5, 14, 9));
        assertThat(bt.baseTime()).isEqualTo("1300");
    }

    @Test
    void 초단기실황_자정_경계에서_날짜가_바뀐다() {
        BaseTime bt = KmaBaseTimeCalculator.ultraSrtNcst(LocalDateTime.of(2026, 8, 5, 0, 5));
        assertThat(bt.baseDate()).isEqualTo("20260804");
        assertThat(bt.baseTime()).isEqualTo("2300");
    }

    @Test
    void 초단기예보_45분_이후면_이번_30분_슬롯을_쓴다() {
        BaseTime bt = KmaBaseTimeCalculator.ultraSrtFcst(LocalDateTime.of(2026, 8, 5, 14, 45));
        assertThat(bt.baseTime()).isEqualTo("1430");
    }

    @Test
    void 초단기예보_45분_이전이면_직전_30분_슬롯을_쓴다() {
        BaseTime bt = KmaBaseTimeCalculator.ultraSrtFcst(LocalDateTime.of(2026, 8, 5, 14, 44));
        assertThat(bt.baseTime()).isEqualTo("1330");
    }

    @Test
    void 단기예보_발표_10분_이후면_해당_슬롯을_쓴다() {
        BaseTime bt = KmaBaseTimeCalculator.vilageFcst(LocalDateTime.of(2026, 8, 5, 8, 10));
        assertThat(bt.baseTime()).isEqualTo("0800");
    }

    @Test
    void 단기예보_슬롯_사이면_이전_슬롯을_쓴다() {
        BaseTime bt = KmaBaseTimeCalculator.vilageFcst(LocalDateTime.of(2026, 8, 5, 10, 59));
        assertThat(bt.baseTime()).isEqualTo("0800");
    }

    @Test
    void 단기예보_자정_직후_전날_23시_슬롯을_쓴다() {
        BaseTime bt = KmaBaseTimeCalculator.vilageFcst(LocalDateTime.of(2026, 8, 5, 1, 0));
        assertThat(bt.baseDate()).isEqualTo("20260804");
        assertThat(bt.baseTime()).isEqualTo("2300");
    }

    @Test
    void 단기예보_23시_발표_10분_전이면_직전날_23시가_아니라_당일_20시_슬롯을_쓴다() {
        BaseTime bt = KmaBaseTimeCalculator.vilageFcst(LocalDateTime.of(2026, 8, 5, 23, 5));
        assertThat(bt.baseDate()).isEqualTo("20260805");
        assertThat(bt.baseTime()).isEqualTo("2000");
    }
}
