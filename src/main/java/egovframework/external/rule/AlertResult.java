package egovframework.external.rule;

import java.time.LocalDateTime;
import java.util.Set;

/** 시설 1개소 × 재해 1종에 대한 rule-base 평가 결과 1건. */
public record AlertResult(
    String facilityId,
    HazardType hazardType,
    VulnerabilityGrade vulnerability,
    WeatherTrigger weatherTrigger,
    boolean regionTriggered,
    /** regionTriggered=true를 만든 출처 (감사/디버그용) - {@code disasterMsg}, {@code weatherWarning} 중 0개 이상. */
    Set<String> regionSources,
    AlertLevel level,
    LocalDateTime evaluatedAt
) {
}
