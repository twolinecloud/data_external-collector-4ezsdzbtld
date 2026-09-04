package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code airkorea-station-facility.csv}(교정기관 59개소 - 최근접 에어코리아 측정소, Haversine
 * 계산, 2026-09-04) 로딩 검증. 실제 API로 확인된 값(측정소정보 조회서비스 승인 후 산출)을
 * 표본 지점으로 고정.
 */
class AirKoreaStationFacilityLoaderTest {

    private final List<AirKoreaStationFacility> all = new AirKoreaStationFacilityLoader().all();

    @Test
    void 교정기관_59개소_전부_매핑된다() {
        assertThat(all).hasSize(59);
    }

    @Test
    void facilityId에_중복이_없다() {
        assertThat(all).extracting(AirKoreaStationFacility::facilityId).doesNotHaveDuplicates();
    }

    @Test
    void 거리가_ASOS보다_훨씬_가깝다() {
        // 측정소 밀도가 ASOS(97개)보다 훨씬 높아서(673개) 최근접 거리도 더 가깝다 - 실측 평균
        // 4.1km, 최대 13.6km. 여기서 크게 벗어나면 좌표 순서(dmX=경도/dmY=위도)가 잘못됐을 수 있다.
        double avg = all.stream().mapToDouble(AirKoreaStationFacility::distanceKm).average().orElseThrow();

        assertThat(avg).isBetween(0.5, 15.0);
        assertThat(all).allSatisfy(m -> assertThat(m.distanceKm()).isBetween(0.0, 30.0));
    }

    @Test
    void 서울지방교정청은_별양동_측정소에_매핑된다() {
        // 실측 고정값 - 2026-09-04 getMsrstnList 응답 기준.
        AirKoreaStationFacility 서울지방교정청 = find("1270254");

        assertThat(서울지방교정청.stationName()).isEqualTo("별양동");
    }

    private AirKoreaStationFacility find(String facilityId) {
        return all.stream()
            .filter(m -> m.facilityId().equals(facilityId))
            .findFirst()
            .orElseThrow();
    }
}
