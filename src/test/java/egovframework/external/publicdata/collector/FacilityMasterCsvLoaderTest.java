package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FacilityMasterCsvLoaderTest {

    @Test
    void 전국_교정기관_59개소가_모두_로딩된다() {
        assertThat(new FacilityMasterCsvLoader().all()).hasSize(59);
    }

    @Test
    void 특정_기관이_기대한_값으로_로딩된다() {
        List<FacilityMasterRecord> all = new FacilityMasterCsvLoader().all();

        FacilityMasterRecord seoul = all.stream()
            .filter(r -> r.facilityId().equals("1270254"))
            .findFirst()
            .orElseThrow();

        assertThat(seoul.facilityName()).isEqualTo("서울지방교정청");
        assertThat(seoul.sido()).isEqualTo("경기도");
        assertThat(seoul.nx()).isEqualTo("60");
        assertThat(seoul.ny()).isEqualTo("124");
    }
}
