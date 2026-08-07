package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기상청 단기예보 조회서비스(VilageFcstInfoService_2.0) - getVilageFcst(단기예보조회, 최대 5일).
 * category: POP/PTY/PCP/REH/SNO/SKY/TMP/TMN/TMX/UUU/VVV/WAV/VEC/WSD 등 (카테고리별 분리는 정제 단계).
 *
 * <p>{@link Location} 하나당 인스턴스 하나, Spring Bean 아님 - {@link KmaLocationCollectorFactory} 참고.</p>
 */
public class KmaVilageFcstCollector implements PublicDataCollector {

    private final KmaApiClient apiClient;
    private final String endpoint;
    private final String serviceKey;
    private final Location location;

    public KmaVilageFcstCollector(KmaApiClient apiClient, String endpoint, String serviceKey, Location location) {
        this.apiClient = apiClient;
        this.endpoint = endpoint;
        this.serviceKey = serviceKey;
        this.location = location;
    }

    @Override
    public String key() {
        return "kma-village-forecast-vilage-fcst--" + location.facilityId();
    }

    @Override
    public String operationKey() {
        return "kma-village-forecast-vilage-fcst";
    }

    @Override
    public String facilityId() {
        return location.facilityId();
    }

    @Override
    public String sourceName() {
        return "공공데이터포털 (기상청 동네예보)";
    }

    @Override
    public String apiName() {
        return "단기예보조회 (" + location.facilityName() + ")";
    }

    @Override
    public List<String> collect() throws CollectException {
        KmaBaseTimeCalculator.BaseTime baseTime = KmaBaseTimeCalculator.vilageFcst(LocalDateTime.now());

        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", "1000");
        params.put("pageNo", "1");
        params.put("dataType", "JSON");
        params.put("base_date", baseTime.baseDate());
        params.put("base_time", baseTime.baseTime());
        params.put("nx", location.nx());
        params.put("ny", location.ny());

        return apiClient.call(sourceName(), apiName(), endpoint + "/getVilageFcst", serviceKey, params);
    }
}
