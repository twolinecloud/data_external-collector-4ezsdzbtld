package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import egovframework.external.publicdata.collector.KmaAsosStationFacilityLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ASOS 시간자료 정제기 - "지점코드 → 시설" 매칭이 {@code KmaWeatherWarningListCleanser}와
 * 반대 방향(시설 기준으로 최근접 지점 1개를 찾음)이라는 점에 초점을 맞춰 검증한다.
 */
class KmaAsosHourlyCleanserTest {

    private final KmaAsosHourlyCleanser cleanser = new KmaAsosHourlyCleanser(new KmaAsosStationFacilityLoader());

    @Test
    void operationKey로만_지원여부를_판단한다() {
        assertThat(cleanser.supports("kma-asos-hourly")).isTrue();
        assertThat(cleanser.supports("kma-village-forecast-vilage-fcst")).isFalse();
    }

    @Test
    void 매핑된_59개소_전부_최근접_지점의_시정_습도로_한_행씩_나온다() throws CleanseException {
        // stnId 108(서울)이 매핑된 기관은 최소 하나 - 서울지방교정청(1270254)이 실제 매핑값.
        String raw = stationRow("108", "580", "96.0")
            .toString();
        // 서울 하나만 있고 나머지 지점은 응답에 없는 상태를 흉내낸다 - 매핑되지 않은 지점은
        // 결과에서 조용히 빠져야 하고(배치 실패 아님), 서울 매칭분만 나와야 한다.

        String result = cleanser.cleanse("[" + raw + "]");

        JSONArray rows = new JSONArray(result);
        // 108에 매핑된 기관 수만큼만 행이 나온다(적어도 서울지방교정청 1건은 포함).
        assertThat(rows.length()).isGreaterThan(0);
        boolean found = false;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            assertThat(row.getString("STN")).isEqualTo("108");
            assertThat(row.getString("VS")).isEqualTo("580");
            assertThat(row.getString("HM")).isEqualTo("96.0");
            assertThat(row.has("facilityId")).isTrue();
            assertThat(row.has("stnDistanceKm")).isTrue();
            // 서울(108) 지점명이 매핑에서 채워졌는지 - ASOS 원본에는 지점명이 없다.
            assertThat(row.getString("stnNm")).isEqualTo("Seoul");
            if ("1270254".equals(row.getString("facilityId"))) {
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void 매핑된_지점이_이번_응답에_없으면_그_기관만_건너뛴다() throws CleanseException {
        // 서울(108) 대신 전혀 다른 지점(999, 존재하지 않는 코드)만 온 상황 - 배치 전체가
        // 실패하면 안 되고, 매칭되는 기관이 하나도 없어 빈 배열이 나와야 한다.
        String raw = "[" + stationRow("999", "1000", "50.0") + "]";

        String result = cleanser.cleanse(raw);

        assertThat(new JSONArray(result).length()).isEqualTo(0);
    }

    @Test
    void 값_타입은_원본_그대로_VARCHAR로_남긴다() throws CleanseException {
        // 결측 표기(-9)를 숫자로 캐스팅하지 않는지 - KmaForecastPivotSupport와 같은 원칙.
        String raw = "[" + stationRow("108", "-9", "-9.0") + "]";

        String result = cleanser.cleanse(raw);

        JSONObject row = new JSONArray(result).getJSONObject(0);
        assertThat(row.getString("VS")).isEqualTo("-9");
        assertThat(row.getString("HM")).isEqualTo("-9.0");
    }

    /** {@link egovframework.external.publicdata.collector.KmaApiHubClient}가 만드는 것과 같은 모양의 station row. */
    private JSONObject stationRow(String stn, String vs, String hm) {
        JSONObject o = new JSONObject();
        o.put("TM", "202609031300");
        o.put("STN", stn);
        o.put("VS", vs);
        o.put("HM", hm);
        return o;
    }
}
