package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * classpath:kma-facility-locations.csv에서 지역매칭용 {@link FacilityRegion}이 정상
 * 로딩되는지 검증.
 */
class FacilityRegionLoaderTest {

    @Test
    void 전국_교정기관_59개소가_모두_로딩된다() {
        List<FacilityRegion> regions = new FacilityRegionLoader().all();

        assertThat(regions).hasSize(59);
    }

    @Test
    void facilityId는_전부_고유하다() {
        List<FacilityRegion> regions = new FacilityRegionLoader().all();

        Set<String> ids = regions.stream().map(FacilityRegion::facilityId).collect(Collectors.toSet());

        assertThat(ids).hasSameSizeAs(regions);
    }

    @Test
    void regionKey는_공백이_없다() {
        List<FacilityRegion> regions = new FacilityRegionLoader().all();

        assertThat(regions).allSatisfy(r -> assertThat(r.regionKey()).doesNotContain(" "));
    }

    @Test
    void 특정_기관의_regionKey가_기대한_형태로_조립된다() {
        List<FacilityRegion> regions = new FacilityRegionLoader().all();

        FacilityRegion anyang = regions.stream()
            .filter(r -> r.facilityId().equals("1270782")) // 안양교도소
            .findFirst()
            .orElseThrow();

        assertThat(anyang.regionKey()).isEqualTo("경기도안양시동안구");
    }

    @Test
    void 행정구역_개편_반영본이_그대로_들어있다() {
        // 전남광주통합특별시/강원특별자치도/전북특별자치도 - terrain-rule-base-spec.md §7-6 참고
        List<FacilityRegion> regions = new FacilityRegionLoader().all();

        assertThat(regions).anySatisfy(r -> assertThat(r.regionKey()).startsWith("전남광주통합특별시"));
        assertThat(regions).anySatisfy(r -> assertThat(r.regionKey()).startsWith("강원특별자치도"));
    }
}
