package egovframework.external.publicdata.collector;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * 기상청 단기예보 API의 발표시각(base_date/base_time) 계산.
 *
 * <p>각 오퍼레이션은 정해진 시각에만 발표되고, 발표 후 일정 시간(제공지연)이 지나야 실제로
 * 조회 가능하다 (weather-api.docx "예보 발표시각" 참고). 이 클래스는 "지금 호출한다면 조회
 * 가능한 가장 최신 base_date/base_time"을 계산한다.</p>
 */
public final class KmaBaseTimeCalculator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int[] VILAGE_FCST_HOURS = {2, 5, 8, 11, 14, 17, 20, 23};

    private KmaBaseTimeCalculator() {
    }

    public record BaseTime(String baseDate, String baseTime) {
    }

    /** getUltraSrtNcst(초단기실황): 매시 정각 발표, 10분 이후 제공. */
    public static BaseTime ultraSrtNcst(LocalDateTime now) {
        LocalDateTime slot = (now.getMinute() < 10 ? now.minusHours(1) : now)
            .withMinute(0).withSecond(0).withNano(0);
        return new BaseTime(slot.format(DATE_FMT), String.format("%02d00", slot.getHour()));
    }

    /** getUltraSrtFcst(초단기예보): 매시 30분 발표, 45분 이후 제공. */
    public static BaseTime ultraSrtFcst(LocalDateTime now) {
        LocalDateTime slot = (now.getMinute() < 45 ? now.minusHours(1) : now)
            .withMinute(30).withSecond(0).withNano(0);
        return new BaseTime(slot.format(DATE_FMT), String.format("%02d30", slot.getHour()));
    }

    /** getVilageFcst(단기예보): 1일 8회(02/05/08/11/14/17/20/23시) 발표, 10분 이후 제공. */
    public static BaseTime vilageFcst(LocalDateTime now) {
        LocalDateTime best = null;
        for (LocalDate day : new LocalDate[]{now.toLocalDate(), now.toLocalDate().minusDays(1)}) {
            for (int hour : VILAGE_FCST_HOURS) {
                LocalDateTime slot = day.atTime(hour, 0);
                LocalDateTime availableAt = slot.plusMinutes(10);
                if (!availableAt.isAfter(now) && (best == null || slot.isAfter(best))) {
                    best = slot;
                }
            }
        }
        if (best == null) {
            // 이론상 도달 안 함(하루+어제 슬롯을 다 봤으므로 항상 하나는 있음) - 방어적 처리
            throw new IllegalStateException("vilageFcst base_time 계산 실패: now=" + now);
        }
        return new BaseTime(best.format(DATE_FMT), String.format("%02d00", best.getHour()));
    }

    static int[] vilageFcstHours() {
        return Arrays.copyOf(VILAGE_FCST_HOURS, VILAGE_FCST_HOURS.length);
    }
}
