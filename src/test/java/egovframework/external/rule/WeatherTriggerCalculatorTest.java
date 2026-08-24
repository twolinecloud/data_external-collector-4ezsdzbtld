package egovframework.external.rule;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherTriggerCalculatorTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 8, 18, 12, 0);

    private static HourlyPrecipitation at(int hoursBeforeT0, double mm) {
        return new HourlyPrecipitation(T0.minusHours(hoursBeforeT0), mm);
    }

    @Test
    void 빈_목록이면_NONE이다() {
        assertThat(WeatherTriggerCalculator.calculate(List.of())).isEqualTo(WeatherTrigger.NONE);
    }

    @Test
    void 비가_안오면_NONE이다() {
        List<HourlyPrecipitation> readings = List.of(at(2, 0), at(1, 0), at(0, 0));

        assertThat(WeatherTriggerCalculator.calculate(readings)).isEqualTo(WeatherTrigger.NONE);
    }

    @Test
    void 최근_1시간_30mm_이상이면_RAIN_INTENSE() {
        List<HourlyPrecipitation> readings = List.of(at(2, 0), at(1, 0), at(0, 30.0));

        assertThat(WeatherTriggerCalculator.calculate(readings)).isEqualTo(WeatherTrigger.RAIN_INTENSE);
    }

    @Test
    void 최근_3시간_누적_60mm_이상이면_RAIN_WARN() {
        List<HourlyPrecipitation> readings = List.of(at(2, 20.0), at(1, 20.0), at(0, 20.0));

        assertThat(WeatherTriggerCalculator.calculate(readings)).isEqualTo(WeatherTrigger.RAIN_WARN);
    }

    @Test
    void 최근_12시간_누적_110mm_이상이면_3시간이_안차도_RAIN_WARN() {
        List<HourlyPrecipitation> readings = List.of(
            at(11, 10), at(10, 10), at(9, 10), at(8, 10), at(7, 10),
            at(6, 10), at(5, 10), at(4, 10), at(3, 10), at(2, 10), at(1, 10), at(0, 10)); // 12*10=120mm, 최근 3h=30mm(주의보 미만)

        assertThat(WeatherTriggerCalculator.calculate(readings)).isEqualTo(WeatherTrigger.RAIN_WARN);
    }

    @Test
    void 최근_3시간_누적_90mm_이상이면_RAIN_ALERT() {
        List<HourlyPrecipitation> readings = List.of(at(2, 30.0), at(1, 30.0), at(0, 30.0));

        assertThat(WeatherTriggerCalculator.calculate(readings)).isEqualTo(WeatherTrigger.RAIN_ALERT);
    }

    @Test
    void 시간_범위_밖의_강수는_누적에서_제외된다() {
        // 13시간 전 폭우는 12시간 누적에 안 들어가야 함
        List<HourlyPrecipitation> readings = List.of(at(13, 200.0), at(1, 0), at(0, 0));

        assertThat(WeatherTriggerCalculator.calculate(readings)).isEqualTo(WeatherTrigger.NONE);
    }

    @Test
    void 결측_시간대가_있어도_있는_값만으로_계산한다() {
        // 3시간 전~1시간 전 데이터가 비어있어도(수집 실패 등) 있는 값만으로 판단
        List<HourlyPrecipitation> readings = List.of(at(0, 35.0));

        assertThat(WeatherTriggerCalculator.calculate(readings)).isEqualTo(WeatherTrigger.RAIN_INTENSE);
    }
}
