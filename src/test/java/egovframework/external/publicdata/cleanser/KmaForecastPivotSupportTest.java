package egovframework.external.publicdata.cleanser;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link KmaForecastPivotSupport}의 긴 형태 -&gt; 넓은 형태 피벗 로직 단위 테스트.
 * 패키지 전용(package-private)이라 같은 패키지에서만 테스트 가능.
 */
class KmaForecastPivotSupportTest {

    private static final List<String> CATEGORIES = List.of("T1H", "REH", "TMN");

    @Test
    void 같은_시간대의_카테고리들을_한_행으로_피벗한다() {
        String raw = new JSONArray()
            .put(item("20260805", "0600", "T1H", "20"))
            .put(item("20260805", "0600", "REH", "55"))
            .toString();

        String result = KmaForecastPivotSupport.pivotByForecastTime(raw, CATEGORIES, "t1h", "reh", "wsd");

        JSONArray rows = new JSONArray(result);
        assertThat(rows.length()).isEqualTo(1);
        JSONObject row = rows.getJSONObject(0);
        assertThat(row.getString("fcstDate")).isEqualTo("20260805");
        assertThat(row.getString("fcstTime")).isEqualTo("0600");
        assertThat(row.getString("t1h")).isEqualTo("20");
        assertThat(row.getString("reh")).isEqualTo("55");
    }

    @Test
    void 시간대가_다르면_행도_나뉜다() {
        String raw = new JSONArray()
            .put(item("20260805", "0600", "T1H", "20"))
            .put(item("20260805", "0700", "T1H", "21"))
            .toString();

        String result = KmaForecastPivotSupport.pivotByForecastTime(raw, CATEGORIES, "t1h", "reh", "wsd");

        JSONArray rows = new JSONArray(result);
        assertThat(rows.length()).isEqualTo(2);
        assertThat(rows.getJSONObject(0).getString("fcstTime")).isEqualTo("0600");
        assertThat(rows.getJSONObject(1).getString("fcstTime")).isEqualTo("0700");
    }

    @Test
    void 특정_시간대에_없는_카테고리는_null로_채워진다() {
        // TMN(최저기온)은 하루 1~2번만 나옴 - 대부분 시간대에는 없다.
        String raw = new JSONArray()
            .put(item("20260805", "0600", "T1H", "20"))
            .toString();

        String result = KmaForecastPivotSupport.pivotByForecastTime(raw, CATEGORIES, "t1h", "reh", "wsd");

        JSONObject row = new JSONArray(result).getJSONObject(0);
        assertThat(row.isNull("tmn")).isTrue();
        assertThat(row.has("tmn")).isTrue();
    }

    @Test
    void 컬럼_구성이_모든_행에서_동일하다() {
        String raw = new JSONArray()
            .put(item("20260805", "0600", "T1H", "20"))
            .put(item("20260805", "0900", "TMN", "15"))
            .toString();

        String result = KmaForecastPivotSupport.pivotByForecastTime(raw, CATEGORIES, "t1h", "reh", "wsd");

        JSONArray rows = new JSONArray(result);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            assertThat(row.has("t1h")).isTrue();
            assertThat(row.has("reh")).isTrue();
            assertThat(row.has("tmn")).isTrue();
            assertThat(row.has("nx")).isTrue();
            assertThat(row.has("ny")).isTrue();
            assertThat(row.has("baseDate")).isTrue();
            assertThat(row.has("baseTime")).isTrue();
            assertThat(row.has("fcstDate")).isTrue();
            assertThat(row.has("fcstTime")).isTrue();
        }
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
