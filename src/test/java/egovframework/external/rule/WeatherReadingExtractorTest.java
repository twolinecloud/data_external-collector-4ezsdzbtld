package egovframework.external.rule;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherReadingExtractorTest {

    @Test
    void 초단기실황은_baseDate_baseTime을_시각으로_쓴다() {
        // KmaUltraSrtNcstCleanser 정제 결과 형태 - fcstDate/fcstTime 필드 자체가 없음
        String payload = new JSONArray()
            .put(new JSONObject().put("baseDate", "20260818").put("baseTime", "1200").put("rn1", "5.0").put("nx", 60).put("ny", 124))
            .toString();

        List<HourlyPrecipitation> readings = WeatherReadingExtractor.extract(payload, "rn1");

        assertThat(readings).hasSize(1);
        assertThat(readings.get(0).time()).isEqualTo(LocalDateTime.of(2026, 8, 18, 12, 0));
        assertThat(readings.get(0).mm()).isEqualTo(5.0);
    }

    @Test
    void 예보류는_fcstDate_fcstTime을_시각으로_쓴다_baseTime과_다름() {
        // KmaVilageFcstCleanser 정제 결과 형태 - 발표시각(baseTime)과 예보대상시각(fcstTime)이 다름
        String payload = new JSONArray()
            .put(new JSONObject()
                .put("baseDate", "20260818").put("baseTime", "0500")
                .put("fcstDate", "20260818").put("fcstTime", "1500")
                .put("pcp", "30.0~50.0mm"))
            .toString();

        List<HourlyPrecipitation> readings = WeatherReadingExtractor.extract(payload, "pcp");

        assertThat(readings.get(0).time()).isEqualTo(LocalDateTime.of(2026, 8, 18, 15, 0));
        assertThat(readings.get(0).mm()).isEqualTo(50.0);
    }

    @Test
    void 값이_null인_행은_건너뛴다() {
        // TMN/TMX처럼 하루 1~2번만 나오는 카테고리가 null로 채워진 행 - pcp/rn1은 항상 있어야
        // 하지만 방어적으로 null이어도 죽지 않고 건너뛴다
        String payload = new JSONArray()
            .put(new JSONObject().put("fcstDate", "20260818").put("fcstTime", "1500").put("pcp", JSONObject.NULL))
            .put(new JSONObject().put("fcstDate", "20260818").put("fcstTime", "1600").put("pcp", "1.0mm"))
            .toString();

        List<HourlyPrecipitation> readings = WeatherReadingExtractor.extract(payload, "pcp");

        assertThat(readings).hasSize(1);
    }

    @Test
    void 시간_필드가_없는_행은_건너뛴다() {
        String payload = new JSONArray()
            .put(new JSONObject().put("pcp", "1.0mm"))
            .toString();

        List<HourlyPrecipitation> readings = WeatherReadingExtractor.extract(payload, "pcp");

        assertThat(readings).isEmpty();
    }

    @Test
    void 빈_배열이면_빈_목록을_반환한다() {
        assertThat(WeatherReadingExtractor.extract("[]", "pcp")).isEmpty();
    }
}
