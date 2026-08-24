package egovframework.external.publicdata.collector;

/**
 * 기상청 생활기상지수(자외선지수/대기정체지수) API의 시도 1개 단위 지점.
 *
 * <p>{@code areaNo}는 10자리 행정구역코드(법정동코드 계열) - 예: {@code 1100000000}=서울특별시.
 * 2026-08-24 data.go.kr "생활기상지수 조회서비스(4.0)_오픈API활용가이드" 참고문서 zip의
 * {@code dfs-zone-tree_excel_20260701.xlsx}에서 16개 시도 전체를 직접 확인, 실제 서비스키로
 * 서울(1100000000)/종로구(1111000000) 호출 결과가 동일함을 실측해서 이 지수가 시군구가 아니라
 * <b>시도 단위로 생산</b>됨을 확인했다 - 그래서 시군구 단위 매칭(기상특보의
 * {@code KmaWarningStation}처럼)이 필요 없고, 시설의 {@code sido} 값으로 이 표를 그대로
 * 조회하면 된다({@code kma-facility-locations.csv}의 {@code sido} 컬럼과 여기 값이 정확히
 * 1:1로 일치함을 확인).</p>
 */
public record LivingWthrIdxArea(String sido, String areaNo) {
}
