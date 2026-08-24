package egovframework.external.rule;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code KmaForecastPivotSupport}/{@code KmaUltraSrtNcstCleanser}가 만든 정제 결과(넓은 형태,
 * 시간 단위 행 - baseDate/baseTime 또는 fcstDate/fcstTime + 카테고리 컬럼들)에서 강수량 시계열만
 * 뽑아낸다.
 *
 * <p>시각은 예보 대상시각({@code fcstDate}/{@code fcstTime})을 우선 쓰고, 없으면(실황은 예보
 * 개념이 없어 이 필드 자체가 없음) 관측시각({@code baseDate}/{@code baseTime})을 쓴다.</p>
 */
public final class WeatherReadingExtractor {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private WeatherReadingExtractor() {
    }

    /**
     * @param cleansedPayload 정제 결과 JSON 배열 원문
     * @param precipField     강수량 컬럼명 - 초단기(ncst/ultra_srt_fcst)는 {@code "rn1"}, 단기예보는 {@code "pcp"}
     */
    public static List<HourlyPrecipitation> extract(String cleansedPayload, String precipField) {
        JSONArray rows = new JSONArray(cleansedPayload);
        List<HourlyPrecipitation> result = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (!row.has(precipField) || row.isNull(precipField)) {
                continue;
            }
            LocalDateTime time = parseTime(row);
            if (time == null) {
                continue; // 시간 파싱 안 되는 행은 방어적으로 건너뜀 - 트리거 계산엔 못 쓰지만 파이프라인은 안 죽임
            }
            double mm = PrecipitationParser.parseMm(row.getString(precipField));
            result.add(new HourlyPrecipitation(time, mm));
        }
        return result;
    }

    private static LocalDateTime parseTime(JSONObject row) {
        String date = row.has("fcstDate") ? row.optString("fcstDate", null) : row.optString("baseDate", null);
        String time = row.has("fcstTime") ? row.optString("fcstTime", null) : row.optString("baseTime", null);
        if (date == null || time == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(date + time, TIMESTAMP);
        } catch (Exception e) {
            return null;
        }
    }
}
