package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
        // Main.java의 JVM 기본 타임존이 Asia/Seoul(KST)라 now()가 곧 한국 시각 (2026-08-27).
        String time = LivingWthrIdxTimeSupport.latestIssuedTime(LocalDateTime.now());

        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", "10");
        params.put("pageNo", "1");
        params.put("dataType", "JSON");
        params.put("areaNo", area.areaNo());
        params.put("time", time);

        return apiClient.call(sourceName(), apiName(), endpoint + "/getUVIdxV5", serviceKey, params);
    }

    /**
     * 기상값은 날짜 기준으로 하루 전까지 유효하다 - 오늘이 9/2면 9/1 00:00 이후 수집분까지는
     * 적재에 실패해도 재시도 대기로 들고 있어야 하고, 8/31 이하는 이미 지난 값이라 폐기해도 된다
     * (2026-09-02 사용자 확인). 그래서 수집일 D의 행은 D가 "그저께"가 되는 순간인
     * D+2일 0시에 만료된다 - {@link PublicDataCollector#stagingExpiresAt(LocalDate)} 참고.
     */
    @Override
    public LocalDateTime stagingExpiresAt(LocalDate collectedOn) {
        return collectedOn.plusDays(2).atStartOfDay();
    }
}
