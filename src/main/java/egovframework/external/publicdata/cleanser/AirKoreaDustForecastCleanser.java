package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import egovframework.external.publicdata.collector.AirKoreaDustForecastFacility;
import egovframework.external.publicdata.collector.AirKoreaDustForecastFacilityLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 에어코리아 대기질예보통보(airkorea-dust-forecast) 정제기. 내일 황사 유도의 데이터 원본을
 * 만든다.
 *
 * <p><b>informCode를 PM10만 남긴다</b> - 원본엔 PM2.5/O3 예보도 같이 오지만 우리 용도(황사
 * 대리지표)엔 PM10만 쓴다({@code AirKoreaDustForecastCollector} 클래스 주석 참고).</p>
 *
 * <p><b>같은 예보일자(informData)에 여러 발표가 누적돼 있으면 가장 최근 발표만 남긴다</b> -
 * {@code dataTime}이 "2026-09-04 05시 발표"/"2026-09-04 11시 발표"처럼 그날 발표된 것들이
 * 응답에 다 실려 오는데(실측), 05시 발표는 11시 발표로 이미 갱신된 정보라 버려야 한다
 * (재난문자 정제기 이전에도 없던 개념이라 여기서 새로 처리 - 기상특보 정제기의
 * "발표시각 가장 늦은 것만 신뢰" 원칙과 동일).</p>
 *
 * <p>{@link egovframework.external.publicdata.cleanser.KmaAsosHourlyCleanser}와 같은
 * "기관 목록을 순회하며 원본에서 자기 값을 찾는" 방향 - 여기서는 지역명(informRegion)으로
 * {@code informGrade} 문자열(쉼표+콜론 구분 목록)을 파싱해서 찾는다.</p>
 *
 * <p><b>판정 없이 원본 등급 저장(2026-09-04)</b>: API가 이미 "좋음/보통/나쁨/매우나쁨" 등급
 * 문자열을 주므로 우리가 추가로 임계값을 정할 건 없지만, 이 등급을 화면의 "황사" 기호로
 * 바로 쓸지(예: 나쁨 이상 → 황사 표시) 기준은 여전히 기획 확정 대기다.</p>
 */
@Component
public class AirKoreaDustForecastCleanser implements PublicDataCleanser {

    private static final Set<String> RAW_ITEM_FIELDS = Set.of(
        "informCode", "informData", "informGrade", "dataTime", "informCause", "informOverall");
    private static final String TARGET_INFORM_CODE = "PM10";

    private final List<AirKoreaDustForecastFacility> mappings;

    public AirKoreaDustForecastCleanser(AirKoreaDustForecastFacilityLoader mappingLoader) {
        this.mappings = mappingLoader.all();
    }

    @Override
    public boolean supports(String operationKey) {
        return "airkorea-dust-forecast".equals(operationKey);
    }

    @Override
    public List<StructureProbe> structureProbes() {
        return List.of(new StructureProbe("raw-item", RAW_ITEM_FIELDS, RAW_ITEM_FIELDS, StructureProbeSupport::unionKeys));
    }

    @Override
    public String cleanse(String rawPayload) throws CleanseException {
        try {
            JSONArray rawItems = new JSONArray(rawPayload);
            // informData(날짜)별로 가장 최근 발표(dataTime) 항목만 남긴다.
            Map<String, JSONObject> latestByDate = new HashMap<>();
            for (int i = 0; i < rawItems.length(); i++) {
                JSONObject item = rawItems.getJSONObject(i);
                if (!TARGET_INFORM_CODE.equals(item.optString("informCode", null))) {
                    continue;
                }
                String informData = item.getString("informData");
                JSONObject existing = latestByDate.get(informData);
                if (existing == null || isLater(item, existing)) {
                    latestByDate.put(informData, item);
                }
            }

            JSONArray result = new JSONArray();
            for (JSONObject item : latestByDate.values()) {
                Map<String, String> gradeByRegion = parseInformGrade(item.getString("informGrade"));
                for (AirKoreaDustForecastFacility mapping : mappings) {
                    String grade = gradeByRegion.get(mapping.informRegion());
                    if (grade == null) {
                        continue; // 예보권역 이름이 응답과 어긋나는 경우 - 구조 드리프트로 별도 감지됨
                    }
                    result.put(toRow(mapping, item, grade));
                }
            }
            return result.toString();
        } catch (Exception e) {
            throw new CleanseException("공공데이터포털 (한국환경공단 에어코리아)", "대기질예보통보",
                "정제 실패: " + e.getMessage(), e);
        }
    }

    private boolean isLater(JSONObject a, JSONObject b) {
        return parseDataTime(a.getString("dataTime")).isAfter(parseDataTime(b.getString("dataTime")));
    }

    /** "2026-09-04 05시 발표" -> LocalDateTime. 형식이 어긋나면 항상 "더 최근"으로 보지 않아 최소 하나는 살아남는다. */
    private LocalDateTime parseDataTime(String dataTime) {
        try {
            String cleaned = dataTime.replace("발표", "").trim(); // "2026-09-04 05시"
            String[] parts = cleaned.split("\\s+");
            LocalDate date = LocalDate.parse(parts[0]);
            int hour = Integer.parseInt(parts[1].replace("시", ""));
            return date.atTime(hour, 0);
        } catch (Exception e) {
            return LocalDateTime.MIN;
        }
    }

    /** "서울 : 좋음,제주 : 좋음,..." -> {"서울":"좋음", "제주":"좋음", ...} */
    private Map<String, String> parseInformGrade(String informGrade) {
        Map<String, String> result = new HashMap<>();
        for (String pair : informGrade.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                result.put(kv[0].trim(), kv[1].trim());
            }
        }
        return result;
    }

    private JSONObject toRow(AirKoreaDustForecastFacility mapping, JSONObject item, String grade) {
        JSONObject row = new JSONObject();
        row.put("facilityId", mapping.facilityId());
        row.put("informRegion", mapping.informRegion());
        row.put("informData", item.getString("informData"));
        row.put("dataTime", item.getString("dataTime"));
        row.put("grade", grade);
        row.put("informCause", item.optString("informCause", ""));
        row.put("informOverall", item.optString("informOverall", ""));
        return row;
    }
}
