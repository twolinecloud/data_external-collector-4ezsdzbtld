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
 * 에어코리아 대기질예보통보(getMinuDustFrcstDspth) - 2026-09-04 신규. 날씨 기호 요구사항의
 * <b>내일 황사</b>용 - {@link AirKoreaRealtimeCollector}(오늘·어제 PM10 실황)와 짝을 이룬다.
 *
 * <p>{@code searchDate=오늘}로 호출하면 <b>오늘과 내일 이틀치</b>가 한 응답에 같이 온다(실측,
 * 2026-09-04) - {@code informData} 필드로 날짜를 구분한다. 하루 최대 4회(05/11/17/23시경)
 * 발표되고, 그날 이미 발표된 것들이 누적돼 응답에 같이 실린다({@code dataTime}에 "○○시 발표"로
 * 표기) - 정제 단계가 같은 날짜라도 <b>가장 최근 발표만</b> 신뢰한다({@code AirKoreaDustForecastCleanser}
 * 참고).</p>
 *
 * <p>응답엔 PM10/PM2.5/O3 예보가 같이 온다({@code informCode}). 우리 용도(황사)엔 PM10만
 * 쓴다 - 정제 단계에서 걸러낸다.</p>
 *
 * <p><b>PM10은 황사가 아니다</b> - {@link AirKoreaRealtimeCollector} 클래스 주석과 동일한
 * 이유. 황사 전용 예보 코드가 없어 미세먼지 예보로 대신하는 것뿐이다.</p>
 */
@Component
public class AirKoreaDustForecastCollector implements PublicDataCollector {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** 하루 최대 응답 건수 여유값 - informCode 3종 × 예보일자 2개 × 발표회차(최대 4회 누적)를 넉넉히 덮음. */
    private static final String NUM_OF_ROWS = "100";

    private final AirKoreaApiClient apiClient;
    private final String endpoint;
    private final String serviceKey;

    public AirKoreaDustForecastCollector(
        AirKoreaApiClient apiClient,
        @Value("${public-data.airkorea.endpoint}") String endpoint,
        @Value("${public-data.airkorea.service-key:}") String serviceKey
    ) {
        this.apiClient = apiClient;
        this.endpoint = endpoint;
        this.serviceKey = serviceKey;
    }

    @Override
    public String key() {
        return "airkorea-dust-forecast";
    }

    @Override
    public String sourceName() {
        return "공공데이터포털 (한국환경공단 에어코리아)";
    }

    @Override
    public String apiName() {
        return "대기질예보통보";
    }

    @Override
    public List<String> collect() throws CollectException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("returnType", "json");
        params.put("numOfRows", NUM_OF_ROWS);
        params.put("searchDate", LocalDate.now().format(DATE_FMT));

        return apiClient.call(sourceName(), apiName(),
            endpoint + "/getMinuDustFrcstDspth", serviceKey, params);
    }

    /**
     * 이 소스는 <b>매일 자정에 완전히 새 값으로 갈아끼워진다</b>(어제 예보는 오늘 조회에
     * 안 나옴, {@code searchDate}가 오늘로 고정이라서) - 다른 기상값처럼 "어제 0시 이후"를
     * 붙들 이유가 없다. 당일 수집분은 자정이 지나면 그 데이터 자체가 이미 무의미해지므로
     * 짧게 잡는다 - {@link PublicDataCollector#stagingExpiresAt(LocalDate)} 참고.
     */
    @Override
    public LocalDateTime stagingExpiresAt(LocalDate collectedOn) {
        return collectedOn.plusDays(1).atStartOfDay();
    }
}
