package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmaUltraSrtFcstCleanserTest {

    private final KmaUltraSrtFcstCleanser cleanser = new KmaUltraSrtFcstCleanser();

    @Test
    void operationKey로만_지원여부를_판단한다() {
        assertThat(cleanser.supports("kma-village-forecast-ultra-srt-fcst")).isTrue();
        assertThat(cleanser.supports("kma-village-forecast-ultra-srt-ncst")).isFalse();
    }

    @Test
    void 시간대별로_피벗된_행을_반환한다() throws CleanseException {
        String raw = new JSONArray()
            .put(fcstItem("20260805", "0600", "T1H", "20"))
            .put(fcstItem("20260805", "0600", "SKY", "1"))
            .put(fcstItem("20260805", "0700", "T1H", "21"))
            .toString();

        String result = cleanser.cleanse(raw);

        JSONArray rows = new JSONArray(result);
        assertThat(rows.length()).isEqualTo(2);
        JSONObject first = rows.getJSONObject(0);
        assertThat(first.getString("t1h")).isEqualTo("20");
        assertThat(first.getString("sky")).isEqualTo("1");
    }

    @Test
    void 기온과_습도가_있으면_체감온도_senstemp를_계산해서_채운다() throws CleanseException {
        String raw = new JSONArray()
            .put(fcstItem("20260805", "0600", "T1H", "30"))
            .put(fcstItem("20260805", "0600", "REH", "70"))
            .toString();

        String result = cleanser.cleanse(raw);

        JSONObject row = new JSONArray(result).getJSONObject(0);
        assertThat(row.isNull("senstemp")).isFalse();
    }

    @Test
    void 정제_실패시_CleanseException으로_감싼다() {
        assertThatThrownBy(() -> cleanser.cleanse("not a json array"))
            .isInstanceOf(CleanseException.class);
    }

    private static JSONObject fcstItem(String fcstDate, String fcstTime, String category, String value) {
        return new JSONObject()
            .put("nx", 60)
            .put("ny", 124)
            .put("baseDate", "20260805")
            .put("baseTime", "0500")
            .put("fcstDate", fcstDate)
            .put("fcstTime", fcstTime)
            .put("category", category)
            .put("fcstValue", value);
    }
}
