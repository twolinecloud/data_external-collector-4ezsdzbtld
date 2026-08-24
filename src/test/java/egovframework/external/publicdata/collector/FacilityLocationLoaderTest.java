package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * classpath:kma-facility-locations.csv(전국 교정기관 59개소)가 정상 로딩되는지 검증.
 * 이 CSV는 실제 운영 데이터라 개수/형식이 깨지면 수집 자체가 안 되므로
 * 기본적인 무결성(개수, 중복 없음, nx/ny 숫자 형식)을 테스트로 고정해둔다.
 */
class FacilityLocationLoaderTest {

    @Test
    void 전국_교정기관_59개소가_모두_로딩된다() {
        List<Location> locations = new FacilityLocationLoader().all();

        assertThat(locations).hasSize(59);
    }

    @Test
    void facilityId는_전부_고유하다() {
        List<Location> locations = new FacilityLocationLoader().all();

        Set<String> ids = locations.stream().map(Location::facilityId).collect(Collectors.toSet());

        assertThat(ids).hasSameSizeAs(locations);
    }

    @Test
    void nx_ny는_숫자_형식이다() {
        List<Location> locations = new FacilityLocationLoader().all();

        assertThat(locations).allSatisfy(loc -> {
            assertThat(loc.nx()).matches("\\d+");
            assertThat(loc.ny()).matches("\\d+");
        });
    }

    @Test
    void 특정_기관이_기대한_격자좌표로_로딩된다() {
        // 경기도 과천시 대표좌표. facilityId 채번 규칙이 바뀔 수 있으므로 기관명으로
        // 찾는다(f01 같은 특정 ID를 고정 가정하지 않음).
        List<Location> locations = new FacilityLocationLoader().all();

        Location seoul = locations.stream()
            .filter(loc -> loc.facilityName().equals("서울지방교정청"))
            .findFirst()
            .orElseThrow();

        assertThat(seoul.nx()).isEqualTo("60");
        assertThat(seoul.ny()).isEqualTo("124");
    }
}
