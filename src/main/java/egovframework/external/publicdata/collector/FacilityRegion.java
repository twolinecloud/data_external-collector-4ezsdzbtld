package egovframework.external.publicdata.collector;

/**
 * 교정기관 하나의 지역 매칭 키. {@code regionKey}는 {@code sido+sigungu}를 공백 없이 그대로
 * 이어붙인 문자열(예: {@code "경기도의왕시"}) - 재난문자 수신지역명 매칭(DisasterMsgCleanser,
 * cleanse-db-schema-spec.md §4.2)에서 startsWith 비교의 기준값으로 쓴다.
 */
public record FacilityRegion(String facilityId, String regionKey) {
}
