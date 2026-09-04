package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code kma-asos-station-facility.csv}(교정기관 59개소 - 최근접 ASOS 지점, Haversine 계산,
 * 2026-09-04) 로딩 검증. 실제 API로 확인된 값(기상청 API허브 {@code stn_inf.php} 승인 후
 * 산출)을 표본 지점으로 고정.
 */
class KmaAsosStationFacilityLoaderTest {

    private final List<AsosStationFacility> all = new KmaAsosStationFacilityLoader().all();

    @Test
    void 교정기관_59개소_전부_매핑된다() {
        assertThat(all).hasSize(59);
    }

    @Test
    void facilityId에_중복이_없다() {
        assertThat(all).extracting(AsosStationFacility::facilityId).doesNotHaveDuplicates();
    }

    @Test
    void 거리가_평균_10km_안팎으로_합리적이다() {
        // 전국 지점 평균 간격(약 67km)보다 훨씬 가깝다 - 교정기관이 대부분 도시 근처라서.
        // 여기서 벗어나면 좌표 단위나 Haversine 계산이 잘못됐을 가능성이 높다.
        double avg = all.stream().mapToDouble(AsosStationFacility::distanceKm).average().orElseThrow();

        assertThat(avg).isBetween(1.0, 30.0);
        assertThat(all).allSatisfy(m -> assertThat(m.distanceKm()).isBetween(0.0, 50.0));
    }

    @Test
    void 서울지방교정청은_서울_지점에_매핑된다() {
        // 실측 고정값 - stnId 108 = 서울(Seoul), 2026-09-03 stn_inf.php 응답.
        AsosStationFacility 서울지방교정청 = find("1270254");

        assertThat(서울지방교정청.stnId()).isEqualTo("108");
        assertThat(서울지방교정청.stnName()).isEqualTo("Seoul");
    }

    private AsosStationFacility find(String facilityId) {
        return all.stream()
            .filter(m -> m.facilityId().equals(facilityId))
            .findFirst()
            .orElseThrow();
    }
}
