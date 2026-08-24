package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 공공데이터포털 기상청 API 공통 호출 로직. 각 {@code Kma*Collector}가 공유해서 사용.
 *
 * <p>
 * <b>serviceKey 이중 인코딩 주의:</b> 공공데이터포털이 발급하는 serviceKey는 이미
 * URL-encode된 문자열이다. {@code RestTemplate.getForObject(String, ...)}에 문자열을 그대로
 * 넘기면 URI 템플릿으로 취급해 다시 인코딩되어 깨진다 - 그래서 반드시 {@link URI#create}로
 * 직접 URI를 만들어 {@code getForObject(URI, ...)} 오버로드를 사용한다.
 * </p>
 *
 * <p>
 * <b>페이지네이션:</b> {@code numOfRows}는 호출자가 넉넉히 잡아두더라도(예: 단기예보 1000건)
 * 실측 응답이 그 값에 근접한 경우가 있었다(944~980건) - 상한을 살짝 넘는 순간 나머지가 조용히
 * 잘려나가는데 이전엔 {@code totalCount}를 아예 안 읽어서 잘렸는지조차 알 수 없었다. 이제
 * 응답의 {@code totalCount}를 확인해서, 이번 페이지로 다 못 받았으면 {@code pageNo}를 늘려가며
 * 나머지를 마저 받아온다 (무한루프 방지로 {@link #MAX_PAGES} 상한).
 * </p>
 */
@Component
public class KmaApiClient {

    private static final Logger logger = LogManager.getLogger(KmaApiClient.class);
    private static final int MAX_PAGES = 10;

    private final RestTemplate restTemplate;

    public KmaApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<String> call(String sourceName, String apiName, String endpoint, String serviceKey,
            Map<String, String> params) throws CollectException {
        if (endpoint == null || endpoint.isBlank() || serviceKey == null || serviceKey.isBlank()) {
            throw new CollectException(sourceName, apiName, "엔드포인트/서비스키 설정이 비어있음 (미확정)");
        }

        List<String> allItems = new ArrayList<>();
        int pageNo = 1;
        int totalCount = Integer.MAX_VALUE; // 첫 페이지 파싱 전엔 모름 - 일단 더 있다고 가정하고 루프 진입
        int pagesFetched = 0;

        while (allItems.size() < totalCount && pagesFetched < MAX_PAGES) {
            Map<String, String> pageParams = new LinkedHashMap<>(params);
            pageParams.put("pageNo", String.valueOf(pageNo));

            String responseBody = fetch(sourceName, apiName, endpoint, serviceKey, pageParams);
            KmaResponseParser.ParsedPage page = parse(sourceName, apiName, responseBody);

            allItems.addAll(page.items());
            totalCount = page.totalCount();
            pagesFetched++;
            pageNo++;

            if (page.items().isEmpty()) {
                logger.info("[{}] {} - 빈 페이지 수신, 수집 종료.  {}페이지에 걸쳐 {}건 수집 (totalCount={})",
                        sourceName, apiName, pagesFetched, allItems.size(), totalCount);

                break; // totalCount가 부정확하더라도 빈 페이지를 받으면 더 없다고 보고 중단 (무한루프 방지)
            }
        }

        if (allItems.size() < totalCount) {
            logger.warn("[{}] {} - {}페이지({}건)까지 받았지만 totalCount={}에 못 미쳐 나머지를 포기함 (MAX_PAGES 상한)",
                    sourceName, apiName, pagesFetched, allItems.size(), totalCount);
        } else if (pagesFetched > 1) {
            logger.info("[{}] {} - 페이지네이션으로 {}페이지에 걸쳐 {}건 수집 (totalCount={})",
                    sourceName, apiName, pagesFetched, allItems.size(), totalCount);
        }

        return allItems;
    }

    private String fetch(String sourceName, String apiName, String endpoint, String serviceKey,
            Map<String, String> params) {
        StringBuilder url = new StringBuilder(endpoint).append('?').append("serviceKey=").append(serviceKey);
        params.forEach((key, value) -> url.append('&').append(key).append('=').append(value));

        try {
            URI uri = URI.create(url.toString());
            return restTemplate.getForObject(uri, String.class);
        } catch (RestClientException | IllegalArgumentException e) {
            throw new CollectException(sourceName, apiName, "API 호출 실패: " + e.getMessage(), e);
        }
    }

    private KmaResponseParser.ParsedPage parse(String sourceName, String apiName, String responseBody) {
        try {
            return KmaResponseParser.parse(responseBody);
        } catch (KmaApiException e) {
            // JSON 구조는 정상이나 API가 업무적으로 실패를 반환한 경우(쿼터초과/인증만료 등) -
            // "파싱 실패"와 구분해서 로그만 보고도 원인을 바로 알 수 있게 함
            throw new CollectException(sourceName, apiName, "KMA API 오류 - " + e.getMessage(), e);
        } catch (Exception e) {
            throw new CollectException(sourceName, apiName, "응답 파싱 실패(형식 오류): " + e.getMessage(), e);
        }
    }
}
