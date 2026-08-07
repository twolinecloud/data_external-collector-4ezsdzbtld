package egovframework.external.publicdata.cleanser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "긴 형태"(카테고리x시간별로 한 항목씩 흩어진) 기상청 예보 데이터를, {@code (fcstDate,fcstTime)}
 * 단위로 묶어 카테고리→값을 한 행에 펼친 "넓은 형태"로 바꾸는 공통 로직.
 *
 * <p>{@code getUltraSrtFcst}(초단기예보)/{@code getVilageFcst}(단기예보) 둘 다
 * {@code baseDate/baseTime/fcstDate/fcstTime/category/fcstValue/nx/ny} 구조를 공유해서
 * 같은 피벗 로직을 재사용한다. {@code getUltraSrtNcst}(초단기실황)는 관측값 필드명이
 * {@code obsrValue}이고 시간 그룹이 항상 1개뿐이라 별도로 처리한다.</p>
 *
 * <p>알려진 카테고리 목록을 미리 넣어 모든 행에 동일한 컬럼 집합이 존재하게 한다 - 어떤
 * 시간대는 특정 카테고리(예: TMN/TMX는 하루 1~2번만)가 없을 수 있는데, 그런 경우 값 없이
 * 컬럼만 {@code null}로 채워서 행마다 컬럼 구성이 들쭉날쭉해지지 않게 한다 (다운스트림에서
 * 고정된 스키마로 다루기 쉽도록).</p>
 */
final class KmaForecastPivotSupport {

    private KmaForecastPivotSupport() {
    }

    static String pivotByForecastTime(String rawPayload, List<String> knownCategories) {
        JSONArray rawItems = new JSONArray(rawPayload);

        Map<String, JSONObject> rowsByTime = new LinkedHashMap<>();
        for (int i = 0; i < rawItems.length(); i++) {
            JSONObject item = rawItems.getJSONObject(i);
            String fcstDate = item.getString("fcstDate");
            String fcstTime = item.getString("fcstTime");
            String timeKey = fcstDate + fcstTime;

            JSONObject row = rowsByTime.computeIfAbsent(timeKey, k -> newRow(item, fcstDate, fcstTime, knownCategories));
            String category = item.getString("category").toLowerCase();
            row.put(category, item.get("fcstValue"));
        }

        JSONArray result = new JSONArray();
        rowsByTime.values().forEach(result::put);
        return result.toString();
    }

    private static JSONObject newRow(JSONObject sampleItem, String fcstDate, String fcstTime, List<String> knownCategories) {
        JSONObject row = new JSONObject();
        row.put("nx", sampleItem.getInt("nx"));
        row.put("ny", sampleItem.getInt("ny"));
        row.put("baseDate", sampleItem.getString("baseDate"));
        row.put("baseTime", sampleItem.getString("baseTime"));
        row.put("fcstDate", fcstDate);
        row.put("fcstTime", fcstTime);
        for (String category : knownCategories) {
            row.put(category.toLowerCase(), JSONObject.NULL);
        }
        return row;
    }
}
