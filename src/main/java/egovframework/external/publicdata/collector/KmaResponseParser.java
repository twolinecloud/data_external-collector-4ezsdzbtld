package egovframework.external.publicdata.collector;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 공공데이터포털 공통 응답 봉투 파싱: {@code response.header.{resultCode,resultMsg}},
 * {@code response.body.items.item[]}. 기상청 단기예보/기상특보 API가 공통으로 이 구조를 씀
 * (weather-api.docx 응답 예제 참고).
 */
public final class KmaResponseParser {

    private KmaResponseParser() {
    }

    /**
     * @return item 배열 각각을 JSON 원문(String)으로 변환한 리스트. 결과 0건이면 빈 리스트.
     * @throws IllegalStateException resultCode가 정상(00)이 아닌 경우
     */
    public static List<String> extractItems(String responseBody) {
        JSONObject root = new JSONObject(responseBody);
        JSONObject response = root.getJSONObject("response");
        JSONObject header = response.getJSONObject("header");

        String resultCode = header.optString("resultCode", "");
        if (!"00".equals(resultCode)) {
            String resultMsg = header.optString("resultMsg", "UNKNOWN");
            throw new IllegalStateException("resultCode=" + resultCode + " resultMsg=" + resultMsg);
        }

        JSONObject body = response.optJSONObject("body");
        if (body == null) {
            return List.of();
        }
        JSONObject items = body.optJSONObject("items");
        if (items == null) {
            return List.of();
        }
        Object item = items.opt("item");
        List<String> result = new ArrayList<>();
        if (item instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                result.add(array.getJSONObject(i).toString());
            }
        } else if (item instanceof JSONObject singleItem) {
            // 결과 1건일 때는 배열이 아니라 단일 객체로 오는 경우가 있음 (공공데이터포털 API 공통 특징)
            result.add(singleItem.toString());
        }
        return result;
    }
}
