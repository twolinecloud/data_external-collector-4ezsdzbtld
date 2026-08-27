package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기상청 단기예보 조회서비스(VilageFcstInfoService_2.0) - getUltraSrtFcst(초단기예보조회, 6시간).
 * category: T1H/RN1/SKY/UUU/VVV/REH/PTY/POP/LGT/VEC/WSD 등 (카테고리별 분리는 정제 단계).
 *
 * <p>{@link Location} 하나당 인스턴스 하나, Spring Bean 아님 - {@link KmaLocationCollectorFactory} 참고.</p>
 */
public class KmaUltraSrtFcstCollector implements PublicDataCollector {

    private final KmaApiClient apiClient;
    private final String endpoint;
    private final String serviceKey;
    private final Location location;

    public KmaUltraSrtFcstCollector(KmaApiClient apiClient, String endpoint, String serviceKey, Location location) {
        this.apiClient = apiClient;
        this.endpoint = endpoint;
        this.serviceKey = serviceKey;
        this.location = location;
    }

    @Override
    public String key() {
        return "kma-village-forecast-ultra-srt-fcst--" + location.facilityId();
    }

    @Override
    public String operationKey() {
        return "kma-village-forecast-ultra-srt-fcst";
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
        return "초단기예보조회 (" + location.facilityName() + ")";
    }

    @Override
    public List<String> collect() throws CollectException {
        // Main.java의 JVM 기본 타임존이 Asia/Seoul(KST)라 now()가 곧 한국 시각 (2026-08-27).
        KmaBaseTimeCalculator.BaseTime baseTime =
            KmaBaseTimeCalculator.ultraSrtFcst(LocalDateTime.now());

        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", "100");
        params.put("pageNo", "1");
        params.put("dataType", "JSON");
        params.put("base_date", baseTime.baseDate());
        params.put("base_time", baseTime.baseTime());
        params.put("nx", location.nx());
        params.put("ny", location.ny());

        return apiClient.call(sourceName(), apiName(), endpoint + "/getUltraSrtFcst", serviceKey, params);
    }
}
