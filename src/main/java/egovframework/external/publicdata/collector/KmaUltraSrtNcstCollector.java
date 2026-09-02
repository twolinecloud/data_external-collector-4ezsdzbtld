package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
        // Main.java의 JVM 기본 타임존이 Asia/Seoul(KST)라 now()가 곧 한국 시각 (2026-08-27).
        KmaBaseTimeCalculator.BaseTime baseTime =
            KmaBaseTimeCalculator.ultraSrtNcst(LocalDateTime.now());

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
