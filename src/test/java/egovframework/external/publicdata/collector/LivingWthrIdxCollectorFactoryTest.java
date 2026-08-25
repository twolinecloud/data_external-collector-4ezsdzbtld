package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LivingWthrIdxCollectorFactory}가 16개 시도 × 오퍼레이션 2종 조합으로 컬렉터를
 * 올바르게 생성하는지 검증. 실제 API 호출은 하지 않음 - 생성/키 유일성만 확인.
 */
class LivingWthrIdxCollectorFactoryTest {

    private final KmaApiClient apiClient = new KmaApiClient(new RestTemplate());
    private final LivingWthrIdxAreaLoader areaLoader = new LivingWthrIdxAreaLoader();
    private final LivingWthrIdxCollectorFactory factory =
        new LivingWthrIdxCollectorFactory(apiClient, "https://example.invalid", "test-key", areaLoader);

    @Test
    void 오퍼레이션별로_16개_시도만큼_컬렉터가_생성된다() {
        assertThat(factory.uvIdxCollectors()).hasSize(16);
        assertThat(factory.airDiffusionIdxCollectors()).hasSize(16);
    }

    @Test
    void 모든_컬렉터의_key는_서로_고유하다() {
        List<PublicDataCollector> all = new java.util.ArrayList<>(factory.uvIdxCollectors());
        all.addAll(factory.airDiffusionIdxCollectors());

        Set<String> keys = all.stream().map(PublicDataCollector::key).collect(Collectors.toSet());

        assertThat(keys).hasSameSizeAs(all);
    }

    @Test
    void operationKey는_오퍼레이션마다_고정값을_공유한다() {
        Set<String> uvOperationKeys = factory.uvIdxCollectors().stream()
            .map(PublicDataCollector::operationKey).collect(Collectors.toSet());
        Set<String> airOperationKeys = factory.airDiffusionIdxCollectors().stream()
            .map(PublicDataCollector::operationKey).collect(Collectors.toSet());

        assertThat(uvOperationKeys).containsExactly("kma-living-uv-idx");
        assertThat(airOperationKeys).containsExactly("kma-living-air-diffusion-idx");
    }

    @Test
    void apiName에_시도명이_포함되어_구분되고_facilityId는_없다() {
        PublicDataCollector first = factory.uvIdxCollectors().get(0);

        assertThat(first.apiName()).contains("자외선지수조회").contains("(");
        assertThat(first.sourceName()).isEqualTo("공공데이터포털 (기상청 생활기상지수)");
        assertThat(first.facilityId()).isNull();
    }
}
