package egovframework.external.publicdata.collector;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 생활기상지수(자외선지수/대기정체지수) 2개 오퍼레이션 × {@link LivingWthrIdxArea}(전국 16개
 * 시도) 조합만큼 {@link PublicDataCollector} 인스턴스를 생성한다. {@link KmaLocationCollectorFactory}
 * 와 동일한 패턴 - 시도가 추가/변경되면 {@code kma-living-wthr-idx-area.csv}만 고치면 됨.
 */
@Component
public class LivingWthrIdxCollectorFactory {

    private final KmaApiClient apiClient;
    private final String endpoint;
    private final String serviceKey;
    private final List<LivingWthrIdxArea> areas;

    public LivingWthrIdxCollectorFactory(
        KmaApiClient apiClient,
        @Value("${public-data.kma.living-wthr-idx.endpoint}") String endpoint,
        @Value("${public-data.kma.living-wthr-idx.service-key:}") String serviceKey,
        LivingWthrIdxAreaLoader areaLoader
    ) {
        this.apiClient = apiClient;
        this.endpoint = endpoint;
        this.serviceKey = serviceKey;
        this.areas = areaLoader.all();
    }

    public List<PublicDataCollector> uvIdxCollectors() {
        return areas.stream()
            .<PublicDataCollector>map(area -> new KmaUVIdxCollector(apiClient, endpoint, serviceKey, area))
            .toList();
    }

    public List<PublicDataCollector> airDiffusionIdxCollectors() {
        return areas.stream()
            .<PublicDataCollector>map(area -> new KmaAirDiffusionIdxCollector(apiClient, endpoint, serviceKey, area))
            .toList();
    }
}
