package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;

import java.time.LocalDateTime;
import java.time.ZoneId;
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
        // Main.java가 JVM 기본 타임존을 UTC로 고정해둬서(회사 스켈레톤 컨벤션) LocalDateTime.now()가
        // 실제 한국 시각이 아니게 됨 - 기상청 발표시각은 KST 기준이라 명시적으로 지정해야 함(2026-08-21).
        KmaBaseTimeCalculator.BaseTime baseTime =
            KmaBaseTimeCalculator.ultraSrtFcst(LocalDateTime.now(ZoneId.of("Asia/Seoul")));

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
