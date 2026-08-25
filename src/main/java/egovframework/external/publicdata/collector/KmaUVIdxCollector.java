package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기상청_생활기상지수 조회서비스(LivingWthrIdxServiceV5) - getUVIdxV5(자외선지수조회).
 *
 * <p>{@link LivingWthrIdxArea}(전국 16개 시도) 하나당 인스턴스 하나 - {@link Location}
 * 기반 동네예보 컬렉터들과 같은 패턴, {@link LivingWthrIdxCollectorFactory}가 시도 수만큼
 * 생성한다. 이 지수는 시군구가 아니라 시도 단위로 생산됨(실측 확인, {@link LivingWthrIdxArea}
 * 클래스 주석 참고) - 시설 매칭은 정제 단계에서 시설의 {@code sido}로 처리한다.</p>
 */
public class KmaUVIdxCollector implements PublicDataCollector {

    private final KmaApiClient apiClient;
    private final String endpoint;
    private final String serviceKey;
    private final LivingWthrIdxArea area;

    public KmaUVIdxCollector(KmaApiClient apiClient, String endpoint, String serviceKey, LivingWthrIdxArea area) {
        this.apiClient = apiClient;
        this.endpoint = endpoint;
        this.serviceKey = serviceKey;
        this.area = area;
    }

    @Override
    public String key() {
        return "kma-living-uv-idx--" + area.areaNo();
    }

    @Override
    public String operationKey() {
        return "kma-living-uv-idx";
    }

    @Override
    public String sourceName() {
        return "공공데이터포털 (기상청 생활기상지수)";
    }

    @Override
    public String apiName() {
        return "자외선지수조회 (" + area.sido() + ")";
    }

    @Override
    public List<String> collect() throws CollectException {
        // Main.java가 JVM 기본 타임존을 UTC로 고정해둬서(회사 스켈레톤 컨벤션) LocalDateTime.now()가
        // 실제 한국 시각이 아니게 됨 - 발표시각은 KST 기준이라 명시적으로 지정해야 함.
        String time = LivingWthrIdxTimeSupport.latestIssuedTime(LocalDateTime.now(ZoneId.of("Asia/Seoul")));

        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", "10");
        params.put("pageNo", "1");
        params.put("dataType", "JSON");
        params.put("areaNo", area.areaNo());
        params.put("time", time);

        return apiClient.call(sourceName(), apiName(), endpoint + "/getUVIdxV5", serviceKey, params);
    }
}
