package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 에어코리아 시도별 실시간 대기오염정보 - 2026-09-03 신규. 날씨 기호 요구사항의 황사용.
 *
 * <p><b>여기서 받는 값은 엄밀히 황사가 아니라 미세먼지(PM10)다.</b> 에어코리아에 황사 전용
 * 코드가 없어서, 황사 신호로는 이미 수집 중인 기상특보(황사주의보/경보)가 더 직접적이다.
 * PM10은 "황사일 때 같이 오르는 값"이라 보조 지표에 가깝고, 어느 농도부터 황사 기호를 띄울지는
 * 기획 확정 대기 중이다.</p>
 *
 * <p>{@code sidoName=전국}으로 <b>전국 측정소를 한 번에</b> 받는다(실측 673개소, 시도 16개).
 * 시도별로 나눠 부르면 시간당 16회가 되는데, 에어코리아는 서버가 불안정해 호출 수를 늘릴수록
 * 실패 확률이 올라간다({@link AirKoreaApiClient}의 재시도 주석 참고). 측정소↔교정기관 매칭은
 * 정제 단계가 한다.</p>
 *
 * <p><b>결측이 정상적으로 섞여 온다</b>: 실측 673개소 중 75개소(약 11%)가 {@code pm10Value="-"}
 * 였고, 그런 행은 {@code pm10Flag="통신장애"}를 함께 달고 온다. 수집은 원본 보존이 목적이라
 * 그대로 담고, 결측 판정은 정제 단계 몫이다 - 숫자로 변환하려 들면 그 행에서 터진다.</p>
 */
@Component
public class AirKoreaRealtimeCollector implements PublicDataCollector {

    /** 한 페이지로 전국이 들어오게 잡는다(실측 673개소). 넘치면 클라이언트가 페이지네이션한다. */
    private static final String NUM_OF_ROWS = "1000";

    private final AirKoreaApiClient apiClient;
    private final String endpoint;
    private final String serviceKey;

    public AirKoreaRealtimeCollector(
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
        return "airkorea-realtime-measure";
    }

    @Override
    public String sourceName() {
        return "공공데이터포털 (한국환경공단 에어코리아)";
    }

    @Override
    public String apiName() {
        return "시도별 실시간 대기오염정보";
    }

    @Override
    public List<String> collect() throws CollectException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("returnType", "json");
        params.put("numOfRows", NUM_OF_ROWS);
        params.put("sidoName", "전국");
        params.put("ver", "1.0");

        return apiClient.call(sourceName(), apiName(),
            endpoint + "/getCtprvnRltmMesureDnsty", serviceKey, params);
    }

    /**
     * 다른 기상값과 같은 규칙 - 날짜 기준으로 하루 전까지 유효하다(2026-09-02 사용자 확인).
     * 화면이 어제·오늘·내일을 보여주므로 어제 0시 이후 수집분은 적재에 실패해도 재시도 대기로
     * 들고 있어야 한다 - {@link PublicDataCollector#stagingExpiresAt(LocalDate)} 참고.
     */
    @Override
    public LocalDateTime stagingExpiresAt(LocalDate collectedOn) {
        return collectedOn.plusDays(2).atStartOfDay();
    }
}
