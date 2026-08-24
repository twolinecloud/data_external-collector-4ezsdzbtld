package egovframework.external.publicdata.collector;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 공공데이터포털 공통 응답 봉투 파싱: {@code response.header.{resultCode,resultMsg}},
 * {@code response.body.{items.item[],totalCount}}. 기상청 단기예보/기상특보 API가 공통으로
 * 이 구조를 씀 (weather-api.docx 응답 예제 참고).
 */
public final class KmaResponseParser {

    private KmaResponseParser() {
    }

    /** 응답 1페이지 파싱 결과. {@code totalCount}가 {@code items.size()}보다 크면 다음 페이지가 더 있다는 뜻 - {@link KmaApiClient}가 이걸로 페이지네이션 여부를 판단한다. */
    public record ParsedPage(List<String> items, int totalCount) {
    }

    /**
     * @return 이번 페이지의 item들 + 전체 건수(totalCount). item 배열이 아니라 단일 객체로 오면
     *         (결과 1건일 때 공공데이터포털 API 공통 특징) 1건짜리로 취급.
     * @throws KmaApiException resultCode가 정상(00)이 아닌 경우 (JSON 구조 자체는 정상 - 업무적 실패)
     */
    public static ParsedPage parse(String responseBody) {
        JSONObject root = new JSONObject(responseBody);
        JSONObject response = root.getJSONObject("response");
        JSONObject header = response.getJSONObject("header");

        String resultCode = header.optString("resultCode", "");
        if (!"00".equals(resultCode)) {
            String resultMsg = header.optString("resultMsg", "UNKNOWN");
            throw new KmaApiException(resultCode, resultMsg);
        }

        JSONObject body = response.optJSONObject("body");
        if (body == null) {
            return new ParsedPage(List.of(), 0);
        }

        int totalCount = body.optInt("totalCount", 0);

        JSONObject items = body.optJSONObject("items");
        if (items == null) {
            return new ParsedPage(List.of(), totalCount);
        }
        Object item = items.opt("item");
        List<String> result = new ArrayList<>();
        if (item instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                result.add(array.getJSONObject(i).toString());
            }
        } else if (item instanceof JSONObject singleItem) {
            result.add(singleItem.toString());
        }
        return new ParsedPage(result, totalCount);
    }
}
