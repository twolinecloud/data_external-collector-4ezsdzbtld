package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link KmaLocationCollectorFactory}가 59개소 × 오퍼레이션 3종 조합으로 컬렉터를
 * 올바르게 생성하는지 검증. 실제 API 호출은 하지 않음 - 생성/키 유일성만 확인.
 */
class KmaLocationCollectorFactoryTest {

    private final KmaApiClient apiClient = new KmaApiClient(new RestTemplate());
    private final FacilityLocationLoader locationLoader = new FacilityLocationLoader();
    private final KmaLocationCollectorFactory factory =
        new KmaLocationCollectorFactory(apiClient, "https://example.invalid", "test-key", locationLoader);

    @Test
    void 오퍼레이션별로_59개소만큼_컬렉터가_생성된다() {
        assertThat(factory.ultraSrtNcstCollectors()).hasSize(59);
        assertThat(factory.ultraSrtFcstCollectors()).hasSize(59);
        assertThat(factory.vilageFcstCollectors()).hasSize(59);
    }

    @Test
    void 전체_목록은_3개_오퍼레이션을_합친_177개다() {
        assertThat(factory.allLocationBasedCollectors()).hasSize(59 * 3);
    }

    @Test
    void 모든_컬렉터의_key는_서로_고유하다() {
        List<PublicDataCollector> all = factory.allLocationBasedCollectors();

        Set<String> keys = all.stream().map(PublicDataCollector::key).collect(Collectors.toSet());

        assertThat(keys).hasSameSizeAs(all);
    }

    @Test
    void apiName에_기관명이_포함되어_구분된다() {
        PublicDataCollector first = factory.ultraSrtNcstCollectors().get(0);

        assertThat(first.apiName()).contains("초단기실황조회").contains("(");
        assertThat(first.sourceName()).isEqualTo("공공데이터포털 (기상청 동네예보)");
    }
}
