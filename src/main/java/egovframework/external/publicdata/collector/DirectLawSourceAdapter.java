package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * {@link LawSourcePort}의 현재 활성 구현 - 국가법령정보센터(law.go.kr) Open API에 직접 연결.
 *
 * <p>ESB 전환 전까지의 임시 어댑터다. {@code OC}(이용자ID)와 엔드포인트만
 * {@code application.yml}에서 주입받고, 실제 HTTP 호출 로직은 이 클래스 안에만 있어서
 * ESB로 전환할 때 이 클래스를 대체할 어댑터 하나만 추가하면 된다.</p>
 *
 * <p><b>{@code target=law}(MST 기준) → {@code target=eflaw}(법령명 기준) 전환(2026-08-28)</b>:
 * MST(법령일련번호)는 법령이 개정될 때마다 새로 발급되는 값이라, 수집 대상 목록에 MST를
 * 고정해두면 개정 이후 옛 버전만 계속 가져오는 문제가 있었음({@link MolegLaw} 클래스 주석
 * 참고). {@code target=eflaw}는 법령명(파라미터 {@code LM})으로 "현재 시행 중인 버전"을
 * 매번 새로 찾기 때문에, 대상 목록의 법령명만 정확하면 개정이 코드/설정 변경 없이 자동
 * 반영된다 - 전환 당시 실 API로 60건(그때 전체 대상) 전수 검증 완료(전부 성공). 이후
 * 2026-08-28 목록이 법령 433건 + 행정규칙 58건으로 확대됐고, 각 명칭은 {@code lawSearch.do}
 * 검색으로 공식 존재를 확인한 뒤 목록에 편입했다 - {@code lawService.do} 본문조회(이 클래스가
 * 실제 매일 호출하는 API)로의 전수 재검증은 아직 별도로 안 함(private-doc 39번 항목).</p>
 *
 * <p><b>응답 형태 주의:</b> 기상청과 달리 resultCode/resultMsg 봉투가 없다. 법령 성공 시
 * 최상위 키가 {@code "법령"}, 실패(존재하지 않는 법령명 등)하면 {@code "Law"} 키에 에러 메시지
 * 문자열만 온다 - 실제 API로 두 경우 다 확인한 내용. 다만 법령명이 전혀 매칭 안 되는 경우는
 * 이 JSON 에러 봉투 대신 HTML 에러 페이지("미신청된 목록/본문에 대한 접근입니다")가 오는
 * 것도 실측 확인 - 아래 {@link #validateLaw}의 JSON 파싱 실패 처리 경로가 이미 이 경우도
 * 안전하게 {@link CollectException}으로 감싼다(추가 대응 불필요).</p>
 *
 * <p><b>행정규칙({@code target=admrul}) 지원(2026-08-28 추가)</b>: {@link #fetchAdminRuleBody}는
 * 같은 엔드포인트에 {@code target=admrul}만 바꿔 호출한다 - 실 API로 명칭 기준 조회 지원
 * 확인함. 다만 응답 봉투가 법령과 다르다: 성공 시 최상위 키가 {@code "AdmRulService"}이고
 * 내부 필드 구성도 다르다(개정문/별표/행정규칙기본정보/조문내용/첨부파일/부칙/제개정이유 -
 * 법령의 {@code 조문} 중첩 구조와 다름). {@link #validateAdminRule} 참고.</p>
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
    public String fetchLawBody(String sourceName, String apiName, String lawName) throws CollectException {
        String responseBody = call(sourceName, apiName, "eflaw", lawName);
        return validateLaw(sourceName, apiName, lawName, responseBody);
    }

    @Override
    public String fetchAdminRuleBody(String sourceName, String apiName, String ruleName) throws CollectException {
        String responseBody = call(sourceName, apiName, "admrul", ruleName);
        return validateAdminRule(sourceName, apiName, ruleName, responseBody);
    }

    private String call(String sourceName, String apiName, String target, String name) throws CollectException {
        if (endpoint == null || endpoint.isBlank() || oc == null || oc.isBlank()) {
            throw new CollectException(sourceName, apiName, "엔드포인트/OC(이용자ID) 설정이 비어있음 (미확정)");
        }

        String lm = URLEncoder.encode(name, StandardCharsets.UTF_8);
        String url = endpoint + "?OC=" + oc + "&target=" + target + "&LM=" + lm + "&type=JSON";
        try {
            URI uri = URI.create(url);
            return restTemplate.getForObject(uri, String.class);
        } catch (RestClientException | IllegalArgumentException e) {
            throw new CollectException(sourceName, apiName, "API 호출 실패: " + e.getMessage(), e);
        }
    }

    private String validateLaw(String sourceName, String apiName, String lawName, String responseBody) throws CollectException {
        try {
            JSONObject json = new JSONObject(responseBody);
            if (json.has("법령")) {
                return responseBody;
            }
            String errorMsg = json.has("Law") ? json.optString("Law") : responseBody;
            throw new CollectException(sourceName, apiName, "법령 조회 실패(법령명=" + lawName + "): " + errorMsg);
        } catch (CollectException e) {
            throw e;
        } catch (Exception e) {
            throw new CollectException(sourceName, apiName, "응답 파싱 실패(형식 오류): " + e.getMessage(), e);
        }
    }

    /**
     * {@code admrul}은 법령과 달리 실패 시 에러 메시지가 담기는 키가 실측 확인되지 않았다
     * ({@code target=eflaw}의 "Law" 키에 대응하는 값 - private-doc에 미기재) - 그래서 성공
     * 키({@code "AdmRulService"}) 부재만 판정하고, 에러 메시지는 추측해서 파싱하지 않고 응답
     * 원문 그대로를 실패 사유에 담는다(추정 필드명으로 잘못 짚어 에러 내용을 숨기는 것보다
     * 안전).
     */
    private String validateAdminRule(String sourceName, String apiName, String ruleName, String responseBody) throws CollectException {
        try {
            JSONObject json = new JSONObject(responseBody);
            if (json.has("AdmRulService")) {
                return responseBody;
            }
            throw new CollectException(sourceName, apiName, "행정규칙 조회 실패(행정규칙명=" + ruleName + "): " + responseBody);
        } catch (CollectException e) {
            throw e;
        } catch (Exception e) {
            throw new CollectException(sourceName, apiName, "응답 파싱 실패(형식 오류): " + e.getMessage(), e);
        }
    }
}
