package egovframework.external.rule;

/**
 * rule-base가 판정하는 재해 종류 (terrain-rule-base-spec.md §2.2/§2.3, §8-HIGH_WAVE).
 *
 * <p>{@code HIGH_WAVE}(풍랑/폭풍해일, 2026-08-24 추가)는 LANDSLIDE/FLOOD와 달리 자체 계산하는
 * 동적 트리거({@link WeatherTrigger})가 없다 - 강수 기반 트리거를 재사용하는 건 의미가 안 맞아서
 * (파고/풍속과 강수량은 무관) 항상 {@code WeatherTrigger.NONE}으로 두고, 기상특보/재난문자
 * 지역신호(regionTriggered)만으로 판단한다. 정적 취약도(해안 인접 여부)도 아직 시설별로
 * 구분 안 하고 전부 MEDIUM 균일 적용(§8 참고) - 지역신호 출처 자체가 이미 연안 중심으로
 * 발표되므로 과대평가 위험은 낮다고 보고 시작한 잠정치.</p>
 */
public enum HazardType {
    LANDSLIDE, FLOOD, HIGH_WAVE
}
