package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기상청 단기예보 조회서비스(VilageFcstInfoService_2.0) - getUltraSrtFcst(초단기예보조회, 6시간).
 * category: T1H/RN1/SKY/UUU/VVV/REH/PTY/POP/LGT/VEC/WSD 등 (카테고리별 분리는 정제 단계).
 */
@Component
public class KmaUltraSrtFcstCollector implements PublicDataCollector {

    private final KmaApiClient apiClient;
    private final String endpoint;
    private final String serviceKey;
    private final String nx;
    private final String ny;

    public KmaUltraSrtFcstCollector(
        KmaApiClient apiClient,
        @Value("${public-data.kma.village-forecast.endpoint}") String endpoint,
        @Value("${public-data.kma.village-forecast.service-key:}") String serviceKey,
        @Value("${public-data.kma.default-location.nx}") String nx,
        @Value("${public-data.kma.default-location.ny}") String ny
    ) {
        this.apiClient = apiClient;
        this.endpoint = endpoint;
        this.serviceKey = serviceKey;
        this.nx = nx;
        this.ny = ny;
    }

    @Override
    public String key() {
        return "kma-village-forecast-ultra-srt-fcst";
    }

    @Override
    public String sourceName() {
        return "공공데이터포털 (기상청 동네예보)";
    }

    @Override
    public String apiName() {
        return "초단기예보조회";
    }

    @Override
    public List<String> collect() throws CollectException {
        KmaBaseTimeCalculator.BaseTime baseTime = KmaBaseTimeCalculator.ultraSrtFcst(LocalDateTime.now());

        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", "100");
        params.put("pageNo", "1");
        params.put("dataType", "JSON");
        params.put("base_date", baseTime.baseDate());
        params.put("base_time", baseTime.baseTime());
        params.put("nx", nx);
        params.put("ny", ny);

        return apiClient.call(sourceName(), apiName(), endpoint + "/getUltraSrtFcst", serviceKey, params);
    }
}
