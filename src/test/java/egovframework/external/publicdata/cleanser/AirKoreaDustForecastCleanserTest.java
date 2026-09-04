package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import egovframework.external.publicdata.collector.AirKoreaDustForecastFacilityLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대기질예보통보 정제기 - PM10만 남기는지, 같은 날짜의 여러 발표 중 최신만 신뢰하는지,
 * informGrade 문자열 파싱이 기관별 예보권역과 맞물리는지를 검증한다.
 */
class AirKoreaDustForecastCleanserTest {

    private final AirKoreaDustForecastCleanser cleanser =
        new AirKoreaDustForecastCleanser(new AirKoreaDustForecastFacilityLoader());

    @Test
    void operationKey로만_지원여부를_판단한다() {
        assertThat(cleanser.supports("airkorea-dust-forecast")).isTrue();
        assertThat(cleanser.supports("airkorea-realtime-measure")).isFalse();
    }

    @Test
    void PM10만_남기고_PM25_O3는_버린다() throws CleanseException {
        String raw = new JSONArray()
            .put(item("PM10", "2026-09-05", "2026-09-04 05시 발표", "서울 : 보통"))
            .put(item("PM25", "2026-09-05", "2026-09-04 05시 발표", "서울 : 나쁨"))
            .put(item("O3", "2026-09-05", "2026-09-04 05시 발표", "서울 : 좋음"))
            .toString();

        JSONArray rows = new JSONArray(cleanser.cleanse(raw));

        // 결과에 PM25/O3 유래 데이터가 섞이지 않았는지 - grade가 전부 PM10 값(보통)이어야 한다.
        for (int i = 0; i < rows.length(); i++) {
            assertThat(rows.getJSONObject(i).getString("grade")).isEqualTo("보통");
        }
        assertThat(rows.length()).isGreaterThan(0);
    }

    @Test
    void 같은_예보일자에_발표가_여러_번이면_가장_최근_발표만_남긴다() throws CleanseException {
        String raw = new JSONArray()
            .put(item("PM10", "2026-09-05", "2026-09-04 05시 발표", "서울 : 보통"))
            .put(item("PM10", "2026-09-05", "2026-09-04 11시 발표", "서울 : 나쁨"))
            .toString();

        JSONArray rows = new JSONArray(cleanser.cleanse(raw));

        // 05시 발표(보통)는 버려지고 11시 발표(나쁨)만 남아야 한다.
        Set<String> grades = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            grades.add(rows.getJSONObject(i).getString("grade"));
        }
        assertThat(grades).containsExactly("나쁨");
    }

    @Test
    void 예보권역별로_매칭된_기관마다_한_행씩_나온다() throws CleanseException {
        String raw = new JSONArray()
            .put(item("PM10", "2026-09-05", "2026-09-04 05시 발표",
                "서울 : 보통,경기남부 : 나쁨,경기북부 : 매우나쁨,영동 : 좋음,영서 : 보통,"
                + "광주 : 좋음,전남 : 좋음,제주 : 좋음,경남 : 좋음,경북 : 좋음,울산 : 좋음,"
                + "대구 : 좋음,부산 : 좋음,충남 : 좋음,충북 : 좋음,세종 : 좋음,대전 : 좋음,전북 : 좋음,인천 : 좋음"))
            .toString();

        JSONArray rows = new JSONArray(cleanser.cleanse(raw));

        // 의정부교도소(경기북부)만 매우나쁨이어야 한다 - 같은 경기도라도 남/북부가 갈린다.
        boolean found = false;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if ("1270785".equals(row.getString("facilityId"))) {
                assertThat(row.getString("informRegion")).isEqualTo("경기북부");
                assertThat(row.getString("grade")).isEqualTo("매우나쁨");
                found = true;
            }
        }
        assertThat(found).isTrue();
        assertThat(rows.length()).isEqualTo(59); // 이번엔 19개 권역 전부 응답에 있어 59개소 다 매칭
    }

    private JSONObject item(String informCode, String informData, String dataTime, String informGrade) {
        JSONObject o = new JSONObject();
        o.put("informCode", informCode);
        o.put("informData", informData);
        o.put("dataTime", dataTime);
        o.put("informGrade", informGrade);
        o.put("informCause", "테스트 사유");
        o.put("informOverall", "테스트 총평");
        return o;
    }
}
