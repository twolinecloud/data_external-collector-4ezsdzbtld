package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기상청 기상특보 조회서비스(WthrWrnInfoService) - getWthrWrnList(기상특보목록조회).
 *
 * <p>한파/건조/안개/폭염/지진해일/폭풍해일/호우/대설/태풍/풍랑/강풍 12개 현상을 178개 시군 +
 * 44개 해역 단위로, 주의보/경보 2단계로 발표. UI 목업의 "호우(특보)" 항목에 대응하지만,
 * 다만 "특보단계"까지 이 오퍼레이션 하나로 커버되는지는 미확정.</p>
 *
 * <p><b>TODO:</b> stnId(지점코드) 필터 없이 전체 조회 중 - 지점코드 매핑표 확보되면 지역별로
 * 나눠서 호출하도록 개선 검토. fromTmFc/toTmFc는 일단 오늘 하루로 고정.</p>
 */
@Component
public class KmaWeatherWarningListCollector implements PublicDataCollector {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final KmaApiClient apiClient;
    private final String endpoint;
    private final String serviceKey;

    public KmaWeatherWarningListCollector(
        KmaApiClient apiClient,
        @Value("${public-data.kma.weather-warning.endpoint}") String endpoint,
        @Value("${public-data.kma.weather-warning.service-key:}") String serviceKey
    ) {
        this.apiClient = apiClient;
        this.endpoint = endpoint;
        this.serviceKey = serviceKey;
    }

    @Override
    public String key() {
        return "kma-weather-warning-list";
    }

    @Override
    public String sourceName() {
        return "공공데이터포털 (기상청 기상특보)";
    }

    @Override
    public String apiName() {
        return "기상특보목록조회";
    }

    @Override
    public List<String> collect() throws CollectException {
        // Main.java의 JVM 기본 타임존이 Asia/Seoul(KST)라 now()가 곧 한국 날짜 (2026-08-27).
        String today = LocalDate.now().format(DATE_FMT);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", "100");
        params.put("pageNo", "1");
        params.put("dataType", "JSON");
        params.put("fromTmFc", today);
        params.put("toTmFc", today);

        return apiClient.call(sourceName(), apiName(), endpoint + "/getWthrWrnList", serviceKey, params);
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
