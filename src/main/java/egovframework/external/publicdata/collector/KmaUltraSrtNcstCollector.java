package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기상청 단기예보 조회서비스(VilageFcstInfoService_2.0) - getUltraSrtNcst(초단기실황조회).
 *
 * <p>한 번 호출로 T1H(기온)/RN1(1h강수량)/REH(습도)/PTY(강수형태)/VEC(풍향)/WSD(풍속)/UUU/VVV 등
 * 여러 category가 한 번에 응답됨 (카테고리별 분리는 정제 단계에서 처리 - private-doc 14번 참고).</p>
 *
 * <p><b>TODO:</b> nx/ny는 현재 설정값 하나(기본 서울)만 사용. 지역별 격자좌표 매핑표 확보되면
 * 지역 목록을 순회하도록 확장 필요.</p>
 */
@Component
public class KmaUltraSrtNcstCollector implements PublicDataCollector {

    private final KmaApiClient apiClient;
    private final String endpoint;
    private final String serviceKey;
    private final String nx;
    private final String ny;

    public KmaUltraSrtNcstCollector(
        KmaApiClient apiClient,
        @Value("${public-data.kma.village-forecast.endpoint}") String endpoint,
        @Value("${public-data.kma.village-forecast.service-key:}") String serviceKey,
        @Value("${public-data.kma.default-location.nx}") String nx,
        @Value("${public-data.kma.default-location.ny}") String ny
    ) {
        this.apiClient = apiClient;
        this.endpoint = endpoint;
        this.serviceKey = serviceKey;
        this.nx = nx;
        this.ny = ny;
    }

    @Override
    public String key() {
        return "kma-village-forecast-ultra-srt-ncst";
    }

    @Override
    public String sourceName() {
        return "공공데이터포털 (기상청 동네예보)";
    }

    @Override
    public String apiName() {
        return "초단기실황조회";
    }

    @Override
    public List<String> collect() throws CollectException {
        KmaBaseTimeCalculator.BaseTime baseTime = KmaBaseTimeCalculator.ultraSrtNcst(LocalDateTime.now());

        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", "100");
        params.put("pageNo", "1");
        params.put("dataType", "JSON");
        params.put("base_date", baseTime.baseDate());
        params.put("base_time", baseTime.baseTime());
        params.put("nx", nx);
        params.put("ny", ny);

        return apiClient.call(sourceName(), apiName(), endpoint + "/getUltraSrtNcst", serviceKey, params);
    }
}
