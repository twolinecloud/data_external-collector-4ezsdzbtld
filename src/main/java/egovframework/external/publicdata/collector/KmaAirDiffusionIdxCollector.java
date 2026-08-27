package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기상청_생활기상지수 조회서비스(LivingWthrIdxServiceV5) - getAirDiffusionIdxV5(대기정체지수조회).
 * {@link KmaUVIdxCollector}와 완전히 동일한 패턴(요청 파라미터/시도 단위 생산) - 오퍼레이션만 다름.
 */
public class KmaAirDiffusionIdxCollector implements PublicDataCollector {

    private final KmaApiClient apiClient;
    private final String endpoint;
    private final String serviceKey;
    private final LivingWthrIdxArea area;

    public KmaAirDiffusionIdxCollector(KmaApiClient apiClient, String endpoint, String serviceKey, LivingWthrIdxArea area) {
        this.apiClient = apiClient;
        this.endpoint = endpoint;
        this.serviceKey = serviceKey;
        this.area = area;
    }

    @Override
    public String key() {
        return "kma-living-air-diffusion-idx--" + area.areaNo();
    }

    @Override
    public String operationKey() {
        return "kma-living-air-diffusion-idx";
    }

    @Override
    public String sourceName() {
        return "공공데이터포털 (기상청 생활기상지수)";
    }

    @Override
    public String apiName() {
        return "대기정체지수조회 (" + area.sido() + ")";
    }

    @Override
    public List<String> collect() throws CollectException {
        // Main.java의 JVM 기본 타임존이 Asia/Seoul(KST)라 now()가 곧 한국 시각 (2026-08-27).
        String time = LivingWthrIdxTimeSupport.latestIssuedTime(LocalDateTime.now());

        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", "10");
        params.put("pageNo", "1");
        params.put("dataType", "JSON");
        params.put("areaNo", area.areaNo());
        params.put("time", time);

        return apiClient.call(sourceName(), apiName(), endpoint + "/getAirDiffusionIdxV5", serviceKey, params);
    }
}
