package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code airkorea-dust-forecast-facility.csv}(교정기관 59개소 - 대기질예보통보 예보권역
 * 매핑, 2026-09-04) 로딩 검증. 예보권역 이름이 실제 API 응답(19종)과 정확히 일치하는지가
 * 핵심 - 하나라도 어긋나면 그 시설은 정제 단계에서 조용히 빠진다.
 */
class AirKoreaDustForecastFacilityLoaderTest {

    /** 2026-09-04 실측 응답(informGrade)에 등장한 19개 예보권역 - 이 밖의 값이 매핑에 있으면 안 된다. */
    private static final Set<String> KNOWN_REGIONS = Set.of(
        "서울", "제주", "전남", "전북", "광주", "경남", "경북", "울산", "대구", "부산",
        "충남", "충북", "세종", "대전", "영동", "영서", "경기남부", "경기북부", "인천");

    private final List<AirKoreaDustForecastFacility> all = new AirKoreaDustForecastFacilityLoader().all();

    @Test
    void 교정기관_59개소_전부_매핑된다() {
        assertThat(all).hasSize(59);
    }

    @Test
    void facilityId에_중복이_없다() {
        assertThat(all).extracting(AirKoreaDustForecastFacility::facilityId).doesNotHaveDuplicates();
    }

    @Test
    void 예보권역_이름이_실측_19종_안에서만_쓰인다() {
        Set<String> used = all.stream().map(AirKoreaDustForecastFacility::informRegion).collect(Collectors.toSet());
        assertThat(KNOWN_REGIONS).containsAll(used);
    }

    @Test
    void 경기도는_남부_북부로_분할되고_강원은_영동_영서로_분할된다() {
        // 경기도/강원특별자치도는 시도 하나가 informGrade의 서로 다른 두 지역명에 대응하므로,
        // 시군구 기준 분할 로직이 실제로 두 값 다 만들어내는지 확인.
        Set<String> regions = all.stream().map(AirKoreaDustForecastFacility::informRegion).collect(Collectors.toSet());
        assertThat(regions).contains("경기남부", "경기북부", "영동", "영서", "광주", "전남");
    }

    @Test
    void 의정부교도소는_경기북부다() {
        // 경기도 시설 중 유일하게 북부로 분류되는 실측 고정값.
        AirKoreaDustForecastFacility 의정부 = find("1270785");

        assertThat(의정부.informRegion()).isEqualTo("경기북부");
    }

    @Test
    void 강릉교도소는_영동이고_춘천교도소는_영서다() {
        assertThat(find("1270788").informRegion()).isEqualTo("영동"); // 강릉(동해안)
        assertThat(find("1270786").informRegion()).isEqualTo("영서"); // 춘천(내륙)
    }

    private AirKoreaDustForecastFacility find(String facilityId) {
        return all.stream()
            .filter(m -> m.facilityId().equals(facilityId))
            .findFirst()
            .orElseThrow();
    }
}
