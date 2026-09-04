package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import egovframework.external.publicdata.collector.AirKoreaStationFacilityLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 에어코리아 정제기 - {@code KmaAsosHourlyCleanser}와 매칭 방향은 같지만(시설 기준 최근접
 * 1개), 매칭 키가 지점번호가 아니라 <b>측정소명 문자열</b>이라는 점에 초점을 맞춘다.
 */
class AirKoreaRealtimeCleanserTest {

    private final AirKoreaRealtimeCleanser cleanser = new AirKoreaRealtimeCleanser(new AirKoreaStationFacilityLoader());

    @Test
    void operationKey로만_지원여부를_판단한다() {
        assertThat(cleanser.supports("airkorea-realtime-measure")).isTrue();
        assertThat(cleanser.supports("kma-asos-hourly")).isFalse();
    }

    @Test
    void 매핑된_측정소명은_facilityId를_붙여_통과시킨다() throws CleanseException {
        // 별양동 = 서울지방교정청(1270254)/서울구치소(1270552) 둘 다의 실측 최근접 측정소.
        String raw = new JSONArray()
            .put(stationRow("별양동", "28", "-"))
            .toString();

        String result = cleanser.cleanse(raw);

        JSONArray rows = new JSONArray(result);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            assertThat(row.getString("stationName")).isEqualTo("별양동");
            assertThat(row.getString("pm10Value")).isEqualTo("28");
            assertThat(row.has("stationDistanceKm")).isTrue();
        }
    }

    @Test
    void 같은_측정소를_공유하는_기관은_둘_다_한_행씩_나온다() throws CleanseException {
        // 처음엔 "측정소명 -> 기관" 역방향 Map으로 짰다가, 서울지방교정청과 서울구치소가
        // 같은 최근접 측정소("별양동")를 공유해서 하나가 조용히 덮어써지는 걸 이 테스트로 잡았다
        // (2026-09-04). ASOS와 같은 "기관 목록을 순회" 방향이어야 둘 다 살아남는다.
        String raw = new JSONArray()
            .put(stationRow("별양동", "28", "-"))
            .toString();

        JSONArray rows = new JSONArray(cleanser.cleanse(raw));

        Set<String> facilityIds = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            facilityIds.add(rows.getJSONObject(i).getString("facilityId"));
        }
        assertThat(facilityIds).contains("1270254", "1270552");
    }

    @Test
    void 매핑되지_않은_측정소는_조용히_건너뛴다() throws CleanseException {
        // 673개 측정소 대부분이 59개 기관 어디의 최근접도 아니다 - 정상 상황이라 경고 없이 skip.
        String raw = new JSONArray()
            .put(stationRow("존재하지않는측정소", "20", "1"))
            .toString();

        String result = cleanser.cleanse(raw);

        assertThat(new JSONArray(result).length()).isEqualTo(0);
    }

    @Test
    void 통신장애_결측을_원본_그대로_남긴다() throws CleanseException {
        String raw = new JSONArray()
            .put(stationRow("별양동", "-", "통신장애"))
            .toString();

        JSONObject row = new JSONArray(cleanser.cleanse(raw)).getJSONObject(0);

        assertThat(row.getString("pm10Value")).isEqualTo("-");
        assertThat(row.getString("pm10Flag")).isEqualTo("통신장애");
    }

    /** 에어코리아 실측(2026-09-03) 응답과 같은 모양의 station row. */
    private JSONObject stationRow(String stationName, String pm10Value, String pm10Flag) {
        JSONObject o = new JSONObject();
        o.put("stationName", stationName);
        o.put("sidoName", "서울");
        o.put("dataTime", "2026-09-04 10:00");
        o.put("pm10Value", pm10Value);
        o.put("pm10Flag", pm10Flag);
        return o;
    }
}
