package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

/**
 * {@link LawSourcePort}의 현재 활성 구현 - 국가법령정보센터(law.go.kr) Open API에 직접 연결.
 *
 * <p>ESB 전환 전까지의 임시 어댑터다. {@code OC}(이용자ID)와 엔드포인트만
 * {@code application.yml}에서 주입받고, 실제 HTTP 호출 로직은 이 클래스 안에만 있어서
 * ESB로 전환할 때 이 클래스를 대체할 어댑터 하나만 추가하면 된다.</p>
 *
 * <p><b>응답 형태 주의:</b> 기상청과 달리 resultCode/resultMsg 봉투가 없다. 성공하면
 * 최상위 키가 {@code "법령"}, 실패(존재하지 않는 MST 등)하면 {@code "Law"} 키에 에러 메시지
 * 문자열만 온다 - 실제 API로 두 경우 다 확인한 내용.</p>
 */
@Component
public class DirectLawSourceAdapter implements LawSourcePort {

    private final RestTemplate restTemplate;
    private final String endpoint;
    private final String oc;

    public DirectLawSourceAdapter(
        RestTemplate restTemplate,
        @Value("${public-data.moleg.endpoint}") String endpoint,
        @Value("${public-data.moleg.oc:}") String oc
    ) {
        this.restTemplate = restTemplate;
        this.endpoint = endpoint;
        this.oc = oc;
    }

    @Override
    public String fetchLawBody(String sourceName, String apiName, String mst) throws CollectException {
        if (endpoint == null || endpoint.isBlank() || oc == null || oc.isBlank()) {
            throw new CollectException(sourceName, apiName, "엔드포인트/OC(이용자ID) 설정이 비어있음 (미확정)");
        }

        String url = endpoint + "?OC=" + oc + "&target=law&MST=" + mst + "&type=JSON";
        String responseBody;
        try {
            URI uri = URI.create(url);
            responseBody = restTemplate.getForObject(uri, String.class);
        } catch (RestClientException | IllegalArgumentException e) {
            throw new CollectException(sourceName, apiName, "API 호출 실패: " + e.getMessage(), e);
        }

        return validate(sourceName, apiName, mst, responseBody);
    }

    private String validate(String sourceName, String apiName, String mst, String responseBody) throws CollectException {
        try {
            JSONObject json = new JSONObject(responseBody);
            if (json.has("법령")) {
                return responseBody;
            }
            String errorMsg = json.has("Law") ? json.optString("Law") : responseBody;
            throw new CollectException(sourceName, apiName, "법령 조회 실패(MST=" + mst + "): " + errorMsg);
        } catch (CollectException e) {
            throw e;
        } catch (Exception e) {
            throw new CollectException(sourceName, apiName, "응답 파싱 실패(형식 오류): " + e.getMessage(), e);
        }
    }
}
