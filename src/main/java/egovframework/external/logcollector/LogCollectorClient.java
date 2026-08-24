package egovframework.external.logcollector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Optional;

/**
 * 로그 컬렉터(Log Collector) REST API 얇은 래퍼 (private-doc/log-collector-api-spec.md).
 *
 * <p>이 클라이언트는 <b>절대 예외를 던지지 않는다</b> - 실패하면 로그만 남기고
 * {@link Optional#empty()}/{@code false}를 반환한다. 로그 컬렉터는 우리 파이프라인의
 * 관측성(observability) 부가 기능일 뿐이라, 이 API 호출이 실패하거나 서비스 자체가
 * 죽어있어도 실제 수집/정제 파이프라인은 절대 영향을 받으면 안 된다
 * ({@code CleansedJsonDropWriter}와 동일한 원칙).</p>
 *
 * <p>{@code log-collector.enabled=false}(기본값)면 네트워크 호출 자체를 안 한다 - 공유
 * 플랫폼(다른 팀도 같이 쓰는 admin-db 로그 테이블)에 원치 않게 데이터를 남기지 않도록,
 * 명시적으로 켜기 전까지는 완전히 비활성 상태로 둔다.</p>
 *
 * <p>인증: 스펙엔 전역 JWT Bearer가 걸려있다고 문서화돼 있지만 실측(2026-08-20)해보니 현재는
 * 인증 없이도 호출된다(§1 참고) - 이 클라이언트는 인증 헤더를 아직 안 붙인다. 나중에 인증이
 * 켜지면 이 클래스에 Bearer 토큰 발급/첨부 로직을 추가해야 함.</p>
 */
@Component
public class LogCollectorClient {

    private static final Logger logger = LogManager.getLogger(LogCollectorClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final boolean enabled;

    public LogCollectorClient(
        @Qualifier("logCollectorRestTemplate") RestTemplate restTemplate,
        @Value("${log-collector.base-url:https://kcais-admin.twolinecloud.com/logc}") String baseUrl,
        @Value("${log-collector.enabled:false}") boolean enabled
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** T1 배치 생성. @return execId (실패 시 empty) */
    public Optional<String> createBatch(JSONObject body) {
        return post(baseUrl + "/api/v1/logs/batches", body).map(r -> field(r, "execId"));
    }

    /** T2 단계 생성. @return stepLogId (실패 시 empty) */
    public Optional<String> createStep(String execId, JSONObject body) {
        return post(baseUrl + "/api/v1/logs/batches/" + execId + "/steps", body).map(r -> field(r, "stepLogId"));
    }

    /** T6 외부수집 이력 bulk 적재. */
    public boolean postExternalCollects(String execId, JSONArray body) {
        return post(baseUrl + "/api/v1/logs/batches/" + execId + "/external-collects", body).isPresent();
    }

    /** T2 단계 종료. */
    public boolean finishStep(String stepLogId, JSONObject body) {
        return patch(baseUrl + "/api/v1/logs/steps/" + stepLogId, body).isPresent();
    }

    /** T1 배치 종료. */
    public boolean finishBatch(String execId, JSONObject body) {
        return patch(baseUrl + "/api/v1/logs/batches/" + execId, body).isPresent();
    }

    private String field(JSONObject response, String name) {
        JSONObject result = response.optJSONObject("result");
        return result == null ? null : result.optString(name, null);
    }

    private Optional<JSONObject> post(String url, Object body) {
        return call(url, HttpMethod.POST, body);
    }

    private Optional<JSONObject> patch(String url, Object body) {
        return call(url, HttpMethod.PATCH, body);
    }

    private Optional<JSONObject> call(String url, HttpMethod method, Object body) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
            ResponseEntity<String> response = restTemplate.exchange(URI.create(url), method, entity, String.class);
            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new JSONObject(responseBody));
        } catch (Exception e) {
            logger.warn("[LOG-COLLECTOR] {} 실패: url={}, error={}", method, url, e.getMessage());
            return Optional.empty();
        }
    }
}
