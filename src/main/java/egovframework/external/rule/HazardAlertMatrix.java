package egovframework.external.rule;

/**
 * 정적 취약도 × 동적 강수트리거 → 알림등급 매트릭스, 그리고 외부(지역) 신호에 의한 보정
 * (terrain-rule-base-spec.md §4.1/§4.2).
 *
 * <p>기본 매트릭스:</p>
 * <pre>
 *          NONE  RAIN_INTENSE  RAIN_WARN  RAIN_ALERT
 * LOW      없음   없음          정보        주의
 * MEDIUM   없음   정보          주의        경계
 * HIGH     없음   주의          경계        심각
 * </pre>
 *
 * <p>지역 신호({@code regionTriggered} - 재난문자/산림청 예보가 해당 재해로 이 지역에 발효 중)가
 * 있으면 한 단계 상향한다(§4.2). 취약도 LOW에 기상 트리거가 없는데 지역 신호만 있는 경우도 이
 * 규칙 하나로 자연스럽게 처리된다 - "없음"에서 한 단계 올라가면 "정보"가 되므로, 별도의
 * "억제" 규칙을 추가할 필요가 없다.</p>
 */
public final class HazardAlertMatrix {

    private static final AlertLevel[][] BASE = {
        // NONE            RAIN_INTENSE       RAIN_WARN            RAIN_ALERT
        {AlertLevel.NONE, AlertLevel.NONE, AlertLevel.INFO, AlertLevel.CAUTION},      // LOW
        {AlertLevel.NONE, AlertLevel.INFO, AlertLevel.CAUTION, AlertLevel.WATCH},     // MEDIUM
        {AlertLevel.NONE, AlertLevel.CAUTION, AlertLevel.WATCH, AlertLevel.SEVERE},   // HIGH
    };

    private HazardAlertMatrix() {
    }

    public static AlertLevel evaluate(VulnerabilityGrade vulnerability, WeatherTrigger weatherTrigger, boolean regionTriggered) {
        AlertLevel base = BASE[vulnerability.ordinal()][weatherTrigger.ordinal()];
        if (!regionTriggered) {
            return base;
        }
        AlertLevel[] levels = AlertLevel.values();
        return levels[Math.min(base.ordinal() + 1, levels.length - 1)];
    }
}
