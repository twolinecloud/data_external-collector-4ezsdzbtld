package egovframework.external.logcollector;

import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
 *
 * <p><b>단일 스레드로 직렬화하는 이유(2026-08-26)</b>: 스케줄러 스레드풀을 10개로 늘린
 * 직후, 여러 스케줄이 동시에 로그 컬렉터로 배치 생성 요청을 보내면서 로그 컬렉터 쪽
 * {@code exec_id} 채번 로직이 동시성에 안전하지 않아 {@code duplicate key} 충돌로 100%
 * 실패하는 게 실측됐다(그쪽 채번 구조는 우리가 손댈 수 없음). 그래서 실제 HTTP 호출부
 * ({@link #call})만 전용 단일 스레드 executor로 라우팅해서, 몇 개의 스케줄러 스레드가
 * 동시에 이 클라이언트를 호출하든 로그 컬렉터로 나가는 요청은 항상 하나씩 순서대로만
 * 나가게 강제한다 - 호출부(createBatch 등)는 여전히 동기 호출이라 기존 시그니처/의미는
 * 그대로다. 이 executor 자체가(혹은 그 안에서 실행 중인 요청이) 막히는 상황까지 대비해서
 * {@link #call}의 {@code Future.get}에도 타임아웃을 건다 - 없으면 앞선 요청 하나가 영원히
 * 안 끝날 때 뒤에 쌓인 모든 로그 컬렉터 호출이 큐에서 무한 대기하게 된다.</p>
 */
@Component
public class LogCollectorClient {

    private static final Logger logger = LogManager.getLogger(LogCollectorClient.class);
    private static final Duration DEFAULT_CALL_TIMEOUT = Duration.ofSeconds(30);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final boolean enabled;
    private final Duration callTimeout;
    private final ExecutorService callExecutor =
        Executors.newSingleThreadExecutor(r -> new Thread(r, "log-collector-call"));

    // 생성자가 2개(운영용/테스트용)라 스프링이 자동으로 하나를 고르지 못함 - 명시적으로
    // 지정 필요(실측 2026-08-26, 로컬 부팅 확인 중 발견: "No default constructor found").
    @Autowired
    public LogCollectorClient(
        @Qualifier("logCollectorRestTemplate") RestTemplate restTemplate,
        @Value("${log-collector.base-url:https://kcais-admin.twolinecloud.com/logc}") String baseUrl,
        @Value("${log-collector.enabled:false}") boolean enabled
    ) {
        this(restTemplate, baseUrl, enabled, DEFAULT_CALL_TIMEOUT);
    }

    /** 타임아웃을 직접 지정하는 생성자 - 테스트에서 30초를 실제로 기다리지 않고 타임아웃 경로를 검증할 때 씀. */
    LogCollectorClient(RestTemplate restTemplate, String baseUrl, boolean enabled, Duration callTimeout) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
        this.callTimeout = callTimeout;
    }

    @PreDestroy
    void shutdown() {
        callExecutor.shutdownNow();
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
        Callable<Optional<JSONObject>> task = () -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
            ResponseEntity<String> response = restTemplate.exchange(URI.create(url), method, entity, String.class);
            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new JSONObject(responseBody));
        };

        Future<Optional<JSONObject>> future = callExecutor.submit(task);
        try {
            return future.get(callTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // 이 요청 자체가 오래 걸리는 것일 수도, 앞서 쌓인 다른 요청이 안 끝나서 큐에서
            // 대기 중인 것일 수도 있음 - 어느 쪽이든 여기서 포기해야 뒤따르는 호출들이 무한정
            // 밀리지 않는다.
            future.cancel(true);
            logger.warn("[LOG-COLLECTOR] {} 타임아웃({}ms): url={}", method, callTimeout.toMillis(), url);
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("[LOG-COLLECTOR] {} 실패: url={}, error={}", method, url, e.getMessage());
            return Optional.empty();
        }
    }
}
