package egovframework.external.publicdata.collector;

/**
 * 교정기관 하나가 황사(PM10) 예보 조회에 참조할 <b>대기질 예보권역</b> 1개.
 * {@code classpath:airkorea-dust-forecast-facility.csv}(59건)의 행 하나 -
 * {@link AirKoreaDustForecastFacilityLoader} 참고.
 *
 * <p>예보권역은 시도보다 거칠거나(예: 서울=전국 통보문과 무관하게 시도 그대로) 더 세밀하다
 * (경기도→경기남부/경기북부, 강원특별자치도→영동/영서, 전남광주통합특별시→광주/전남로 분할).
 * 분할 3개는 공식 기상 예보구역 관례를 시군구 기준으로 수동 판정했다 - 실측 API가 아니라
 * 지리적 관례에 기반한 값이라 {@link AsosStationFacility}/{@link AirKoreaStationFacility}
 * (좌표 계산 기반)보다 신뢰도가 낮다는 걸 알아둬야 한다(2026-09-04).</p>
 */
public record AirKoreaDustForecastFacility(String facilityId, String informRegion) {
}
