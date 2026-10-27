package egovframework.external.publicdata.collector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenTopoData SRTM 30m 로 시설 해발을 얻는다 - 시설 지형 분석(private-doc/facility-terrain.csv, terrain-rule-base-spec.md §6-2)과
 * 같은 자료라 값이 같다(2026-10-27 운영 파드에서 제주교도소 210m, 인천구치소 25m 일치 확인).
 *
 * <p>공개 API 제한(초당 1회, 하루 1,000회)이 있어 위치마다 한 번만 묻고 결과를 보관하며, 요청 사이를 1.1초 띄운다
 * (처음 한 번 시설 59곳이면 약 1분). 실패하면 빈 값을 주고
 * {@link #RETRY_INTERVAL} 뒤에 다시 묻는다 - 그동안 특보 구역은 거리 규칙만으로 정해진다.</p>
 */
@Component
public class OpenTopoDataElevationLookup implements FacilityElevationLookup {

    private static final Logger logger = LogManager.getLogger(OpenTopoDataElevationLookup.class);
    private static final Duration RETRY_INTERVAL = Duration.ofHours(1);
    /** 공개 API 제한이 초당 1회라 요청 사이를 이만큼 띄운다 - 첫 정제 때 시설 59곳을 연달아 물으면 대부분 거절된다. */
    private static final long MIN_INTERVAL_MS = 1100;
    private long lastRequestAt;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String endpoint;
    private final boolean enabled;
    private final Map<String, Double> known = new ConcurrentHashMap<>();
    private final Map<String, Instant> failedAt = new ConcurrentHashMap<>();

    public OpenTopoDataElevationLookup(
        @Value("${public-data.kma.warning-zone.elevation-endpoint:https://api.opentopodata.org/v1/srtm30m}") String endpoint,
        @Value("${public-data.kma.warning-zone.elevation-enabled:true}") boolean enabled
    ) {
        this.endpoint = endpoint;
        this.enabled = enabled;
    }

    @Override
    public Optional<Double> elevationM(double lat, double lon) {
        if (!enabled || endpoint == null || endpoint.isBlank()) {
            return Optional.empty();
        }
        String key = String.format(Locale.ROOT, "%.6f,%.6f", lat, lon);
        Double cached = known.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        Instant failed = failedAt.get(key);
        if (failed != null && failed.isAfter(Instant.now().minus(RETRY_INTERVAL))) {
            return Optional.empty();
        }
        try {
            throttle();
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + "?locations=" + key))
                .timeout(Duration.ofSeconds(15)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                JSONArray results = new JSONObject(response.body()).optJSONArray("results");
                if (results != null && !results.isEmpty() && !results.getJSONObject(0).isNull("elevation")) {
                    double elevation = results.getJSONObject(0).getDouble("elevation");
                    known.put(key, elevation);
                    failedAt.remove(key);
                    return Optional.of(elevation);
                }
            }
            logger.warn("[WARN-ZONE] 해발 조회 실패({}): HTTP {} - 거리 규칙만 적용", key, response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.warn("[WARN-ZONE] 해발 조회 실패({}): {} - 거리 규칙만 적용", key, e.getMessage());
        }
        failedAt.put(key, Instant.now());
        return Optional.empty();
    }

    private synchronized void throttle() throws InterruptedException {
        long wait = lastRequestAt + MIN_INTERVAL_MS - System.currentTimeMillis();
        if (wait > 0) {
            Thread.sleep(wait);
        }
        lastRequestAt = System.currentTimeMillis();
    }
}
