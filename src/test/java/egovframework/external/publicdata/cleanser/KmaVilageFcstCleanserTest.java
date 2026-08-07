package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmaVilageFcstCleanserTest {

    private final KmaVilageFcstCleanser cleanser = new KmaVilageFcstCleanser();

    @Test
    void operationKey로만_지원여부를_판단한다() {
        assertThat(cleanser.supports("kma-village-forecast-vilage-fcst")).isTrue();
        assertThat(cleanser.supports("kma-village-forecast-ultra-srt-fcst")).isFalse();
    }

    @Test
    void 하루_1_2회만_나오는_TMN_TMX는_해당_시간대_행에만_값이_있고_나머지는_null이다() throws CleanseException {
        String raw = new JSONArray()
            .put(item("20260805", "0600", "TMP", "20"))
            .put(item("20260805", "0900", "TMN", "15"))
            .toString();

        String result = cleanser.cleanse(raw);

        JSONArray rows = new JSONArray(result);
        assertThat(rows.length()).isEqualTo(2);
        JSONObject firstRow = rows.getJSONObject(0);
        assertThat(firstRow.isNull("tmn")).isTrue();
        JSONObject secondRow = rows.getJSONObject(1);
        assertThat(secondRow.getString("tmn")).isEqualTo("15");
    }

    @Test
    void 정제_실패시_CleanseException으로_감싼다() {
        assertThatThrownBy(() -> cleanser.cleanse("{}"))
            .isInstanceOf(CleanseException.class);
    }

    private static JSONObject item(String fcstDate, String fcstTime, String category, String value) {
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
