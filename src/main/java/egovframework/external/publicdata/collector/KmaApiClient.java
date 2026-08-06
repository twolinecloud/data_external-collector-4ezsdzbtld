package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 공공데이터포털 기상청 API 공통 호출 로직. 각 {@code Kma*Collector}가 공유해서 사용.
 *
 * <p><b>serviceKey 이중 인코딩 주의:</b> 공공데이터포털이 발급하는 serviceKey는 이미
 * URL-encode된 문자열이다. {@code RestTemplate.getForObject(String, ...)}에 문자열을 그대로
 * 넘기면 URI 템플릿으로 취급해 다시 인코딩되어 깨진다 - 그래서 반드시 {@link URI#create}로
 * 직접 URI를 만들어 {@code getForObject(URI, ...)} 오버로드를 사용한다.</p>
 */
@Component
public class KmaApiClient {

    private final RestTemplate restTemplate;

    public KmaApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<String> call(String sourceName, String apiName, String endpoint, String serviceKey,
                              Map<String, String> params) throws CollectException {
        if (endpoint == null || endpoint.isBlank() || serviceKey == null || serviceKey.isBlank()) {
            throw new CollectException(sourceName, apiName, "엔드포인트/서비스키 설정이 비어있음 (미확정)");
        }

        StringBuilder url = new StringBuilder(endpoint).append('?').append("serviceKey=").append(serviceKey);
        params.forEach((key, value) -> url.append('&').append(key).append('=').append(value));

        String responseBody;
        try {
            URI uri = URI.create(url.toString());
            responseBody = restTemplate.getForObject(uri, String.class);
        } catch (RestClientException | IllegalArgumentException e) {
            throw new CollectException(sourceName, apiName, "API 호출 실패: " + e.getMessage(), e);
        }

        try {
            return KmaResponseParser.extractItems(responseBody);
        } catch (Exception e) {
            throw new CollectException(sourceName, apiName, "응답 파싱 실패: " + e.getMessage(), e);
        }
    }
}
