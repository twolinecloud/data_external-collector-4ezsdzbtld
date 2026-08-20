package egovframework.external.rule;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 강수 관측/예보 목록에서 {@link WeatherTrigger}를 계산한다 (terrain-rule-base-spec.md §3-1).
 *
 * <p>가장 최근 시각을 기준으로 지난 1/3/12시간 누적을 구한다. 매 시각이 정확히 존재한다고
 * 가정하지 않고 시간 구간으로 걸러서 합산 - 결측 시간대(수집 실패 등)가 있어도 있는 값만으로
 * 계산이 되도록 하기 위함. 관측(실황)과 예보 값을 섞어 넣을지는 호출자가 결정한다(§5-2) - 이
 * 계산기는 주어진 목록만 갖고 판단한다.</p>
 */
public final class WeatherTriggerCalculator {

    private static final double INTENSE_1H = 30.0;
    private static final double WARN_3H = 60.0;
    private static final double WARN_12H = 110.0;
    private static final double ALERT_3H = 90.0;
    private static final double ALERT_12H = 180.0;

    private WeatherTriggerCalculator() {
    }

    public static WeatherTrigger calculate(List<HourlyPrecipitation> readings) {
        if (readings.isEmpty()) {
            return WeatherTrigger.NONE;
        }

        LocalDateTime latest = readings.stream()
            .map(HourlyPrecipitation::time)
            .max(Comparator.naturalOrder())
            .orElseThrow();

        double sum1h = sumWithin(readings, latest.minusHours(1), latest);
        double sum3h = sumWithin(readings, latest.minusHours(3), latest);
        double sum12h = sumWithin(readings, latest.minusHours(12), latest);

        if (sum3h >= ALERT_3H || sum12h >= ALERT_12H) {
            return WeatherTrigger.RAIN_ALERT;
        }
        if (sum3h >= WARN_3H || sum12h >= WARN_12H) {
            return WeatherTrigger.RAIN_WARN;
        }
        if (sum1h >= INTENSE_1H) {
            return WeatherTrigger.RAIN_INTENSE;
        }
        return WeatherTrigger.NONE;
    }

    private static double sumWithin(List<HourlyPrecipitation> readings, LocalDateTime fromExclusive, LocalDateTime toInclusive) {
        return readings.stream()
            .filter(r -> r.time().isAfter(fromExclusive) && !r.time().isAfter(toInclusive))
            .mapToDouble(HourlyPrecipitation::mm)
            .sum();
    }
}
