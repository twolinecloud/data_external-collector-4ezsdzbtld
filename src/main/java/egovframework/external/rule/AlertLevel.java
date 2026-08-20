package egovframework.external.rule;

/**
 * 최종 알림 등급 (terrain-rule-base-spec.md §4.1 매트릭스 결과값).
 * 선언 순서가 심각도 순서 - {@link HazardAlertMatrix}가 "한 단계 상향"을 ordinal+1로 구현한다.
 */
public enum AlertLevel {
    NONE("없음"),
    INFO("정보"),
    CAUTION("주의"),
    WATCH("경계"),
    SEVERE("심각");

    private final String label;

    AlertLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
