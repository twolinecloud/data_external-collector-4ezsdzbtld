package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 한국환경공단 에어코리아(대기오염정보) 호출 로직. 발급처는 {@link KmaApiClient}와 같은
 * 공공데이터포털이지만 <b>응답 봉투가 달라 재사용할 수 없다</b>(2026-09-03 실측).
 *
 * <ul>
 *   <li>목록 위치: 기상청은 {@code body.items.item[]}인데 여기는 {@code body.items[]}가 곧 배열이다.
 *       {@link KmaResponseParser}에 그대로 물리면 예외 없이 <b>0건</b>으로 나와 조용히 유실된다.</li>
 *   <li>실패 봉투: 기상청은 {@code response.header.resultCode}로 오지만 여기는 최상위가 통째로
 *       {@code OpenAPI_ServiceResponse.cmmMsgHeader}로 바뀐다 - {@code response}를 찾다 실패해
 *       "형식 오류"로만 보이고 진짜 원인이 묻힌다.</li>
 * </ul>
 *
 * <p><b>재시도가 필수인 이유</b>: 검증 중 같은 요청이 연달아 {@code SERVICETIMEOUT_ERROR}
 * (returnReasonCode 05, "서비스 연결실패")로 실패하다 재시도에서 성공하는 일이 반복됐다
 * (한 번은 3회 중 2회 실패). 수집 주기가 시간당 1회라 한 번 실패하면 그 시각 자료를 영영
 * 놓치므로, 다음 주기를 기다리지 않고 호출 안에서 즉시 되풀이한다. 다만 인증 오류처럼
 * 되풀이해도 달라지지 않는 실패까지 붙들면 스케줄러 스레드만 잡아먹으므로, <b>일시적 실패로
 * 문서화된 코드에만</b> 재시도를 건다.</p>
 */
@Component
public class AirKoreaApiClient {

    private static final Logger logger = LogManager.getLogger(AirKoreaApiClient.class);

    private static final int MAX_PAGES = 10;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MS = 2000L;

    /** 되풀이하면 성공할 수 있는 실패. 05 = 서비스 연결실패(에어코리아 서버측 일시 장애). */
    private static final String TRANSIENT_REASON_CODE = "05";

    private final RestTemplate restTemplate;

    public AirKoreaApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<String> call(String sourceName, String apiName, String endpoint, String serviceKey,
            Map<String, String> params) throws CollectException {
        if (endpoint == null || endpoint.isBlank() || serviceKey == null || serviceKey.isBlank()) {
            throw new CollectException(sourceName, apiName, "엔드포인트/서비스키 설정이 비어있음 (미확정)");
        }

        List<String> allItems = new ArrayList<>();
        int pageNo = 1;
        int totalCount = Integer.MAX_VALUE; // 첫 페이지를 받기 전엔 모름
        int pagesFetched = 0;

        while (allItems.size() < totalCount && pagesFetched < MAX_PAGES) {
            Map<String, String> pageParams = new LinkedHashMap<>(params);
            pageParams.put("pageNo", String.valueOf(pageNo));

            ParsedPage page = fetchWithRetry(sourceName, apiName, endpoint, serviceKey, pageParams);
            allItems.addAll(page.items());
            totalCount = page.totalCount();
            pagesFetched++;
            pageNo++;

            if (page.items().isEmpty()) {
                break; // totalCount가 부정확해도 빈 페이지면 중단 (무한루프 방지)
            }
        }

        if (allItems.size() < totalCount) {
            logger.warn("[{}] {} - {}페이지({}건)까지 받았지만 totalCount={}에 못 미쳐 나머지를 포기함 (MAX_PAGES 상한)",
                    sourceName, apiName, pagesFetched, allItems.size(), totalCount);
        }
        return allItems;
    }

