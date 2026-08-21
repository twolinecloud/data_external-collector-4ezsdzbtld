package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기상청 단기예보 조회서비스(VilageFcstInfoService_2.0) - getUltraSrtNcst(초단기실황조회).
 *
 * <p>한 번 호출로 T1H(기온)/RN1(1h강수량)/REH(습도)/PTY(강수형태)/VEC(풍향)/WSD(풍속)/UUU/VVV 등
 * 여러 category가 한 번에 응답됨 (카테고리별 분리는 정제 단계에서 처리).</p>
 *
 * <p>{@link Location}(전국 교정기관 59개소) 하나당 인스턴스 하나. Spring Bean이 아니라
 * {@link KmaLocationCollectorFactory}가 지역 수만큼 생성한다 - 59개 빈을 등록하는 대신
 * 팩토리 하나로 관리.</p>
 */
public class KmaUltraSrtNcstCollector implements PublicDataCollector {

    private final KmaApiClient apiClient;
    private final String endpoint;
    private final String serviceKey;
    private final Location location;

    public KmaUltraSrtNcstCollector(KmaApiClient apiClient, String endpoint, String serviceKey, Location location) {
        this.apiClient = apiClient;
        this.endpoint = endpoint;
        this.serviceKey = serviceKey;
        this.location = location;
    }

    @Override
    public String key() {
        return "kma-village-forecast-ultra-srt-ncst--" + location.facilityId();
    }

    @Override
    public String operationKey() {
        return "kma-village-forecast-ultra-srt-ncst";
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
        return "초단기실황조회 (" + location.facilityName() + ")";
    }

    @Override
    public List<String> collect() throws CollectException {
        // Main.java가 JVM 기본 타임존을 UTC로 고정해둬서(회사 스켈레톤 컨벤션) LocalDateTime.now()가
        // 실제 한국 시각이 아니게 됨 - 기상청 발표시각은 KST 기준이라 명시적으로 지정해야 함(2026-08-21).
        KmaBaseTimeCalculator.BaseTime baseTime =
            KmaBaseTimeCalculator.ultraSrtNcst(LocalDateTime.now(ZoneId.of("Asia/Seoul")));

        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", "100");
        params.put("pageNo", "1");
        params.put("dataType", "JSON");
        params.put("base_date", baseTime.baseDate());
        params.put("base_time", baseTime.baseTime());
        params.put("nx", location.nx());
        params.put("ny", location.ny());

        return apiClient.call(sourceName(), apiName(), endpoint + "/getUltraSrtNcst", serviceKey, params);
    }
}
