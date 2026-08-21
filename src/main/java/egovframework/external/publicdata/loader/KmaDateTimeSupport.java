package egovframework.external.publicdata.loader;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 기상청 날짜(yyyyMMdd)+시각(HHmm) 조합 필드를 {@link LocalDateTime}으로 합치는 공용 헬퍼. */
final class KmaDateTimeSupport {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private KmaDateTimeSupport() {
    }

    static LocalDateTime combine(String date, String time) {
        return LocalDateTime.parse(date + time, FMT);
    }
}
