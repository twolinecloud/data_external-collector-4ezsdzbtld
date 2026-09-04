package egovframework.external.publicdata.collector;

/**
 * 교정기관 하나가 황사(PM10) 유도에 참조할 <b>최근접 에어코리아 측정소</b> 1개.
 * {@code classpath:airkorea-station-facility.csv}(59건)의 행 하나 - {@link AirKoreaStationFacilityLoader}
 * 참고.
 *
 * <p>매칭 키가 {@code stationName}(측정소명)인 이유 - {@link AirKoreaRealtimeCollector}가 수집하는
 * 실시간 측정정보(getCtprvnRltmMesureDnsty) 응답에는 측정소 고유번호(stationCode)가 없고
 * {@code stationName}만 있다. 측정소정보(getMsrstnList) 전량(673개) 실측 결과 이름 중복이
 * 하나도 없어 전국 단위로 유일한 키로 써도 안전하다(2026-09-04 확인) - 이름이 겹치는 순간
 * 이 가정이 깨지므로, 측정소 목록이 늘어나면(신규 측정소 추가) 재확인이 필요하다.</p>
 */
public record AirKoreaStationFacility(String facilityId, String stationName, double distanceKm) {
}
