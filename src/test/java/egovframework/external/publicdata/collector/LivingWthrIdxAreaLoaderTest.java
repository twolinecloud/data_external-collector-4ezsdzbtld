package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2026-08-24 실제 서비스키 호출로 확인한 areaNo 값(data.go.kr 참고문서 zip의
 * dfs-zone-tree_excel_20260701.xlsx 기준)을 그대로 고정.
 */
class LivingWthrIdxAreaLoaderTest {

    @Test
    void 시도_16개가_모두_로딩된다() {
        assertThat(new LivingWthrIdxAreaLoader().all()).hasSize(16);
    }

    @Test
    void 서울특별시_areaNo는_1100000000이다() {
        LivingWthrIdxArea seoul = find("서울특별시");

        assertThat(seoul.areaNo()).isEqualTo("1100000000");
    }

    @Test
    void 시도명이_시설_CSV_표기와_일치한다() {
        // FacilitySidoLoader의 sido 값과 문자열이 정확히 같아야 매칭이 되므로 실제 값을 고정
        List<LivingWthrIdxArea> all = new LivingWthrIdxAreaLoader().all();
        List<FacilitySido> facilities = new FacilitySidoLoader(new CsvFacilityMasterSource(new FacilityMasterCsvLoader())).all();

        for (FacilitySido facility : facilities) {
            assertThat(all).anyMatch(a -> a.sido().equals(facility.sido()));
        }
    }

    private LivingWthrIdxArea find(String sido) {
        return new LivingWthrIdxAreaLoader().all().stream()
            .filter(a -> a.sido().equals(sido))
            .findFirst()
            .orElseThrow();
    }
}
