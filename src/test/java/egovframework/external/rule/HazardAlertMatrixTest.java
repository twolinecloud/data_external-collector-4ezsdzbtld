package egovframework.external.rule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** terrain-rule-base-spec.md §4.1 매트릭스를 그대로 고정. */
class HazardAlertMatrixTest {

    @Test
    void LOW_취약도_기본_매트릭스() {
        assertThat(HazardAlertMatrix.evaluate(VulnerabilityGrade.LOW, WeatherTrigger.NONE, false)).isEqualTo(AlertLevel.NONE);
        assertThat(HazardAlertMatrix.evaluate(VulnerabilityGrade.LOW, WeatherTrigger.RAIN_INTENSE, false)).isEqualTo(AlertLevel.NONE);
        assertThat(HazardAlertMatrix.evaluate(VulnerabilityGrade.LOW, WeatherTrigger.RAIN_WARN, false)).isEqualTo(AlertLevel.INFO);
        assertThat(HazardAlertMatrix.evaluate(VulnerabilityGrade.LOW, WeatherTrigger.RAIN_ALERT, false)).isEqualTo(AlertLevel.CAUTION);
    }

    @Test
    void MEDIUM_취약도_기본_매트릭스() {
        assertThat(HazardAlertMatrix.evaluate(VulnerabilityGrade.MEDIUM, WeatherTrigger.NONE, false)).isEqualTo(AlertLevel.NONE);
        assertThat(HazardAlertMatrix.evaluate(VulnerabilityGrade.MEDIUM, WeatherTrigger.RAIN_INTENSE, false)).isEqualTo(AlertLevel.INFO);
        assertThat(HazardAlertMatrix.evaluate(VulnerabilityGrade.MEDIUM, WeatherTrigger.RAIN_WARN, false)).isEqualTo(AlertLevel.CAUTION);
        assertThat(HazardAlertMatrix.evaluate(VulnerabilityGrade.MEDIUM, WeatherTrigger.RAIN_ALERT, false)).isEqualTo(AlertLevel.WATCH);
    }

    @Test
    void HIGH_취약도_기본_매트릭스() {
        assertThat(HazardAlertMatrix.evaluate(VulnerabilityGrade.HIGH, WeatherTrigger.NONE, false)).isEqualTo(AlertLevel.NONE);
        assertThat(HazardAlertMatrix.evaluate(VulnerabilityGrade.HIGH, WeatherTrigger.RAIN_INTENSE, false)).isEqualTo(AlertLevel.CAUTION);
        assertThat(HazardAlertMatrix.evaluate(VulnerabilityGrade.HIGH, WeatherTrigger.RAIN_WARN, false)).isEqualTo(AlertLevel.WATCH);
        assertThat(HazardAlertMatrix.evaluate(VulnerabilityGrade.HIGH, WeatherTrigger.RAIN_ALERT, false)).isEqualTo(AlertLevel.SEVERE);
    }

    @Test
    void 지역신호가_있으면_한단계_상향된다() {
        assertThat(HazardAlertMatrix.evaluate(VulnerabilityGrade.MEDIUM, WeatherTrigger.RAIN_WARN, true)).isEqualTo(AlertLevel.WATCH);
    }

    @Test
    void 취약도_LOW에_기상트리거_없이_지역신호만_있으면_정보로_억제된다() {
        // §4.2 핵심: "없음"에서 한 단계만 올라가 "정보"가 된다 - 별도 억제 규칙이 필요 없음을 검증
        assertThat(HazardAlertMatrix.evaluate(VulnerabilityGrade.LOW, WeatherTrigger.NONE, true)).isEqualTo(AlertLevel.INFO);
    }

    @Test
    void 이미_심각이면_지역신호가_있어도_더_올라가지_않는다() {
        assertThat(HazardAlertMatrix.evaluate(VulnerabilityGrade.HIGH, WeatherTrigger.RAIN_ALERT, true)).isEqualTo(AlertLevel.SEVERE);
    }

    @Test
    void 강원권_실제_사례_영월_춘천_강원북부_같은_경보에_다른_등급() {
        // terrain-rule-base-spec.md §4.3 예시 - 영월(HIGH)/춘천(MEDIUM)/강원북부(LOW)가
        // 같은 산사태 주의보+RAIN_WARN 상황에서 서로 다른 등급을 받아야 한다
        boolean regionLandslideWarning = true;

        assertThat(HazardAlertMatrix.evaluate(VulnerabilityGrade.HIGH, WeatherTrigger.RAIN_WARN, regionLandslideWarning))
            .isEqualTo(AlertLevel.SEVERE); // 영월교도소
        assertThat(HazardAlertMatrix.evaluate(VulnerabilityGrade.MEDIUM, WeatherTrigger.RAIN_WARN, regionLandslideWarning))
            .isEqualTo(AlertLevel.WATCH); // 춘천교도소
        assertThat(HazardAlertMatrix.evaluate(VulnerabilityGrade.LOW, WeatherTrigger.RAIN_WARN, regionLandslideWarning))
            .isEqualTo(AlertLevel.CAUTION); // 강원북부교도소
    }
}
