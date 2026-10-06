package egovframework.external.publicdata.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 서비스(admin-api)에서 배치 스케줄 설정을 가져온다. 정형·비정형 수집기가 쓰는 것과 같은 방식이다
 * (기동 시 + 5분마다 {@code GET /api/batch-schedules*} 폴링).
 *
 * <p>관리자 DB를 직접 읽지 않고 API 계약만 따른다 - 테이블 구조가 바뀌어도 영향이 없고 admin-db 연결 설정과도 무관하다.
 * 세부 스케줄이 여러 행이므로 단건이 아니라 목록 API({@code GET /api/batch-schedules}) 한 번으로 전부 받는다.
 * 이 API는 인증 토큰이 필요 없다.
 *
 * <p>{@code admin-api.base-url}이 비어 있으면 {@link #isConfigured()}가 false이고 호출하지 않는다.
 */
@Component
public class AdminBatchScheduleClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 관리자 스케줄 한 행에서 쓰는 값만 담는다. schdCd가 null이면 데이터 구분 대표 행 */
    public record Row(String dataTypeCd, String schdCd, String activeYn) {
    }

    private final String baseUrl;
    private final RestClient restClient;

    public AdminBatchScheduleClient(
            @Value("${admin-api.base-url:}") String baseUrl,
            @Value("${admin-api.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${admin-api.read-timeout-ms:3000}") long readTimeoutMs) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean isConfigured() {
        return StringUtils.hasText(baseUrl);
    }

    /**
     * 모든 스케줄 행을 가져온다.
     *
     * @throws RestClientException   통신 오류 또는 비2xx 응답
     * @throws IllegalStateException 응답이 기대한 형식({@code result} 배열)이 아닐 때
     */
    public List<Row> fetchAll() {
        String body = restClient.get()
                .uri(baseUrl + "/api/batch-schedules")
                .retrieve()
                .body(String.class);
        return parse(body);
    }

    static List<Row> parse(String body) {
        JsonNode result;
        try {
            result = OBJECT_MAPPER.readTree(body == null ? "" : body).path("result");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("스케줄 응답이 JSON이 아닙니다", e);
        }
        if (!result.isArray()) {
            throw new IllegalStateException("스케줄 응답에 result 배열이 없습니다");
        }
        List<Row> rows = new ArrayList<>();
        for (JsonNode node : result) {
            rows.add(new Row(text(node, "dataTypeCd"), text(node, "schdCd"), text(node, "activeYn")));
        }
        return rows;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() && !value.isNull() ? value.asText() : null;
    }
}
