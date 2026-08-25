package egovframework.external.publicdata.cleanser;

import egovframework.external.publicdata.collector.CsvFacilityMasterSource;
import egovframework.external.publicdata.collector.FacilityMasterCsvLoader;
import egovframework.external.exception.CleanseException;
import egovframework.external.publicdata.collector.FacilityRegion;
import egovframework.external.publicdata.collector.FacilityRegionLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DisasterMsgCleanser} 검증. 테스트용 지역 목록은 실제 CSV 대신 최소한만
 * (경북 청송군 3개소 + 경남 통영시 1개소) 직접 구성해 매칭 로직만 독립적으로 검증한다.
 */
class DisasterMsgCleanserTest {

    private static final List<FacilityRegion> REGIONS = List.of(
        new FacilityRegion("1270325", "경상북도청송군"),   // 경북북부제1교도소
        new FacilityRegion("1270416", "경상북도청송군"),   // 경북북부제2교도소 (같은 지역, 다른 기관)
        new FacilityRegion("1270936", "경상남도통영시"));  // 통영구치소

    /**
     * 실제 59개소 대신 위 3개소만으로 매칭 로직을 검증하기 위해, 로더의 {@code all()}만
     * 오버라이드한다(생성자는 실제 classpath CSV를 여전히 읽지만 결과는 안 씀 - 무해함).
     */
    private DisasterMsgCleanser cleanser() {
        FacilityRegionLoader fixedLoader = new FacilityRegionLoader(new CsvFacilityMasterSource(new FacilityMasterCsvLoader())) {
            @Override
            public List<FacilityRegion> all() {
                return REGIONS;
            }
        };
        return new DisasterMsgCleanser(fixedLoader);
    }

    private static String msg(String sn, String rgn, String dst) {
        return "{\"SN\":" + sn + ",\"MSG_CN\":\"테스트 메시지\",\"RCPTN_RGN_NM\":\"" + rgn + "\","
            + "\"CRT_DT\":\"2026/08/18 02:00:10\",\"REG_YMD\":\"2026/08/18 02:01:05.000000000\","
            + "\"EMRG_STEP_NM\":\"안전안내\",\"DST_SE_NM\":\"" + dst + "\","
            + "\"MDFCN_YMD\":\"2026/08/18 02:10:35.000000000\"}";
    }

    @Test
    void supports는_operationKey로만_판별한다() {
        DisasterMsgCleanser c = cleanser();
        assertThat(c.supports("safetydata-disaster-msg-list")).isTrue();
        assertThat(c.supports("kma-weather-warning-list")).isFalse();
    }

    @Test
    void 시군구까지만_온_지역명은_같은_시군구_기관_전부에_매칭된다() throws CleanseException {
        String raw = "[" + msg("1", "경상북도 청송군 ", "산사태") + "]";

        JSONArray result = new JSONArray(cleanser().cleanse(raw));

        assertThat(result.length()).isEqualTo(2); // 경북북부제1/2교도소 둘 다
        assertThat(toFacilityIds(result)).containsExactlyInAnyOrder("1270325", "1270416");
    }

    @Test
    void 읍면동까지_내려간_지역명도_시군구_단위로_매칭된다() throws CleanseException {
        // 실제 API에서 확인된 것처럼 시군구 뒤에 읍면동이 붙는 경우
        String raw = "[" + msg("1", "경상북도 청송군 진보면", "호우") + "]";

        JSONArray result = new JSONArray(cleanser().cleanse(raw));

        assertThat(result.length()).isEqualTo(2);
    }

    @Test
    void 콤마로_구분된_다중지역_각각을_따로_매칭한다() throws CleanseException {
        String raw = "[" + msg("1", "경상북도 청송군 ,경상남도 통영시 ", "호우") + "]";

        JSONArray result = new JSONArray(cleanser().cleanse(raw));

        assertThat(toFacilityIds(result)).containsExactlyInAnyOrder("1270325", "1270416", "1270936");
    }

    @Test
    void 매칭되는_기관이_없으면_결과에서_빠진다() throws CleanseException {
        String raw = "[" + msg("1", "서울특별시 강남구 ", "기타") + "]";

        JSONArray result = new JSONArray(cleanser().cleanse(raw));

        assertThat(result.length()).isZero();
    }

    @Test
    void 같은_기관에_여러_지역조각으로_중복매칭되면_1건으로_dedupe된다() throws CleanseException {
        // 청송군이 두 번 나와도 같은 기관에는 행 1개만
        String raw = "[" + msg("1", "경상북도 청송군 ,경상북도 청송군 ", "산사태") + "]";

        JSONArray result = new JSONArray(cleanser().cleanse(raw));

        assertThat(result.length()).isEqualTo(2); // 여전히 청송군 소속 2개 기관뿐
    }

