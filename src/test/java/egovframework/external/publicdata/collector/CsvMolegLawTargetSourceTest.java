package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvMolegLawTargetSourceTest {

    @Test
    void CSV_로더의_목록을_그대로_반환한다() {
        CsvMolegLawTargetSource source = new CsvMolegLawTargetSource(new MolegLawListLoader());

        List<MolegLaw> laws = source.current();

        assertThat(laws).hasSize(491);
    }
}
