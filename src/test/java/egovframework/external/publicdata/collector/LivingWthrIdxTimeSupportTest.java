package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LivingWthrIdxTimeSupportTest {

    @Test
    void 정각_발표시각이면_그대로_반환한다() {
        String time = LivingWthrIdxTimeSupport.latestIssuedTime(LocalDateTime.of(2026, 8, 24, 12, 0));

        assertThat(time).isEqualTo("2026082412");
    }

    @Test
    void 발표시각_사이면_가장_최근_발표시각으로_내림한다() {
        String time = LivingWthrIdxTimeSupport.latestIssuedTime(LocalDateTime.of(2026, 8, 24, 14, 37));

        assertThat(time).isEqualTo("2026082412");
    }

    @Test
    void 자정_직후면_0시_발표로_내림한다() {
        String time = LivingWthrIdxTimeSupport.latestIssuedTime(LocalDateTime.of(2026, 8, 24, 1, 59));

        assertThat(time).isEqualTo("2026082400");
    }

    @Test
    void 밤_21시_이후면_21시_발표를_쓴다() {
        String time = LivingWthrIdxTimeSupport.latestIssuedTime(LocalDateTime.of(2026, 8, 24, 23, 30));

        assertThat(time).isEqualTo("2026082421");
    }
}
