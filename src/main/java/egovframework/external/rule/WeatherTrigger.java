package egovframework.external.rule;

/**
 * 기상 격자 데이터로 자체 계산하는 강수 트리거 (terrain-rule-base-spec.md §3-1).
 * 기상청 호우특보 발표기준을 시설 격자 단위로 앞당겨 적용한 것 - 산림청 공식 산사태 기준
 * (토양함수지수)을 재현한 게 아니라 호우 기준을 차용한 대리지표임(§7-2 참고).
 *
 * <p>선언 순서(NONE &lt; RAIN_INTENSE &lt; RAIN_WARN &lt; RAIN_ALERT)가 심각도 순서이자
 * {@link HazardAlertMatrix}의 열 인덱스로 그대로 쓰인다 - 순서를 바꾸면 매트릭스도 같이
 * 바꿔야 한다.</p>
 */
public enum WeatherTrigger {
    /** 트리거 없음. */
    NONE,
    /** 1시간 강수량 ≥ 30mm (단시간 집중호우 - 자체 기준). */
    RAIN_INTENSE,
    /** 호우주의보 기준: 3시간 ≥ 60mm 또는 12시간 ≥ 110mm. */
    RAIN_WARN,
    /** 호우경보 기준: 3시간 ≥ 90mm 또는 12시간 ≥ 180mm. */
    RAIN_ALERT
}
