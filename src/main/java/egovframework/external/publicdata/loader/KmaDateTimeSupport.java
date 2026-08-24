package egovframework.external.publicdata.loader;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 기상청 날짜(yyyyMMdd)+시각(HHmm) 조합 필드를 {@link LocalDateTime}으로 합치는 공용 헬퍼. */
final class KmaDateTimeSupport {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    // 생활기상지수(자외선/대기정체지수) 전용 - 분 없이 10자리(yyyyMMddHH), 위 FMT와 다른 포맷.
    private static final DateTimeFormatter YYYYMMDDHH_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private KmaDateTimeSupport() {
    }

    static LocalDateTime combine(String date, String time) {
        return LocalDateTime.parse(date + time, FMT);
    }

    /** @param yyyyMMddHH 생활기상지수 API의 {@code date} 필드 형식(분 없음, 예: "2026082412") */
    static LocalDateTime parseYyyyMMddHH(String yyyyMMddHH) {
        return LocalDateTime.parse(yyyyMMddHH, YYYYMMDDHH_FMT);
    }
}
