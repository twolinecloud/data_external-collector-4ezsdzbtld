package egovframework.external.publicdata.collector;

/**
 * 기상청 격자좌표 조회 대상 지점 하나. {@code facilityId}는 법무부 교정본부 공식 교정기관코드
 * (CORR_INSTT_CD, 예: {@code 1270254}=서울지방교정청) - 2026-08-14부터 자체 채번(f01~f59)
 * 대신 이 공식 코드를 그대로 씀(private-doc/CORR_INSTT_CD.csv 기준, task-spec 35번 항목).
 * classpath:kma-facility-locations.csv 에서 그대로 읽어오므로 행 순서와는 무관 - 자체 채번이던
 * 시절과 달리 행을 어디에 추가하든 상관없음.
 */
public record Location(String facilityId, String facilityName, String nx, String ny) {
}
