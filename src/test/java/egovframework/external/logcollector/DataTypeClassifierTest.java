package egovframework.external.logcollector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataTypeClassifierTest {

    @Test
    void moleg_criminal_law는_법령이다() {
        assertThat(DataTypeClassifier.isLaw("moleg-criminal-law")).isTrue();
        assertThat(DataTypeClassifier.dataTypeCd("moleg-criminal-law")).isEqualTo("EXTERNAL_LAW");
    }

    @Test
    void 그_외_오퍼레이션은_전부_공공데이터다() {
        assertThat(DataTypeClassifier.isLaw("kma-weather-warning-list")).isFalse();
        assertThat(DataTypeClassifier.dataTypeCd("kma-weather-warning-list")).isEqualTo("EXTERNAL_PUBLIC");
        assertThat(DataTypeClassifier.dataTypeCd("safetydata-disaster-msg-list")).isEqualTo("EXTERNAL_PUBLIC");
    }

    @Test
    void 처음_보는_operationKey도_기본값은_공공데이터다() {
        assertThat(DataTypeClassifier.dataTypeCd("some-brand-new-operation")).isEqualTo("EXTERNAL_PUBLIC");
    }
}
