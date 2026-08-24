package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FacilitySidoLoaderTest {

    @Test
    void 전국_교정기관_59개소가_모두_로딩된다() {
        assertThat(new FacilitySidoLoader().all()).hasSize(59);
    }

    @Test
    void 시도명이_기상특보_지점코드_관할표기와_일치한다() {
        // KmaWarningStation의 jurisdictionSido와 문자열이 정확히 같아야 매칭이 되므로 실제 값을 고정
        List<FacilitySido> all = new FacilitySidoLoader().all();

        FacilitySido yeongwol = all.stream()
            .filter(f -> f.facilityId().equals("1272038"))
            .findFirst()
            .orElseThrow();

        assertThat(yeongwol.sido()).isEqualTo("강원특별자치도");
    }
}
