package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 초단기실황(getUltraSrtNcst) 정제기. 관측 스냅샷 1개 시점(baseDate/baseTime)뿐이라
 * 시간 그룹핑이 필요 없음 - category→obsrValue를 한 행으로 피벗해서 배열(길이 1)로 반환.
 * 값 필드명이 예보류(fcstValue)와 달리 {@code obsrValue}인 점 주의.
 */
@Component
public class KmaUltraSrtNcstCleanser implements PublicDataCleanser {

    /** weather-api.docx 기준 초단기실황 카테고리. */
    private static final List<String> CATEGORIES = List.of("T1H", "RN1", "REH", "PTY", "VEC", "WSD", "UUU", "VVV");

    /** raw item(단일 관측치)의 알려진 필드 전체 - 전부 매 항목에 항상 있어야 함(선택 필드 없음). */
    private static final Set<String> RAW_ITEM_FIELDS = Set.of("nx", "ny", "baseDate", "baseTime", "category", "obsrValue");

    @Override
    public boolean supports(String operationKey) {
        return "kma-village-forecast-ultra-srt-ncst".equals(operationKey);
    }

    @Override
    public List<StructureProbe> structureProbes() {
        return List.of(new StructureProbe("raw-item", RAW_ITEM_FIELDS, RAW_ITEM_FIELDS, StructureProbeSupport::unionKeys));
    }

    @Override
    public String cleanse(String rawPayload) throws CleanseException {
        try {
            JSONArray rawItems = new JSONArray(rawPayload);
            if (rawItems.isEmpty()) {
                return new JSONArray().toString();
            }

            JSONObject sample = rawItems.getJSONObject(0);
            JSONObject row = new JSONObject();
            row.put("nx", sample.getInt("nx"));
            row.put("ny", sample.getInt("ny"));
            row.put("baseDate", sample.getString("baseDate"));
            row.put("baseTime", sample.getString("baseTime"));
            for (String category : CATEGORIES) {
                row.put(category.toLowerCase(), JSONObject.NULL);
            }

            for (int i = 0; i < rawItems.length(); i++) {
                JSONObject item = rawItems.getJSONObject(i);
                row.put(item.getString("category").toLowerCase(), item.get("obsrValue"));
            }

            return new JSONArray().put(row).toString();
        } catch (Exception e) {
            throw new CleanseException("공공데이터포털 (기상청 동네예보)", "초단기실황조회", "정제 실패: " + e.getMessage(), e);
        }
    }
}
