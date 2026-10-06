package egovframework.external.dto;

/**
 * 관리자 서비스(admin-api)의 스케줄 즉시 반영 요청 본문.
 *
 * <p>스케줄 행 하나를 {@code (dataTypeCd, schdCd)}로 식별한다. schdCd는 세부 스케줄(날씨 오퍼레이션 등)에만 있고,
 * 없으면 그 데이터 구분의 대표 행이다. 이 서비스는 <b>activeYn만 반영</b>하고 execSchedTypeCd/schedVal은
 * 받기만 한다(실행 시각은 application.yml의 cron을 따른다).
 */
public record ScheduleRefreshRequest(
        String dataTypeCd,
        String schdCd,
        String activeYn,
        String execSchedTypeCd,
        String schedVal
) { }