    private ParsedPage fetchWithRetry(String sourceName, String apiName, String endpoint, String serviceKey,
            Map<String, String> params) {
        CollectException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return parse(sourceName, apiName, fetch(sourceName, apiName, endpoint, serviceKey, params));
            } catch (TransientApiException e) {
                lastFailure = new CollectException(sourceName, apiName,
                    "일시적 실패가 " + MAX_ATTEMPTS + "회 반복됨 - " + e.getMessage(), e);
                logger.warn("[{}] {} - 일시적 실패({}/{}): {}", sourceName, apiName, attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    backoff();
                }
            }
        }
        throw lastFailure;
    }

    private void backoff() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재시도 대기 중 인터럽트됨", e);
        }
    }

    private String fetch(String sourceName, String apiName, String endpoint, String serviceKey,
            Map<String, String> params) {
        // serviceKey는 이미 URL-encode된 문자열이라 그대로 붙인다 - 다시 인코딩하면 깨진다
        // (이중 인코딩 방지, KmaApiClient 주석과 같은 이유).
        StringBuilder url = new StringBuilder(endpoint).append('?').append("serviceKey=").append(serviceKey);
        // 나머지 파라미터는 반드시 인코딩한다. sidoName="전국"처럼 한글 값이 들어오는데, 원문
        // 그대로 붙이면 API가 400 Bad Request로 거절한다(2026-09-03 운영 첫 수집에서 실측 -
        // 같은 요청도 %EC%A0%84%EA%B5%AD로 보내면 200). 값에 한글을 쓰는 건 이 API가 처음이라
        // 기존 클라이언트에는 없던 문제다.
        params.forEach((key, value) -> url.append('&').append(key).append('=')
            .append(URLEncoder.encode(value, StandardCharsets.UTF_8)));

        try {
            URI uri = URI.create(url.toString());
            return restTemplate.getForObject(uri, String.class);
        } catch (HttpClientErrorException e) {
            // 4xx는 우리 요청이 잘못된 것이라 되풀이해도 그대로다 - 재시도하면 원인 파악만 늦어진다
            // (2026-09-03 실측: 한글 미인코딩으로 400이 났는데 3회를 되풀이하고 4초를 버렸다).
            throw new CollectException(sourceName, apiName,
                "API 호출 실패(요청 오류): " + e.getMessage(), e);
        } catch (RestClientException e) {
            // 5xx와 연결 끊김은 되풀이하면 살아나는 부류.
            throw new TransientApiException("API 호출 실패: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new CollectException(sourceName, apiName, "API 호출 실패(잘못된 URI): " + e.getMessage(), e);
        }
    }

    private ParsedPage parse(String sourceName, String apiName, String body) {
        if (body == null || body.isBlank()) {
            throw new TransientApiException("빈 응답", null);
        }

        JSONObject root;
        try {
            root = new JSONObject(body);
        } catch (Exception e) {
            throw new CollectException(sourceName, apiName, "응답 파싱 실패(형식 오류): " + e.getMessage(), e);
        }

        // 실패 시 최상위가 통째로 바뀐다 - resultCode를 찾기 전에 이쪽부터 확인해야 원인이 안 묻힌다.
        JSONObject errorEnvelope = root.optJSONObject("OpenAPI_ServiceResponse");
        if (errorEnvelope != null) {
            JSONObject header = errorEnvelope.optJSONObject("cmmMsgHeader");
            String errMsg = header == null ? "UNKNOWN" : header.optString("errMsg", "UNKNOWN");
            String reasonCode = header == null ? "" : header.optString("returnReasonCode", "");
            String detail = errMsg + " (returnReasonCode=" + reasonCode + ")";
            if (TRANSIENT_REASON_CODE.equals(reasonCode)) {
                throw new TransientApiException(detail, null);
            }
            throw new CollectException(sourceName, apiName, "에어코리아 API 오류 - " + detail);
        }

        JSONObject response = root.optJSONObject("response");
        if (response == null) {
            throw new CollectException(sourceName, apiName, "응답 파싱 실패(response 없음)");
        }

        JSONObject header = response.optJSONObject("header");
        String resultCode = header == null ? "" : header.optString("resultCode", "");
        if (!"00".equals(resultCode)) {
            String resultMsg = header == null ? "UNKNOWN" : header.optString("resultMsg", "UNKNOWN");
            throw new CollectException(sourceName, apiName,
                "에어코리아 API 오류 - resultCode=" + resultCode + " resultMsg=" + resultMsg);
        }

        JSONObject responseBody = response.optJSONObject("body");
        if (responseBody == null) {
            return new ParsedPage(List.of(), 0);
        }
        int totalCount = responseBody.optInt("totalCount", 0);

        // 기상청과 달리 items가 배열 자체다 - 객체로 읽으려 하면 조용히 0건이 된다.
        JSONArray items = responseBody.optJSONArray("items");
        if (items == null) {
            return new ParsedPage(List.of(), totalCount);
        }

        List<String> result = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            result.add(items.getJSONObject(i).toString());
        }
        return new ParsedPage(result, totalCount);
    }

    /** 응답 1페이지 파싱 결과. */
    private record ParsedPage(List<String> items, int totalCount) {
    }

    /** 되풀이하면 성공할 수 있는 실패. 이 클래스 밖으로 새어나가지 않는다(재시도 소진 시 CollectException으로 바뀜). */
    private static final class TransientApiException extends RuntimeException {
        private TransientApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
