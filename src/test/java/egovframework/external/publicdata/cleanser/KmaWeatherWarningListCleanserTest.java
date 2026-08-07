package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 기상특보목록은 이미 넓은 형태라 피벗 없이 통과시키되, title 필드 존재만 검증한다.
 */
class KmaWeatherWarningListCleanserTest {

    private final KmaWeatherWarningListCleanser cleanser = new KmaWeatherWarningListCleanser();

    @Test
    void operationKey로만_지원여부를_판단한다() {
        assertThat(cleanser.supports("kma-weather-warning-list")).isTrue();
        assertThat(cleanser.supports("kma-village-forecast-vilage-fcst")).isFalse();
    }

    @Test
    void title이_있으면_원문_그대로_통과시킨다() throws CleanseException {
        String raw = new JSONArray()
            .put(new JSONObject().put("title", "호우주의보").put("stnId", "108"))
            .toString();

        String result = cleanser.cleanse(raw);

        JSONArray rows = new JSONArray(result);
        assertThat(rows.length()).isEqualTo(1);
        assertThat(rows.getJSONObject(0).getString("title")).isEqualTo("호우주의보");
    }

    @Test
    void title이_없으면_CleanseException을_던진다() {
        String raw = new JSONArray().put(new JSONObject().put("stnId", "108")).toString();

        assertThatThrownBy(() -> cleanser.cleanse(raw))
            .isInstanceOf(CleanseException.class);
    }
}
