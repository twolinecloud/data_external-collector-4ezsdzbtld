package egovframework.external.publicdata.cleanser;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link ApparentTemperatureCalculator}의 여름철/겨울철 공식 분기, 산출 불가 조건,
 * 입력 누락 시 처리를 검증. 정확한 소수점까지의 "정답값"을 박아넣는 대신(수식이 복잡해
 * 손계산 오차 위험이 큼) 물리적으로 당연한 방향성(습도가 높을수록 더 덥게, 바람이 강할수록
 * 더 춥게 느껴짐)과 경계조건만 검증한다.
 */
class ApparentTemperatureCalculatorTest {

    @Test
    void 여름철_5월에서_9월_사이에는_습구온도_기반_공식을_쓴다() {
        JSONObject row = row("t1h", "30", "reh", "70", "wsd", null);

        ApparentTemperatureCalculator.enrich(row, "20260815", "t1h", "reh", "wsd");

        assertThat(row.isNull("senstemp")).isFalse();
        // 고온다습이면 체감온도가 실제 기온보다 높게 나오는 게 정상.
        assertThat(row.getDouble("senstemp")).isGreaterThan(30.0);
    }

    @Test
    void 여름철_습도가_높을수록_체감온도도_높다() {
        JSONObject lowHumidity = row("t1h", "30", "reh", "40", "wsd", null);
        JSONObject highHumidity = row("t1h", "30", "reh", "90", "wsd", null);

        ApparentTemperatureCalculator.enrich(lowHumidity, "20260815", "t1h", "reh", "wsd");
        ApparentTemperatureCalculator.enrich(highHumidity, "20260815", "t1h", "reh", "wsd");

        assertThat(highHumidity.getDouble("senstemp")).isGreaterThan(lowHumidity.getDouble("senstemp"));
    }

    @Test
    void 겨울철_10월에서_익년_4월_사이_기온10도이하_풍속1_3이상이면_풍속기반_공식을_쓴다() {
        JSONObject row = row("t1h", "0", "reh", null, "wsd", "5.0");

        ApparentTemperatureCalculator.enrich(row, "20260115", "t1h", "reh", "wsd");

        assertThat(row.isNull("senstemp")).isFalse();
        // 바람이 강하면 체감온도가 실제 기온보다 낮게 나오는 게 정상(windchill).
        assertThat(row.getDouble("senstemp")).isLessThan(0.0);
    }

    @Test
    void 겨울철_바람이_강할수록_체감온도가_더_낮다() {
        JSONObject weakWind = row("t1h", "0", "reh", null, "wsd", "1.5");
        JSONObject strongWind = row("t1h", "0", "reh", null, "wsd", "10.0");

        ApparentTemperatureCalculator.enrich(weakWind, "20260115", "t1h", "reh", "wsd");
        ApparentTemperatureCalculator.enrich(strongWind, "20260115", "t1h", "reh", "wsd");

        assertThat(strongWind.getDouble("senstemp")).isLessThan(weakWind.getDouble("senstemp"));
    }

    @Test
    void 겨울철이라도_기온이_10도_초과면_산출하지_않는다() {
        JSONObject row = row("t1h", "15", "reh", null, "wsd", "5.0");

        ApparentTemperatureCalculator.enrich(row, "20260115", "t1h", "reh", "wsd");

        assertThat(row.isNull("senstemp")).isTrue();
    }

    @Test
    void 겨울철이라도_풍속이_1_3미만이면_산출하지_않는다() {
        JSONObject row = row("t1h", "0", "reh", null, "wsd", "1.0");

        ApparentTemperatureCalculator.enrich(row, "20260115", "t1h", "reh", "wsd");

        assertThat(row.isNull("senstemp")).isTrue();
    }

    @Test
    void 기온이_없으면_계절과_무관하게_산출하지_않는다() {
        JSONObject row = row("t1h", null, "reh", "70", "wsd", "5.0");

        ApparentTemperatureCalculator.enrich(row, "20260815", "t1h", "reh", "wsd");

        assertThat(row.isNull("senstemp")).isTrue();
    }

    @Test
    void 여름철에_습도가_없으면_산출하지_않는다() {
        JSONObject row = row("t1h", "30", "reh", null, "wsd", "3.0");

        ApparentTemperatureCalculator.enrich(row, "20260815", "t1h", "reh", "wsd");

        assertThat(row.isNull("senstemp")).isTrue();
    }

    @Test
    void 소수점_첫째자리로_반올림한다() {
        JSONObject row = row("t1h", "30", "reh", "70", "wsd", null);

        ApparentTemperatureCalculator.enrich(row, "20260815", "t1h", "reh", "wsd");

        double senstemp = row.getDouble("senstemp");
        assertThat(senstemp).isCloseTo(Math.round(senstemp * 10.0) / 10.0, within(0.001));
    }

    private static JSONObject row(String tempField, String tempValue, String humField, String humValue, String windField, String windValue) {
        JSONObject row = new JSONObject();
        row.put(tempField, tempValue == null ? JSONObject.NULL : tempValue);
        row.put(humField, humValue == null ? JSONObject.NULL : humValue);
        row.put(windField, windValue == null ? JSONObject.NULL : windValue);
        return row;
    }
}