    @Test
    void 여러_메시지가_섞여있으면_각각_독립적으로_매칭된다() throws CleanseException {
        String raw = "[" + msg("1", "경상북도 청송군 ", "산사태") + ","
            + msg("2", "경상남도 통영시 ", "호우") + "]";

        JSONArray result = new JSONArray(cleanser().cleanse(raw));

        assertThat(result.length()).isEqualTo(3); // 청송 2건 + 통영 1건
    }

    @Test
    void 여러_지역이_나열된_메시지는_기관마다_실제로_매칭된_지역조각을_따로_기록한다() throws CleanseException {
        // 실측 회귀 테스트(2026-08-18): 도 전체 호우주의보처럼 한 메시지에 여러 시군구가
        // 콤마로 나열되면, 청송군으로 매칭된 기관과 통영시로 매칭된 기관이 각각 자기를
        // 매칭시킨 조각을 가져야 한다 - 메시지 전체에서 처음 매칭된 지역 하나로 전부 찍히면 안 됨
        String raw = "[" + msg("1", "경상남도 통영시 ,경상북도 청송군 ", "호우") + "]";

        JSONArray result = new JSONArray(cleanser().cleanse(raw));

        Map<String, String> matchedByFacility = new HashMap<>();
        for (int i = 0; i < result.length(); i++) {
            JSONObject row = result.getJSONObject(i);
            matchedByFacility.put(row.getString("facilityId"), row.getString("matchedRegionNm"));
        }
        assertThat(matchedByFacility.get("1270936")).isEqualTo("경상남도 통영시"); // 통영구치소
        assertThat(matchedByFacility.get("1270325")).isEqualTo("경상북도 청송군"); // 경북북부제1교도소
        assertThat(matchedByFacility.get("1270416")).isEqualTo("경상북도 청송군"); // 경북북부제2교도소
    }

    @Test
    void 결과_행에_DDL_대응_필드가_전부_담긴다() throws CleanseException {
        String raw = "[" + msg("266798", "경상남도 통영시 ", "호우") + "]";

        JSONObject row = new JSONArray(cleanser().cleanse(raw)).getJSONObject(0);

        assertThat(row.getLong("sn")).isEqualTo(266798L);
        assertThat(row.getString("facilityId")).isEqualTo("1270936");
        assertThat(row.getString("matchedRegionNm")).isEqualTo("경상남도 통영시");
        assertThat(row.getString("crtDtm")).isEqualTo("2026-08-18T02:00:10");
        assertThat(row.getString("msgCn")).isEqualTo("테스트 메시지");
        assertThat(row.getString("emrgStepNm")).isEqualTo("안전안내");
        assertThat(row.getString("dstSeNm")).isEqualTo("호우");
        assertThat(row.getString("rcptnRgnNmRaw")).isEqualTo("경상남도 통영시 ");
        // REG_YMD/MDFCN_YMD는 이름과 달리 초 이하 단위까지 포함한 원문 그대로 통과
        assertThat(row.getString("regDe")).isEqualTo("2026/08/18 02:01:05.000000000");
        assertThat(row.getString("mdfcnDe")).isEqualTo("2026/08/18 02:10:35.000000000");
    }

    @Test
    void 필수필드가_없으면_CleanseException으로_감싼다() {
        String raw = "[{\"SN\":1,\"RCPTN_RGN_NM\":\"경상남도 통영시 \"}]"; // MSG_CN 등 누락

        assertThatThrownBy(() -> cleanser().cleanse(raw))
            .isInstanceOf(CleanseException.class)
            .hasMessageContaining("정제 실패");
    }

    @Test
    void CRT_DT_형식이_다르면_CleanseException으로_감싼다() {
        String raw = "[{\"SN\":1,\"MSG_CN\":\"m\",\"RCPTN_RGN_NM\":\"경상남도 통영시 \","
            + "\"CRT_DT\":\"2026-08-18\",\"EMRG_STEP_NM\":\"안전안내\",\"DST_SE_NM\":\"호우\"}]";

        assertThatThrownBy(() -> cleanser().cleanse(raw))
            .isInstanceOf(CleanseException.class);
    }

    @Test
    void structureProbes에_실측_필드가_담겨있다() {
        StructureProbe probe = cleanser().structureProbes().get(0);

        assertThat(probe.knownFields())
            .containsExactlyInAnyOrder("SN", "MSG_CN", "RCPTN_RGN_NM", "CRT_DT", "REG_YMD",
                "EMRG_STEP_NM", "DST_SE_NM", "MDFCN_YMD");
    }

    private static List<String> toFacilityIds(JSONArray result) {
        return result.toList().stream()
            .map(o -> (String) ((java.util.Map<?, ?>) o).get("facilityId"))
            .toList();
    }
}
