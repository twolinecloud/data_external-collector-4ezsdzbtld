package egovframework.external.publicdata.collector;

/**
 * 교정기관 하나가 안개/박무/연무 유도에 참조할 <b>최근접 ASOS 지점</b> 1개.
 * {@code classpath:kma-asos-station-facility.csv}(59건)의 행 하나 - {@link KmaAsosStationFacilityLoader}
 * 참고. {@code distanceKm}은 판정 로직이 쓰는 값은 아니고, 시정·습도가 실제 기관 위치가 아니라
 * 최근접 관측소 값이라는 걸 추적할 수 있도록 원본에 남겨둔다.
 */
public record AsosStationFacility(String facilityId, String stnId, String stnName, double distanceKm) {
}
