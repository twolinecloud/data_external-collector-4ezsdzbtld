package egovframework.external.service;

import egovframework.external.exception.InvalidParameterException;
import egovframework.external.publicdata.collector.KmaBaseTimeCalculator;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static egovframework.external.service.KmaBackfillService.ULTRA_SRT_FCST;
import static egovframework.external.service.KmaBackfillService.ULTRA_SRT_NCST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmaBackfillServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 11, 5);

    private static LocalDateTime t(int hour, int minute) {
        return LocalDateTime.of(2026, 11, 10, hour, minute);
    }

    @Test
    void 초단기실황_06시부터_10시까지는_정각_다섯_시각이다() {
        List<LocalDateTime> baseTimes = KmaBackfillService.baseTimes(ULTRA_SRT_NCST, t(6, 0), t(10, 0), NOW);

        assertThat(baseTimes).containsExactly(t(6, 0), t(7, 0), t(8, 0), t(9, 0), t(10, 0));
    }

    @Test
    void 분_이하는_버리고_초단기예보는_각_시의_30분_발표분이다() {
        List<LocalDateTime> baseTimes = KmaBackfillService.baseTimes(ULTRA_SRT_FCST, t(6, 55), t(9, 5), NOW);

        assertThat(baseTimes).containsExactly(t(6, 30), t(7, 30), t(8, 30), t(9, 30));
    }

    @Test
    void 호출_시각으로_계산한_기준시각이_보충하려던_발표시각과_같다() {
        for (LocalDateTime published : KmaBackfillService.baseTimes(ULTRA_SRT_NCST, t(0, 0), t(10, 0), NOW)) {
            var base = KmaBaseTimeCalculator.ultraSrtNcst(KmaBackfillService.requestedAt(ULTRA_SRT_NCST, published));
            assertThat(base.baseDate() + base.baseTime())
                .isEqualTo(String.format("20261110%02d00", published.getHour()));
        }
        for (LocalDateTime published : KmaBackfillService.baseTimes(ULTRA_SRT_FCST, t(0, 0), t(10, 0), NOW)) {
            var base = KmaBaseTimeCalculator.ultraSrtFcst(KmaBackfillService.requestedAt(ULTRA_SRT_FCST, published));
            assertThat(base.baseDate() + base.baseTime())
                .isEqualTo(String.format("20261110%02d30", published.getHour()));
        }
    }

    @Test
    void 아직_제공_전인_시각은_거절한다() {
        // 11:05 - 11시 실황은 11:10부터, 10시 30분 예보는 10:45부터 조회 가능
        assertThatThrownBy(() -> KmaBackfillService.baseTimes(ULTRA_SRT_NCST, t(10, 0), t(11, 0), NOW))
            .isInstanceOf(InvalidParameterException.class);
        assertThat(KmaBackfillService.baseTimes(ULTRA_SRT_FCST, t(10, 0), t(10, 0), NOW)).containsExactly(t(10, 30));
        assertThatThrownBy(() -> KmaBackfillService.baseTimes(ULTRA_SRT_FCST, t(11, 0), t(11, 0), NOW))
            .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void 잘못된_입력은_거절한다() {
        assertThatThrownBy(() -> KmaBackfillService.baseTimes("kma-asos-hourly", t(6, 0), t(7, 0), NOW))
            .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> KmaBackfillService.baseTimes(ULTRA_SRT_NCST, t(8, 0), t(6, 0), NOW))
            .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> KmaBackfillService.baseTimes(ULTRA_SRT_NCST, null, t(6, 0), NOW))
            .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> KmaBackfillService.baseTimes(ULTRA_SRT_NCST, t(10, 0).minusDays(2), t(10, 0), NOW))
            .isInstanceOf(InvalidParameterException.class); // 25시간 초과
    }
}
