package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvFacilityMasterSourceTest {

    @Test
    void CSV_로더의_목록을_그대로_반환한다() {
        CsvFacilityMasterSource source = new CsvFacilityMasterSource(new FacilityMasterCsvLoader());

        assertThat(source.current()).hasSize(59);
    }
}
