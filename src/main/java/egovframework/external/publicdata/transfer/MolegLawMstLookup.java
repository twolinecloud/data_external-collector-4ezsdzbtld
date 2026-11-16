package egovframework.external.publicdata.transfer;

import egovframework.external.publicdata.collector.LawNameConfusables;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 법제처 {@code lawSearch.do}({@code target=eflaw})로 법령의 MST(법령일련번호)를 찾는다 - 법령명으로 검색해 법령ID가
 * 같은 항목을 고른다. 전송 대상으로 확정된 문서(새로 생겼거나 바뀐 것)에만 부르므로 평소엔 하루 몇 건이고, 처음
 * 전체를 보낼 때만 법령 수만큼(약 430건) 호출한다.
 *
 * <p>현행({@code nw=3})에서 먼저 찾고, 없으면 시행예정({@code nw=2})에서 찾는다 - 시행일이 아직 안 된 법령은
 * 현행 목록에 없다. 현행은 법령ID당 한 건이라 시행일자가 달라도 그 항목을 쓰지만, 시행예정은 같은 법령ID가 여러 버전이라
 * 시행일자가 본문과 정확히 같은 항목만 쓴다(다른 버전의 MST를 붙이지 않으려고).</p>
 */
@Component
public class MolegLawMstLookup implements LawMstLookup {

    private static final String[] SCOPES = {"3", "2"}; // 3=현행, 2=시행예정

    private final HttpClient client;
    private final String searchEndpoint;
    private final String oc;

    public MolegLawMstLookup(
        @Value("${public-data.moleg.endpoint:https://www.law.go.kr/DRF/lawService.do}") String lawServiceEndpoint,
        @Value("${public-data.moleg.oc:}") String oc
    ) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        this.searchEndpoint = lawServiceEndpoint.replace("lawService.do", "lawSearch.do");
        this.oc = oc;
    }

    @Override
    public String find(LawDocument doc) throws LookupException {
        if (oc == null || oc.isBlank()) {
            throw new LookupException("법제처 OC(이용자ID) 설정이 비어 있어 MST를 조회할 수 없음");
        }
        String query = URLEncoder.encode(LawNameConfusables.normalizeForLaw(doc.name()), StandardCharsets.UTF_8);
        for (String scope : SCOPES) {
            boolean current = "3".equals(scope);
            String mst = pick(search(query, scope), doc.lawId(), doc.effectiveDate(), current);
            if (mst != null) {
                return mst;
            }
        }
        return null;
    }

    private String search(String encodedQuery, String scope) throws LookupException {
        String url = searchEndpoint + "?OC=" + oc + "&target=eflaw&type=JSON&nw=" + scope
            + "&display=100&query=" + encodedQuery;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new LookupException("법령 검색 HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new LookupException("법령 검색 호출 실패: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LookupException("법령 검색 중단", e);
        }
    }

    /**
     * 검색 응답에서 법령ID가 같은 항목의 법령일련번호를 고른다. 시행일자가 같은 항목이 있으면 그것, 없으면
     * {@code fallbackToFirst}일 때만 법령ID가 같은 첫 항목.
     *
     * @return MST. 일치하는 항목이 없으면 null
     * @throws LookupException JSON이 아닌 응답(HTML 오류 페이지 등)
     */
    static String pick(String responseBody, String lawId, String effectiveDate, boolean fallbackToFirst)
        throws LookupException {
        Object law;
        try {
            JSONObject root = new JSONObject(responseBody);
            JSONObject search = root.optJSONObject("LawSearch");
            law = (search == null ? root : search).opt("law");
        } catch (JSONException e) {
            throw new LookupException("법령 검색 응답이 JSON이 아님: " + e.getMessage(), e);
        }
        JSONArray items = law instanceof JSONArray a ? a : law instanceof JSONObject o ? new JSONArray().put(o) : new JSONArray();
        String first = null;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null || !lawId.equals(item.optString("법령ID")) || item.optString("법령일련번호").isBlank()) {
                continue;
            }
            if (effectiveDate != null && effectiveDate.equals(item.optString("시행일자"))) {
                return item.getString("법령일련번호");
            }
            if (first == null) {
                first = item.getString("법령일련번호");
            }
        }
        return fallbackToFirst ? first : null;
    }
}
