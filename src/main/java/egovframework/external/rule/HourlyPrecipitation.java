package egovframework.external.rule;

import java.time.LocalDateTime;

/** 시각 1개에 대한 강수량(mm) 관측/예보 값. */
public record HourlyPrecipitation(LocalDateTime time, double mm) {
}
